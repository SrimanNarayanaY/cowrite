package com.sriman.cowrite.auth.exception;

/**
 * Same exception for "unknown email" and "wrong password" so the API cannot be used
 * to enumerate which emails are registered.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid credentials");
	}

}
