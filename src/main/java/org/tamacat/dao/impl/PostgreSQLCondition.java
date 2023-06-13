/*
 * Copyright (c) 2023 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import org.tamacat.dao.Search.Conditions;

public enum PostgreSQLCondition implements Conditions {
	
	REGEXP(" ~ ", "#{value1}");

	private final String replaceHolder;
	private final String condition;

	private PostgreSQLCondition(String condition, String replaceHolder) {
		this.condition = condition;
		this.replaceHolder = replaceHolder;
	}

	public String getReplaceHolder() {
		return replaceHolder;
	}

	public String getCondition() {
		return condition;
	}
}
