package com.sriman.cowrite.document;

import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.permission.DocumentPermissionRepository;
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
	private DocumentPermissionRepository documentPermissionRepository;

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
	void listAccessibleMapsPageToResponse() {
		given(documentRepository.findAllAccessibleByUserId(eq(OWNER_ID), any(Pageable.class)))
				.willReturn(new PageImpl<>(List.of(document("Title", "Body")), PageRequest.of(0, 10), 1));

		var page = documentService.listAccessible(OWNER_ID, PageRequest.of(0, 10));

		assertThat(page.content()).hasSize(1);
		assertThat(page.content().getFirst().title()).isEqualTo("Title");
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.first()).isTrue();
		assertThat(page.last()).isTrue();
	}

	@Test
	void updateChangesExistingDocument() {
		Document existing = document("Old", "Old body");
		given(documentRepository.findWithOwnerById(1L)).willReturn(Optional.of(existing));
		given(documentRepository.save(existing)).willReturn(existing);

		DocumentResponse response = documentService.update(1L, new DocumentRequest("New", "New body"));

		assertThat(response.title()).isEqualTo("New");
		assertThat(response.content()).isEqualTo("New body");
	}

	@Test
	void getByIdThrowsWhenMissing() {
		given(documentRepository.findWithOwnerById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.getById(99L))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found with id: 99");
	}

	@Test
	void deleteRemovesSharesBeforeTheDocument() {
		Document existing = document("Doc", "Body");
		given(documentRepository.findWithOwnerById(1L)).willReturn(Optional.of(existing));

		documentService.delete(1L);

		then(documentPermissionRepository).should().deleteByDocumentId(1L);
		then(documentRepository).should().delete(existing);
	}

	@Test
	void deleteThrowsWhenMissing() {
		given(documentRepository.findWithOwnerById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.delete(99L))
				.isInstanceOf(DocumentNotFoundException.class);
		then(documentRepository).should(never()).delete(any());
	}

}
