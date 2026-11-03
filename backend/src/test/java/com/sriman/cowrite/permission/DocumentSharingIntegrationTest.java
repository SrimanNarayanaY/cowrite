package com.sriman.cowrite.permission;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sharing + RBAC over the real HTTP stack:
 * owner shares, viewer reads only, editor reads + updates, only owner deletes/shares.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentSharingIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void ownerCanShareWithViewerWhoCanOnlyRead() throws Exception {
		String owner = registerAndLogin("owner@example.com");
		String viewer = registerAndLogin("viewer@example.com");
		long docId = createDocument(owner, "Shared design doc");

		share(owner, docId, "viewer@example.com", "VIEWER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documentId").value(docId))
				.andExpect(jsonPath("$.email").value("viewer@example.com"))
				.andExpect(jsonPath("$.role").value("VIEWER"));

		// viewer can read
		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + viewer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Shared design doc"));

		// ...and sees it in the list together with their own documents
		mockMvc.perform(get("/api/documents").header("Authorization", "Bearer " + viewer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[*].id", contains(Math.toIntExact(docId))));

		// viewer cannot edit
		mockMvc.perform(put("/api/documents/" + docId)
						.header("Authorization", "Bearer " + viewer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"viewer was here\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));

		// viewer cannot delete
		mockMvc.perform(delete("/api/documents/" + docId).header("Authorization", "Bearer " + viewer))
				.andExpect(status().isForbidden());

		// viewer cannot re-share
		share(viewer, docId, "someone@example.com", "VIEWER")
				.andExpect(status().isForbidden());
	}

	@Test
	void editorCanUpdateButNotDeleteOrShare() throws Exception {
		String owner = registerAndLogin("owner2@example.com");
		String editor = registerAndLogin("editor@example.com");
		long docId = createDocument(owner, "Editorial doc");

		share(owner, docId, "editor@example.com", "EDITOR").andExpect(status().isOk());

		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + editor))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/documents/" + docId)
						.header("Authorization", "Bearer " + editor)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"edited by collaborator\",\"content\":\"new body\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("edited by collaborator"));

		// edit is the limit: delete and share stay owner-only
		mockMvc.perform(delete("/api/documents/" + docId).header("Authorization", "Bearer " + editor))
				.andExpect(status().isForbidden());

		share(editor, docId, "someone@example.com", "VIEWER")
				.andExpect(status().isForbidden());
	}

	@Test
	void reSharingUpdatesTheRoleInsteadOfDuplicating() throws Exception {
		String owner = registerAndLogin("owner3@example.com");
		String collaborator = registerAndLogin("collab@example.com");
		long docId = createDocument(owner, "Role upgrade doc");

		share(owner, docId, "collab@example.com", "VIEWER").andExpect(status().isOk());
		share(owner, docId, "collab@example.com", "EDITOR")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("EDITOR"));

		// the upgrade takes effect immediately
		mockMvc.perform(put("/api/documents/" + docId)
						.header("Authorization", "Bearer " + collaborator)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"now I can edit\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("now I can edit"));
	}

	@Test
	void sharingRulesForSelfUnknownEmailAndStrangers() throws Exception {
		String owner = registerAndLogin("owner4@example.com");
		long docId = createDocument(owner, "Private doc");

		share(owner, docId, "owner4@example.com", "VIEWER")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("You cannot share a document with yourself"));

		share(owner, docId, "ghost@example.com", "VIEWER")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("User not found with email: ghost@example.com"));

		share(owner, docId, "not-an-email", "VIEWER")
				.andExpect(status().isBadRequest());
	}

	@Test
	void deletingTheDocumentRemovesTheShare() throws Exception {
		String owner = registerAndLogin("owner5@example.com");
		String viewer = registerAndLogin("viewer5@example.com");
		long docId = createDocument(owner, "Disposable doc");

		share(owner, docId, "viewer5@example.com", "VIEWER").andExpect(status().isOk());

		mockMvc.perform(delete("/api/documents/" + docId).header("Authorization", "Bearer " + owner))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + viewer))
				.andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- helpers

	private long createDocument(String token, String title) throws Exception {
		String body = mockMvc.perform(post("/api/documents")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"" + title + "\"}"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();

		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private org.springframework.test.web.servlet.ResultActions share(
			String token, long docId, String email, String role) throws Exception {
		return mockMvc.perform(post("/api/documents/" + docId + "/share")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"));
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
