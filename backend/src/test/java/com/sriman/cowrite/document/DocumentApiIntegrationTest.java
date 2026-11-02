package com.sriman.cowrite.document;

import com.jayway.jsonpath.JsonPath;
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
 * Full stack test: HTTP + security filter + JWT + controller -> service -> repository -> (H2) database.
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
		String token = registerAndLogin("writer@example.com");

		String createdBody = mockMvc.perform(post("/api/documents")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Design notes","content":"draft"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.ownerEmail").value("writer@example.com"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		long id = ((Number) JsonPath.read(createdBody, "$.id")).longValue();

		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].title").value("Design notes"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));

		mockMvc.perform(get("/api/documents/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("draft"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());

		mockMvc.perform(put("/api/documents/" + id)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Design notes v2","content":"final"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Design notes v2"));

		mockMvc.perform(delete("/api/documents/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/documents/" + id).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void validationErrorsReturn400() throws Exception {
		String token = registerAndLogin("validator@example.com");

		mockMvc.perform(post("/api/documents")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").exists());
	}

	@Test
	void paginatesDocuments() throws Exception {
		String token = registerAndLogin("pager@example.com");

		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/api/documents")
							.header("Authorization", "Bearer " + token)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"title\":\"Doc " + i + "\"}"))
					.andExpect(status().isCreated());
		}

		mockMvc.perform(get("/api/documents?page=0&size=2").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.first").value(true))
				.andExpect(jsonPath("$.last").value(false));

		mockMvc.perform(get("/api/documents?page=1&size=2").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.first").value(false))
				.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void usersOnlySeeAndTouchTheirOwnDocuments() throws Exception {
		String aliceToken = registerAndLogin("alice@example.com");
		String bobToken = registerAndLogin("bob@example.com");

		mockMvc.perform(post("/api/documents")
						.header("Authorization", "Bearer " + aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Alice private doc\"}"))
				.andExpect(status().isCreated());

		String bobDocBody = mockMvc.perform(post("/api/documents")
						.header("Authorization", "Bearer " + bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Bob private doc\"}"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		long bobDocId = ((Number) JsonPath.read(bobDocBody, "$.id")).longValue();

		// List is scoped to the caller
		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].title").value("Alice private doc"))
				.andExpect(jsonPath("$.content[0].ownerEmail").value("alice@example.com"));

		// Someone else's document behaves like it does not exist -> 404, not 403
		mockMvc.perform(get("/api/documents/" + bobDocId).header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/documents/" + bobDocId)
						.header("Authorization", "Bearer " + aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"hijacked\"}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/documents/" + bobDocId).header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isNotFound());

		// Bob's document is untouched
		mockMvc.perform(get("/api/documents/" + bobDocId).header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Bob private doc"));
	}

	@Test
	void documentsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/documents"))
				.andExpect(status().isUnauthorized());
	}

	private String registerAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());

		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return JsonPath.read(body, "$.token");
	}

}
