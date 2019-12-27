/*
 * Copyright (c) 2015 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.exception;

import org.tamacat.dao.meta.Column;

public class InvalidValueLengthException extends InvalidParameterException {
	
	private static final long serialVersionUID = 1L;
	protected Column column;
	protected String value;
	
	public InvalidValueLengthException() {}
	
	public InvalidValueLengthException(String s) {
		super(s);
	}
	
	public InvalidValueLengthException(String s, Column column) {
		super(s);
		this.column = column;
	}
	
	public InvalidValueLengthException(String s, Column column, String value) {
		super(s);
		this.column = column;
		this.value = value;
	}
	
	public Column getColumn() {
		return column;
	}
	
	public String getValue() {
		return value;
	}
}
