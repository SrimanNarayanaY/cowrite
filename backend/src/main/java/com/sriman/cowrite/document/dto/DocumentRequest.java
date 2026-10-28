package com.sriman.cowrite.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(

		@NotBlank(message = "title is required")
		@Size(max = 200, message = "title must be at most 200 characters")
		String title,

		String content
) {
}
