package com.sriman.cowrite.permission.dto;

import com.sriman.cowrite.permission.DocumentRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareRequest(

		@NotBlank(message = "email is required")
		@Email(message = "email must be a valid address")
		String email,

		@NotNull(message = "role is required (VIEWER or EDITOR)")
		DocumentRole role
) {
}
