package com.sriman.cowrite.document.dto;

import com.sriman.cowrite.document.Document;
import com.sriman.cowrite.permission.DocumentAccess;

import java.time.Instant;

/**
 * Detail response: everything the editor needs, including the caller's access level.
 *
 * <p>The list endpoint keeps {@link DocumentResponse} on purpose: resolving access
 * per row would mean one permission query per document (N+1).</p>
 */
public record DocumentDetailResponse(
		Long id,
		String title,
		String content,
		Long version,
		Long ownerId,
		String ownerEmail,
		Instant createdAt,
		Instant updatedAt,
		DocumentAccess access,
		boolean canEdit
) {

	public static DocumentDetailResponse from(Document document, DocumentAccess access) {
		var owner = document.getOwner();

		return new DocumentDetailResponse(
				document.getId(),
				document.getTitle(),
				document.getContent(),
				document.getVersion(),
				owner == null ? null : owner.getId(),
				owner == null ? null : owner.getEmail(),
				document.getCreatedAt(),
				document.getUpdatedAt(),
				access,
				access == DocumentAccess.OWNER || access == DocumentAccess.EDITOR
		);
	}
}
