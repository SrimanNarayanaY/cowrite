package com.sriman.cowrite.document;

import com.sriman.cowrite.document.dto.DocumentRequest;
import com.sriman.cowrite.document.dto.DocumentResponse;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

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

	@Test
	void createsDocumentAndReturnsCreatedWithLocation() throws Exception {
		given(documentService.create(any(DocumentRequest.class)))
				.willReturn(new DocumentResponse(1L, "First doc", "hello", NOW, NOW));

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
	void listsDocuments() throws Exception {
		given(documentService.list())
				.willReturn(List.of(new DocumentResponse(1L, "First doc", "hello", NOW, NOW)));

		mockMvc.perform(get("/api/documents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(1));
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
		given(documentService.update(eq(1L), any(DocumentRequest.class)))
				.willReturn(new DocumentResponse(1L, "Renamed", "body", NOW, NOW));

		mockMvc.perform(put("/api/documents/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Renamed","content":"body"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Renamed"));
	}

	@Test
	void deletesDocumentAndReturns204() throws Exception {
		mockMvc.perform(delete("/api/documents/1"))
				.andExpect(status().isNoContent());
	}

}
