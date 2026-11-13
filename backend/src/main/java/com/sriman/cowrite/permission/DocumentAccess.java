package com.sriman.cowrite.permission;

/**
 * The caller's relationship to one document.
 *
 * <p>Unlike {@link DocumentRole} (which only describes a row in
 * {@code document_permissions}), this includes {@code OWNER}, because ownership is
 * stored on the document itself.</p>
 */
public enum DocumentAccess {
	OWNER,
	EDITOR,
	VIEWER
}
