package com.sriman.cowrite.document;

import com.sriman.cowrite.common.web.PageResponse;
import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DocumentService documentService;

	private static DocumentResponse sample() {
		return new DocumentResponse(1L, "First doc", "hello", 5L, "owner@example.com", NOW, NOW);
	}

	@Test
	void createsDocumentAndReturnsCreatedWithLocation() throws Exception {
		given(documentService.create(any(DocumentRequest.class))).willReturn(sample());

		mockMvc.perform(post("/api/documents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"First doc","content":"hello"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/documents/1"))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.title").value("First doc"))
				.andExpect(jsonPath("$.content").value("hello"));
	}

	@Test
	void rejectsBlankTitleWith400() throws Exception {
		mockMvc.perform(post("/api/documents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"  ","content":"hello"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fieldErrors.title").value("title is required"));

		then(documentService).should(never()).create(any());
	}

	@Test
	void rejectsTitleLongerThan200CharactersWith400() throws Exception {
		String longTitle = "a".repeat(201);

		mockMvc.perform(post("/api/documents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"" + longTitle + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").value("title must be at most 200 characters"));
	}

	@Test
	void listsDocumentsAsPage() throws Exception {
		PageResponse<DocumentResponse> page =
				new PageResponse<>(List.of(sample()), 0, 10, 1, 1, true, true);
		given(documentService.list(any(Pageable.class))).willReturn(page);

		mockMvc.perform(get("/api/documents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].ownerEmail").value("owner@example.com"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.first").value(true))
				.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void passesPageAndSizeToService() throws Exception {
		given(documentService.list(any(Pageable.class)))
				.willReturn(new PageResponse<>(List.of(), 2, 5, 0, 0, false, true));

		mockMvc.perform(get("/api/documents?page=2&size=5"))
				.andExpect(status().isOk());

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		then(documentService).should().list(captor.capture());

		assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
		assertThat(captor.getValue().getPageSize()).isEqualTo(5);
		assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	@Test
	void returns404WhenDocumentMissing() throws Exception {
		given(documentService.getById(42L)).willThrow(new DocumentNotFoundException(42L));

		mockMvc.perform(get("/api/documents/42"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.message").value("Document not found with id: 42"));
	}

	@Test
	void updatesDocument() throws Exception {
		given(documentService.update(eq(1L), any(DocumentRequest.class))).willReturn(sample());

		mockMvc.perform(put("/api/documents/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Renamed","content":"body"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("First doc"));
	}

	@Test
	void deletesDocumentAndReturns204() throws Exception {
		mockMvc.perform(delete("/api/documents/1"))
				.andExpect(status().isNoContent());
	}

}
