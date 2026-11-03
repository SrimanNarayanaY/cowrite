package com.sriman.cowrite.document;

import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.user.User;
import com.sriman.cowrite.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the classic N+1 and proves the fix.
 *
 * <p>Run with {@code mvnw test -Dtest=DocumentNPlusOneTest} and watch the printed
 * query counts (or set {@code spring.jpa.show-sql=true} to see the SQL).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentNPlusOneTest {

	private static final int OWNER_COUNT = 10;
	private static final int DOCUMENTS_PER_OWNER = 10;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DocumentService documentService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Statistics statistics;
	private final List<Long> ownerIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);

		seed(OWNER_COUNT, DOCUMENTS_PER_OWNER);

		entityManager.flush();
		entityManager.clear();
		statistics.clear();
	}

	@Test
	void naiveMappingTriggersNPlusOne() {
		List<Document> documents = documentRepository.findAll();
		List<String> ownerEmails = documents.stream()
				.map(document -> document.getOwner().getEmail())
				.toList();

		long queries = statistics.getPrepareStatementCount();
		System.out.printf("[N+1 BAD] %d documents / %d owners -> %d queries (+1 per owner)%n",
				documents.size(), OWNER_COUNT, queries);

		assertThat(ownerEmails).hasSize(OWNER_COUNT * DOCUMENTS_PER_OWNER);
		assertThat(queries).isEqualTo(1 + OWNER_COUNT);
	}

	@Test
	void entityGraphLoadsOwnersInOneQuery() {
		Long ownerId = ownerIds.getFirst();

		var page = documentService.listAccessible(ownerId,
				PageRequest.of(0, DOCUMENTS_PER_OWNER, Sort.by(Sort.Direction.DESC, "createdAt")));

		long queries = statistics.getPrepareStatementCount();
		System.out.printf("[N+1 FIXED] page of %d documents -> %d queries (1 count + 1 join fetch)%n",
				page.content().size(), queries);

		assertThat(page.content()).hasSize(DOCUMENTS_PER_OWNER);
		assertThat(page.content())
				.allSatisfy(dto -> assertThat(dto.ownerEmail()).isEqualTo("owner0@example.com"));
		assertThat(queries).isEqualTo(2);
	}

	@Test
	void getByIdAlsoLoadsOwnerInOneQuery() {
		Long ownerId = ownerIds.getFirst();
		Long id = documentRepository.findAllAccessibleByUserId(ownerId, PageRequest.of(0, 1))
				.getContent().getFirst().getId();

		entityManager.clear();
		statistics.clear();

		DocumentResponse response = documentService.getById(id);

		long queries = statistics.getPrepareStatementCount();
		System.out.printf("[N+1 FIXED] getById -> %d query%n", queries);

		assertThat(response.ownerEmail()).isEqualTo("owner0@example.com");
		assertThat(queries).isEqualTo(1);
	}

	private void seed(int owners, int documentsPerOwner) {
		for (int i = 0; i < owners; i++) {
			User owner = new User();
			owner.setEmail("owner" + i + "@example.com");
			owner.setPasswordHash("$2a$10$hash" + i);
			userRepository.save(owner);
			ownerIds.add(owner.getId());

			for (int j = 0; j < documentsPerOwner; j++) {
				Document document = new Document();
				document.setTitle("Document " + i + "-" + j);
				document.setContent("Body " + i + "-" + j);
				document.setOwner(owner);
				documentRepository.save(document);
			}
		}
	}

}
