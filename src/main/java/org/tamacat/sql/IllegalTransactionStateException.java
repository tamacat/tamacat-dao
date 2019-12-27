/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

public class IllegalTransactionStateException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public IllegalTransactionStateException(String message, Throwable cause) {
		super(message, cause);
	}

	public IllegalTransactionStateException(String message) {
		super(message);
	}

	public IllegalTransactionStateException(Throwable cause) {
		super(cause);
	}
}
