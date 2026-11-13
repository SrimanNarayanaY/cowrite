package com.sriman.cowrite.document;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Optimistic locking over the real HTTP stack:
 * the version returned by a save is the one the next save must send.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentVersioningIntegrationTest {

	private static final String TIPTAP_V1 = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";
	private static final String TIPTAP_V2 = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}]}";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void saveIncrementsVersionAndStaleVersionReturns409() throws Exception {
		String owner = registerAndLogin("versioner@example.com");
		long docId = createDocument(owner, "Versioned doc");

		// Fresh document starts at version 0.
		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.access").value("OWNER"))
				.andExpect(jsonPath("$.canEdit").value(true));

		saveContent(owner, docId, TIPTAP_V1, 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(TIPTAP_V1))
				.andExpect(jsonPath("$.version").value(1));

		// A second tab still holding version 0 must not overwrite the save above.
		saveContent(owner, docId, TIPTAP_V2, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message", containsString("changed by someone else")));

		// Reloading gives the current version, and the retry succeeds.
		saveContent(owner, docId, TIPTAP_V2, 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2));

		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(TIPTAP_V2))
				.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void viewerCanReadButNotSave() throws Exception {
		String owner = registerAndLogin("owner-v@example.com");
		String viewer = registerAndLogin("viewer-v@example.com");
		long docId = createDocument(owner, "Read only doc");

		share(owner, docId, "viewer-v@example.com", "VIEWER").andExpect(status().isOk());

		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + viewer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access").value("VIEWER"))
				.andExpect(jsonPath("$.canEdit").value(false));

		saveContent(viewer, docId, TIPTAP_V1, 0)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void editorCanSave() throws Exception {
		String owner = registerAndLogin("owner-e@example.com");
		String editor = registerAndLogin("editor-v@example.com");
		long docId = createDocument(owner, "Editable doc");

		share(owner, docId, "editor-v@example.com", "EDITOR").andExpect(status().isOk());

		mockMvc.perform(get("/api/documents/" + docId).header("Authorization", "Bearer " + editor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access").value("EDITOR"))
				.andExpect(jsonPath("$.canEdit").value(true));

		saveContent(editor, docId, TIPTAP_V1, 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void contentAndVersionAreRequired() throws Exception {
		String owner = registerAndLogin("owner-r@example.com");
		long docId = createDocument(owner, "Validation doc");

		mockMvc.perform(put("/api/documents/" + docId + "/content")
						.header("Authorization", "Bearer " + owner)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"text\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.version").exists());

		mockMvc.perform(put("/api/documents/" + docId + "/content")
						.header("Authorization", "Bearer " + owner)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"version\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.content").exists());
	}

	@Test
	void savingRequiresAuthentication() throws Exception {
		String owner = registerAndLogin("owner-a@example.com");
		long docId = createDocument(owner, "Auth doc");

		mockMvc.perform(put("/api/documents/" + docId + "/content")
						.contentType(MediaType.APPLICATION_JSON)
						.content(contentBody(TIPTAP_V1, 0)))
				.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- helpers

	private ResultActions saveContent(String token, long docId, String content, long version) throws Exception {
		return mockMvc.perform(put("/api/documents/" + docId + "/content")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(contentBody(content, version)));
	}

	private String contentBody(String content, long version) {
		String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
		return "{\"content\":\"" + escaped + "\",\"version\":" + version + "}";
	}

	private long createDocument(String token, String title) throws Exception {
		String body = mockMvc.perform(post("/api/documents")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"" + title + "\",\"content\":null}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(0))
				.andReturn()
				.getResponse()
				.getContentAsString();

		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions share(String token, long docId, String email, String role) throws Exception {
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
