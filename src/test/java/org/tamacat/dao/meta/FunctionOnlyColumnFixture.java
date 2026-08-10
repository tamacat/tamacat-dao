/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

/**
 * Test-only factory for a {@code Column} in the state
 * {@code isFunction() == true} / {@code getFunctionName() == null} - reachable
 * only via the {@code DefaultColumn(Table, String, DataType, String,
 * ColumnDefine...)} constructor's {@code Column.FUNCTION} define, never given
 * a {@code functionName(...)} (business-logic-model.md sec 1, BR-14).
 * {@code ColumnDefine} is package-private, so this factory - and not the
 * caller - must live in {@code org.tamacat.dao.meta}.
 */
public final class FunctionOnlyColumnFixture {

	private FunctionOnlyColumnFixture() {
	}

	public static Column create(Table table, String columnName) {
		return new DefaultColumn(table, columnName, DataType.FUNCTION, columnName, Column.FUNCTION);
	}

	/**
	 * A plain {@code DataType.FUNCTION} column with no {@code ColumnDefine}
	 * (so {@code isFunction() == false} until {@code functionName(...)} is
	 * called). Exists purely so callers outside this package - where
	 * {@code ColumnDefine} is inaccessible even for an implicit empty varargs
	 * array - can construct a {@code DefaultColumn(Table, String, DataType,
	 * String)}-shaped fixture.
	 */
	public static Column createPlain(Table table, String columnName) {
		return new DefaultColumn(table, columnName, DataType.FUNCTION, columnName);
	}
}
