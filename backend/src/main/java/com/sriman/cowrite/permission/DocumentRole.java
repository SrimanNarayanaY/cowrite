package com.sriman.cowrite.permission;

/**
 * Roles granted on a shared document. The owner is not stored here:
 * ownership always comes from {@code documents.owner_id}.
 */
public enum DocumentRole {
	VIEWER,
	EDITOR
}
