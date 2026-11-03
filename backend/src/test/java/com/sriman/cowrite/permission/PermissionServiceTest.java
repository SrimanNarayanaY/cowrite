package com.sriman.cowrite.permission;

import com.sriman.cowrite.document.Document;
import com.sriman.cowrite.document.DocumentRepository;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.permission.exception.PermissionDeniedException;
import com.sriman.cowrite.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

	private static final Long DOC_ID = 10L;
	private static final Long OWNER_ID = 1L;
	private static final Long EDITOR_ID = 2L;
	private static final Long VIEWER_ID = 3L;
	private static final Long STRANGER_ID = 4L;

	@Mock
	private DocumentRepository documentRepository;

	@Mock
	private DocumentPermissionRepository documentPermissionRepository;

	@InjectMocks
	private PermissionService permissionService;

	private static Document documentOwnedBy(Long ownerId) {
		User owner = new User();
		owner.setId(ownerId);
		owner.setEmail("owner@example.com");

		Document document = new Document();
		document.setId(DOC_ID);
		document.setTitle("Doc");
		document.setOwner(owner);
		return document;
	}

	private static DocumentPermission permission(Long userId, DocumentRole role) {
		User user = new User();
		user.setId(userId);
		user.setEmail("user" + userId + "@example.com");

		DocumentPermission permission = new DocumentPermission();
		permission.setDocument(documentOwnedBy(OWNER_ID));
		permission.setUser(user);
		permission.setRole(role);
		return permission;
	}

	private void givenDocumentExists() {
		given(documentRepository.findById(DOC_ID)).willReturn(Optional.of(documentOwnedBy(OWNER_ID)));
	}

	// ---------- isOwner / canView / canEdit ----------

	@Test
	void ownerHasEveryRight() {
		given(documentRepository.existsByIdAndOwnerId(DOC_ID, OWNER_ID)).willReturn(true);

		assertThat(permissionService.isOwner(DOC_ID, OWNER_ID)).isTrue();
		assertThat(permissionService.canView(DOC_ID, OWNER_ID)).isTrue();
		assertThat(permissionService.canEdit(DOC_ID, OWNER_ID)).isTrue();
	}

	@Test
	void viewerCanViewButCannotEdit() {
		given(documentRepository.existsByIdAndOwnerId(DOC_ID, VIEWER_ID)).willReturn(false);
		given(documentPermissionRepository.existsByDocumentIdAndUserId(DOC_ID, VIEWER_ID)).willReturn(true);
		given(documentPermissionRepository.findByDocumentIdAndUserId(DOC_ID, VIEWER_ID))
				.willReturn(Optional.of(permission(VIEWER_ID, DocumentRole.VIEWER)));

		assertThat(permissionService.canView(DOC_ID, VIEWER_ID)).isTrue();
		assertThat(permissionService.canEdit(DOC_ID, VIEWER_ID)).isFalse();
	}

	@Test
	void editorCanViewAndEditButIsNotOwner() {
		given(documentRepository.existsByIdAndOwnerId(DOC_ID, EDITOR_ID)).willReturn(false);
		given(documentPermissionRepository.existsByDocumentIdAndUserId(DOC_ID, EDITOR_ID)).willReturn(true);
		given(documentPermissionRepository.findByDocumentIdAndUserId(DOC_ID, EDITOR_ID))
				.willReturn(Optional.of(permission(EDITOR_ID, DocumentRole.EDITOR)));

		assertThat(permissionService.canView(DOC_ID, EDITOR_ID)).isTrue();
		assertThat(permissionService.canEdit(DOC_ID, EDITOR_ID)).isTrue();
		assertThat(permissionService.isOwner(DOC_ID, EDITOR_ID)).isFalse();
	}

	@Test
	void strangerCanNeitherViewNorEdit() {
		given(documentRepository.existsByIdAndOwnerId(DOC_ID, STRANGER_ID)).willReturn(false);
		given(documentPermissionRepository.existsByDocumentIdAndUserId(DOC_ID, STRANGER_ID)).willReturn(false);
		given(documentPermissionRepository.findByDocumentIdAndUserId(DOC_ID, STRANGER_ID)).willReturn(Optional.empty());

		assertThat(permissionService.canView(DOC_ID, STRANGER_ID)).isFalse();
		assertThat(permissionService.canEdit(DOC_ID, STRANGER_ID)).isFalse();
	}

	// ---------- require* (the enforcement used by controllers) ----------

	@Test
	void requireViewAllowsOwnerEditorAndViewer() {
		givenDocumentExists();
		given(documentPermissionRepository.existsByDocumentIdAndUserId(DOC_ID, EDITOR_ID)).willReturn(true);
		given(documentPermissionRepository.existsByDocumentIdAndUserId(DOC_ID, VIEWER_ID)).willReturn(true);

		assertThatCode(() -> permissionService.requireView(DOC_ID, OWNER_ID)).doesNotThrowAnyException();
		assertThatCode(() -> permissionService.requireView(DOC_ID, EDITOR_ID)).doesNotThrowAnyException();
		assertThatCode(() -> permissionService.requireView(DOC_ID, VIEWER_ID)).doesNotThrowAnyException();
	}

	@Test
	void requireViewRejectsStrangerWith403() {
		givenDocumentExists();
		given(documentPermissionRepository.existsByDocumentIdAndUserId(DOC_ID, STRANGER_ID)).willReturn(false);

		assertThatThrownBy(() -> permissionService.requireView(DOC_ID, STRANGER_ID))
				.isInstanceOf(PermissionDeniedException.class);
	}

	@Test
	void requireEditRejectsViewerWith403() {
		givenDocumentExists();
		given(documentPermissionRepository.findByDocumentIdAndUserId(DOC_ID, VIEWER_ID))
				.willReturn(Optional.of(permission(VIEWER_ID, DocumentRole.VIEWER)));

		assertThatThrownBy(() -> permissionService.requireEdit(DOC_ID, VIEWER_ID))
				.isInstanceOf(PermissionDeniedException.class)
				.hasMessageContaining("edit");
	}

	@Test
	void requireEditAllowsEditor() {
		givenDocumentExists();
		given(documentPermissionRepository.findByDocumentIdAndUserId(DOC_ID, EDITOR_ID))
				.willReturn(Optional.of(permission(EDITOR_ID, DocumentRole.EDITOR)));

		assertThatCode(() -> permissionService.requireEdit(DOC_ID, EDITOR_ID)).doesNotThrowAnyException();
	}

	@Test
	void requireOwnerRejectsEditorAndViewerWith403() {
		givenDocumentExists();

		assertThatThrownBy(() -> permissionService.requireOwner(DOC_ID, EDITOR_ID))
				.isInstanceOf(PermissionDeniedException.class);
		assertThatThrownBy(() -> permissionService.requireOwner(DOC_ID, VIEWER_ID))
				.isInstanceOf(PermissionDeniedException.class);
	}

	@Test
	void requireOwnerAllowsOwner() {
		givenDocumentExists();

		assertThatCode(() -> permissionService.requireOwner(DOC_ID, OWNER_ID)).doesNotThrowAnyException();
	}

	@Test
	void missingDocumentIs404EvenForTheOwner() {
		given(documentRepository.findById(DOC_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> permissionService.requireView(DOC_ID, OWNER_ID))
				.isInstanceOf(DocumentNotFoundException.class);
	}

}
