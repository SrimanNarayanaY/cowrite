package com.sriman.cowrite.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body for {@code PUT /api/documents/{id}/content}.
 *
 * <p>{@code content} is the serialized editor document (Tiptap JSON for the React
 * client). The backend treats it as an opaque string, so changing the editor later
 * does not change the API.</p>
 *
 * <p>{@code version} is the value the client last read. Sending a stale version is
 * rejected with 409 instead of silently overwriting another user's work.</p>
 */
public record DocumentContentRequest(

		@NotNull(message = "content is required")
		String content,

		@NotNull(message = "version is required")
		@PositiveOrZero(message = "version must be zero or positive")
		Long version
) {
}
