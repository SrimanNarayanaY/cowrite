package com.sriman.cowrite.common.exception;

import com.sriman.cowrite.auth.exception.EmailAlreadyRegisteredException;
import com.sriman.cowrite.auth.exception.InvalidCredentialsException;
import com.sriman.cowrite.document.exception.DocumentNotFoundException;
import com.sriman.cowrite.permission.exception.CannotShareWithSelfException;
import com.sriman.cowrite.permission.exception.PermissionDeniedException;
import com.sriman.cowrite.user.exception.UserNotFoundException;
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
		return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
		return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
	}

	@ExceptionHandler(PermissionDeniedException.class)
	public ResponseEntity<ApiError> handlePermissionDenied(PermissionDeniedException ex) {
		return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
	}

	@ExceptionHandler(CannotShareWithSelfException.class)
	public ResponseEntity<ApiError> handleSelfShare(CannotShareWithSelfException ex) {
		return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ResponseEntity<ApiError> handleConflict(EmailAlreadyRegisteredException ex) {
		return error(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
		return error(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
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
		return error(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed request body");
	}

	private ResponseEntity<ApiError> error(HttpStatus status, String error, String message) {
		return ResponseEntity.status(status)
				.body(ApiError.of(status.value(), error, message));
	}

}
