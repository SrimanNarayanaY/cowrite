package com.sriman.cowrite.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack test: controller -> service -> repository -> (H2) database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void fullCrudLifecycle() throws Exception {
		String createdBody = mockMvc.perform(post("/api/documents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Design notes","content":"draft"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andReturn()
				.getResponse()
				.getContentAsString();

		long id = ((Number) com.jayway.jsonpath.JsonPath.read(createdBody, "$.id")).longValue();

		mockMvc.perform(get("/api/documents"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].title").value("Design notes"));

		mockMvc.perform(get("/api/documents/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("draft"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());

		mockMvc.perform(put("/api/documents/" + id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Design notes v2","content":"final"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Design notes v2"));

		mockMvc.perform(delete("/api/documents/" + id))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/documents/" + id))
				.andExpect(status().isNotFound());
	}

	@Test
	void validationErrorsReturn400() throws Exception {
		mockMvc.perform(post("/api/documents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").exists());
	}

}
