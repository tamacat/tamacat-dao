/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.pool;

public class ObjectActivateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ObjectActivateException() {
    }

    public ObjectActivateException(String message) {
        super(message);
    }

    public ObjectActivateException(Throwable cause) {
        super(cause);
    }

    public ObjectActivateException(String message, Throwable cause) {
        super(message, cause);
    }

}
