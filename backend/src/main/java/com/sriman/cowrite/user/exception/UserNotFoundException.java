package com.sriman.cowrite.user.exception;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(String email) {
		super("User not found with email: " + email);
	}

}
