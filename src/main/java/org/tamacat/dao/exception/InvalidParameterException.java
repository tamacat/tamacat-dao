/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.exception;

public class InvalidParameterException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public InvalidParameterException() {}

	public InvalidParameterException(String s) {
		super(s);
	}

	public InvalidParameterException(Throwable cause) {
		super(cause);
	}

	public InvalidParameterException(String message, Throwable cause) {
		super(message, cause);
	}
}
