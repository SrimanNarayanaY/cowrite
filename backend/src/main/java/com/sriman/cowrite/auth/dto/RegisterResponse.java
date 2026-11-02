package com.sriman.cowrite.auth.dto;

import com.sriman.cowrite.user.User;

import java.time.Instant;

public record RegisterResponse(
		Long id,
		String email,
		Instant createdAt
) {

	public static RegisterResponse from(User user) {
		return new RegisterResponse(user.getId(), user.getEmail(), user.getCreatedAt());
	}

}
