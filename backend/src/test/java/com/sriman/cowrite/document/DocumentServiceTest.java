package com.sriman.cowrite.document;

import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

	@Mock
	private DocumentRepository documentRepository;

	@InjectMocks
	private DocumentService documentService;

	@Test
	void createSavesDocumentWithRequestValues() {
		given(documentRepository.save(any(Document.class))).willAnswer(invocation -> {
			Document saved = invocation.getArgument(0);
			saved.setId(7L);
			saved.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
			saved.setUpdatedAt(saved.getCreatedAt());
			return saved;
		});

		DocumentResponse response = documentService.create(new DocumentRequest("Title", "Body"));

		ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
		then(documentRepository).should().save(captor.capture());
		assertThat(captor.getValue().getTitle()).isEqualTo("Title");
		assertThat(captor.getValue().getContent()).isEqualTo("Body");
		assertThat(response.id()).isEqualTo(7L);
		assertThat(response.title()).isEqualTo("Title");
	}

	@Test
	void updateChangesExistingDocument() {
		Document existing = new Document();
		existing.setId(1L);
		existing.setTitle("Old");
		existing.setContent("Old body");
		given(documentRepository.findById(1L)).willReturn(Optional.of(existing));
		given(documentRepository.save(existing)).willReturn(existing);

		DocumentResponse response = documentService.update(1L, new DocumentRequest("New", "New body"));

		assertThat(response.title()).isEqualTo("New");
		assertThat(response.content()).isEqualTo("New body");
	}

	@Test
	void getByIdThrowsWhenMissing() {
		given(documentRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.getById(99L))
				.isInstanceOf(DocumentNotFoundException.class)
				.hasMessage("Document not found with id: 99");
	}

	@Test
	void deleteThrowsWhenMissing() {
		given(documentRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> documentService.delete(99L))
				.isInstanceOf(DocumentNotFoundException.class);
		then(documentRepository).should(never()).delete(any());
	}

}
