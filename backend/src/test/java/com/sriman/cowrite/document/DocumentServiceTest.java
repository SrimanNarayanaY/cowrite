package com.sriman.cowrite.document;

import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.user.User;
import com.sriman.cowrite.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

	private static final Long OWNER_ID = 3L;
	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	@Mock
	private DocumentRepository documentRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private DocumentService documentService;

	private static User owner() {
		User user = new User();
		user.setId(OWNER_ID);
		user.setEmail("owner@example.com");
		return user;
	}

	private static Document document(String title, String content) {
		Document document = new Document();
		document.setId(1L);
		document.setTitle(title);
		document.setContent(content);
		document.setOwner(owner());
		document.setCreatedAt(NOW);
		document.setUpdatedAt(NOW);
		return document;
	}

	@Test
	void createSavesDocumentWithAuthenticatedOwner() {
		User owner = owner();
		given(userRepository.getReferenceById(OWNER_ID)).willReturn(owner);
		given(documentRepository.save(any(Document.class))).willAnswer(invocation -> {
			Document saved = invocation.getArgument(0);
			saved.setId(7L);
			saved.setCreatedAt(NOW);
			saved.setUpdatedAt(NOW);
			return saved;
		});

		DocumentResponse response = documentService.create(new DocumentRequest("Title", "Body"), OWNER_ID);

		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
		then(documentRepository).should().save(captor.capture());

		assertThat(captor.getValue().getTitle()).isEqualTo("Title");
		assertThat(captor.getValue().getOwner()).isSameAs(owner);
		assertThat(response.id()).isEqualTo(7L);
		assertThat(response.ownerId()).isEqualTo(OWNER_ID);
		assertThat(response.ownerEmail()).isEqualTo("owner@example.com");
	}

	@Test
	void listMapsPageToResponseForOwner() {
		given(documentRepository.findAllByOwnerId(eq(OWNER_ID), any(Pageable.class)))
				.willReturn(new PageImpl<>(List.of(document("Title", "Body")), PageRequest.of(0, 10), 1));

		var page = documentService.list(OWNER_ID, PageRequest.of(0, 10));

		assertThat(page.content()).hasSize(1);
		assertThat(page.content().getFirst().title()).isEqualTo("Title");
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.first()).isTrue();
		assertThat(page.last()).isTrue();
	}

	@Test
	void updateChangesExistingDocument() {
		Document existing = document("Old", "Old body");
		given(documentRepository.findByIdAndOwnerId(1L, OWNER_ID)).willReturn(Optional.of(existing));
		given(documentRepository.save(existing)).willReturn(existing);

		DocumentResponse response = documentService.update(1L, new DocumentRequest("New", "New body"), OWNER_ID);

		assertThat(response.title()).isEqualTo("New");
		assertThat(response.content()).isEqualTo("New body");
	}

	@Test
	void getByIdThrowsWhenMissingOrNotOwned() {
		given(documentRepository.findByIdAndOwnerId(99L, OWNER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.getById(99L, OWNER_ID))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found with id: 99");
	}

	@Test
	void deleteThrowsWhenMissingOrNotOwned() {
		given(documentRepository.findByIdAndOwnerId(99L, OWNER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.delete(99L, OWNER_ID))
				.isInstanceOf(DocumentNotFoundException.class);
		then(documentRepository).should(never()).delete(any());
	}

}
