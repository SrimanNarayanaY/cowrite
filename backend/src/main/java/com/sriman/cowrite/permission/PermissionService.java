package com.sriman.cowrite.permission;

import com.sriman.cowrite.document.Document;
import com.sriman.cowrite.document.DocumentRepository;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.permission.exception.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of truth for "who may do what with a document".
 *
 * <p>Every entry point (REST controller today, WebSocket handler later) must call
 * {@code requireView/requireEdit/requireOwner}. Never copy permission {@code if}s into a
 * controller: the copy you forget to update becomes a security hole.</p>
 *
 * <p>Rules: OWNER = full control (from {@code documents.owner_id}),
 * EDITOR = read + update (from {@code document_permissions}),
 * VIEWER = read only.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

	private final DocumentRepository documentRepository;
	private final DocumentPermissionRepository documentPermissionRepository;

	public boolean isOwner(Long documentId, Long userId) {
		return documentRepository.existsByIdAndOwnerId(documentId, userId);
	}

	public boolean canView(Long documentId, Long userId) {
		return isOwner(documentId, userId) || hasAnyRole(documentId, userId);
	}

	public boolean canEdit(Long documentId, Long userId) {
		if (isOwner(documentId, userId)) {
			return true;
		}
		return roleOf(documentId, userId) == DocumentRole.EDITOR;
	}

	/**
	 * @throws DocumentNotFoundException 404 when the document does not exist at all
	 * @throws PermissionDeniedException 403 when it exists but is not shared with the caller
	 */
	public void requireView(Long documentId, Long userId) {
		Document document = loadDocument(documentId);

		if (isOwner(document, userId) || hasAnyRole(documentId, userId)) {
			return;
		}
		throw new PermissionDeniedException("view", documentId);
	}

	public void requireEdit(Long documentId, Long userId) {
		Document document = loadDocument(documentId);

		if (isOwner(document, userId) || roleOf(documentId, userId) == DocumentRole.EDITOR) {
			return;
		}
		throw new PermissionDeniedException("edit", documentId);
	}

	public void requireOwner(Long documentId, Long userId) {
		Document document = loadDocument(documentId);

		if (!isOwner(document, userId)) {
			throw new PermissionDeniedException("manage", documentId);
		}
	}

	private Document loadDocument(Long documentId) {
		return documentRepository.findById(documentId)
				.orElseThrow(() -> new DocumentNotFoundException(documentId));
	}

	/** Id-only check: reading the lazy owner proxy's id does not hit the database. */
	private boolean isOwner(Document document, Long userId) {
		return userId.equals(document.getOwner().getId());
	}

	private boolean hasAnyRole(Long documentId, Long userId) {
		return documentPermissionRepository.existsByDocumentIdAndUserId(documentId, userId);
	}

	private DocumentRole roleOf(Long documentId, Long userId) {
		return documentPermissionRepository.findByDocumentIdAndUserId(documentId, userId)
				.map(DocumentPermission::getRole)
				.orElse(null);
	}

}
