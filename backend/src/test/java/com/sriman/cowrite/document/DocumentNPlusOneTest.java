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

	private static final int DOCUMENT_COUNT = 10;

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

	@BeforeEach
	void setUp() {
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);

		seedDocumentsWithOwners(DOCUMENT_COUNT);

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
		System.out.printf("[N+1 BAD] %d documents -> %d queries (1 for documents + %d for owners)%n",
				documents.size(), queries, documents.size());

		assertThat(ownerEmails).hasSize(DOCUMENT_COUNT);
		assertThat(queries).isEqualTo(DOCUMENT_COUNT + 1);
	}

	@Test
	void entityGraphLoadsOwnersInOneQuery() {
		var page = documentService.list(
				PageRequest.of(0, DOCUMENT_COUNT, Sort.by(Sort.Direction.DESC, "createdAt")));

		long queries = statistics.getPrepareStatementCount();
		System.out.printf("[N+1 FIXED] %d documents -> %d queries (1 count + 1 join fetch)%n",
				page.content().size(), queries);

		assertThat(page.content()).hasSize(DOCUMENT_COUNT);
		assertThat(page.content())
				.allSatisfy(dto -> assertThat(dto.ownerEmail()).endsWith("@example.com"));
		assertThat(queries).isEqualTo(2);
	}

	@Test
	void getByIdAlsoLoadsOwnerInOneQuery() {
		Long id = documentRepository.findAll().getFirst().getId();
		entityManager.clear();
		statistics.clear();

		DocumentResponse response = documentService.getById(id);

		long queries = statistics.getPrepareStatementCount();
		System.out.printf("[N+1 FIXED] getById -> %d query%n", queries);

		assertThat(response.ownerEmail()).isNotBlank();
		assertThat(queries).isEqualTo(1);
	}

	private void seedDocumentsWithOwners(int count) {
		for (int i = 0; i < count; i++) {
			User owner = new User();
			owner.setEmail("owner" + i + "@example.com");
			owner.setPasswordHash("$2a$10$hash" + i);
			userRepository.save(owner);

			Document document = new Document();
			document.setTitle("Document " + i);
			document.setContent("Body " + i);
			document.setOwner(owner);
			documentRepository.save(document);
		}
	}

}
