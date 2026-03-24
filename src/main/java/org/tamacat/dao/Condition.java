/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import org.tamacat.dao.Search.Conditions;

public enum Condition implements Conditions {

	LIKE_HEAD(" like ", "#{value1}%"),
	LIKE_PART(" like ", "%#{value1}%"),
	LIKE_TAIL(" like ", "%#{value1}"),
	EQUAL("=", "#{value1}"),
	NOT_EQUAL("<>", "#{value1}"),
	BETWEEN(" between ", "#{value1} and #{value2}"),
	LESS("<", "#{value1}"),
	GREATER(">", "#{value1}"),
	LESS_OR_EQUAL("<=", "#{value1}"),
	GREATER_OR_EQUAL(">=", "#{value1}"),
	IS_NULL(" is null", null),
	NOT_NULL(" not null", null),
	IN(" in ", "(#{values})")
	;

	private final String replaceHolder;
	private final String condition;

	private Condition(String condition, String replaceHolder) {
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
