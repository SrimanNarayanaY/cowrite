package com.sriman.cowrite.permission.exception;

public class CannotShareWithSelfException extends RuntimeException {

	public CannotShareWithSelfException() {
		super("You cannot share a document with yourself");
	}

}
