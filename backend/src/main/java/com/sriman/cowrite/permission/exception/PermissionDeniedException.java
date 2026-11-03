package com.sriman.cowrite.permission.exception;

/**
 * The caller is authenticated but has no permission for this action.
 * Always a 403 — never a 500, and never a silent no-op.
 */
public class PermissionDeniedException extends RuntimeException {

	public PermissionDeniedException(String action, Long documentId) {
		super("You do not have permission to " + action + " document " + documentId);
	}

}
