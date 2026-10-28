package com.sriman.cowrite.document.dto;

import com.sriman.cowrite.document.Document;

import java.time.Instant;

public record DocumentResponse(
		Long id,
		String title,
		String content,
		Instant createdAt,
		Instant updatedAt
) {

	public static DocumentResponse from(Document document) {
		return new DocumentResponse(
				document.getId(),
				document.getTitle(),
				document.getContent(),
				document.getCreatedAt(),
				document.getUpdatedAt()
		);
	}

}
