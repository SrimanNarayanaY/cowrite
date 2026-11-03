package com.sriman.cowrite.permission.dto;

import com.sriman.cowrite.permission.DocumentRole;

public record ShareResponse(
		Long documentId,
		Long userId,
		String email,
		DocumentRole role
) {
}
