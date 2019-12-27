/*
 * Copyright (c) 2016 tamacat.org
 * All rights reserved.
 */
package org.tamacat.pool;

/**
 * Throws maximum number of pool objects.
 * @since 1.3-20160325
 */
public class PoolLimitException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PoolLimitException() {
	}

	public PoolLimitException(String message) {
		super(message);
	}

	public PoolLimitException(Throwable cause) {
		super(cause);
	}

	public PoolLimitException(String message, Throwable cause) {
		super(message, cause);
	}

}
