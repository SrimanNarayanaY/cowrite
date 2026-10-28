package com.sriman.cowrite.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		Map<String, String> fieldErrors
) {

	public static ApiError of(int status, String error, String message) {
		return new ApiError(Instant.now(), status, error, message, Map.of());
	}

	public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors) {
		return new ApiError(Instant.now(), status, error, message, fieldErrors);
	}

}
