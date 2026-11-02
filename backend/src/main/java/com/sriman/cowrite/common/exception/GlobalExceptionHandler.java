package com.sriman.cowrite.common.exception;

import com.sriman.cowrite.auth.exception.EmailAlreadyRegisteredException;
import com.sriman.cowrite.auth.exception.InvalidCredentialsException;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DocumentNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(DocumentNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
				ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage())
		);
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ResponseEntity<ApiError> handleConflict(EmailAlreadyRegisteredException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage())
		);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage())
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return ResponseEntity.badRequest().body(
				ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Validation failed", fieldErrors)
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest().body(
				ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Malformed request body")
		);
	}

}
