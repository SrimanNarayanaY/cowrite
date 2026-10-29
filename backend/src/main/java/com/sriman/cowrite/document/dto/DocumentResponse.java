package com.sriman.cowrite.document.dto;

import com.sriman.cowrite.document.Document;

import java.time.Instant;

public record DocumentResponse(
		Long id,
		String title,
		String content,
		Long ownerId,
		String ownerEmail,
		Instant createdAt,
		Instant updatedAt
) {

	/**
	 * Must be called inside the transaction/loading scope: {@code owner} is LAZY
	 * and is fetched here only when it was loaded eagerly (entity graph / join fetch).
	 */
	public static DocumentResponse from(Document document) {
		var owner = document.getOwner();

		return new DocumentResponse(
				document.getId(),
				document.getTitle(),
				document.getContent(),
				owner == null ? null : owner.getId(),
				owner == null ? null : owner.getEmail(),
				document.getCreatedAt(),
				document.getUpdatedAt()
		);
	}

}
