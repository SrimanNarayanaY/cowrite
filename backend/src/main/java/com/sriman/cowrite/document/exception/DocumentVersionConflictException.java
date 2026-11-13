package com.sriman.cowrite.document.exception;

/**
 * The client tried to save a version that is no longer current.
 * Mapped to 409 Conflict by the global exception handler.
 */
public class DocumentVersionConflictException extends RuntimeException {

	public DocumentVersionConflictException(Long documentId, Long sentVersion, Long currentVersion) {
		super("Document " + documentId + " was changed by someone else (your version: "
				+ sentVersion + ", current version: " + currentVersion + "). Reload and try again.");
	}
}
