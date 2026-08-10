/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import org.tamacat.dao.exception.InvalidParameterException;

/**
 * Validation rules for SQL identifier positions (table names, column names,
 * ORDER BY expressions) that cannot be bind-parameterized - JDBC has no
 * {@code ?} placeholder for identifiers. Safety here is validate-and-reject
 * of dangerous literal characters, not binding (business-logic-model.md sec 1).
 *
 * <p>Public - callers are {@code org.tamacat.dao.Sort} and
 * {@code org.tamacat.dao.impl.QueryImpl}, both in different packages from
 * {@code org.tamacat.sql}; package-private would not reach them (same
 * constraint as U1's {@code PreparedSql.getPlaceholderCount()} and U2's
 * {@code Search.getSearchParam()}, domain-entities.md sec "2.6 / 2.7").
 *
 * @since 2.0
 */
public final class IdentifierRules {

	private IdentifierRules() {
	}

	/**
	 * Rejects {@code raw} if it contains any of the dangerous literal
	 * characters/sequences {@code '} {@code "} {@code ;} {@code --}
	 * {@code /*} {@code *}{@code /} (ID-1..ID-5, business-rules.md BR-10).
	 * A simple chain of {@code String.indexOf} calls, linear in input length -
	 * no regex, no backtracking risk (BR-1).
	 *
	 * <p>{@code null} is passed through without throwing (BR-14) - this is
	 * deliberate, not an oversight: {@code Column.isFunction() == true} with
	 * {@code Column.getFunctionName() == null} is a real, reachable, legitimate
	 * existing state (a {@code Column} defined only with the {@code FUNCTION}
	 * {@code ColumnDefine}, never given a {@code functionName}; see
	 * {@code ColumnFunctionTest}), and the current SELECT-clause output for
	 * that state ({@code "null " + columnName}) must keep working. {@code null}
	 * cannot carry any of ID-1..ID-5, so passing it through is safe.
	 *
	 * @param raw the identifier text to validate, or {@code null}
	 * @return {@code raw}, unchanged (no escaping or normalization, IR-2)
	 * @throws InvalidParameterException if {@code raw} contains a dangerous literal
	 */
	public static String validate(String raw) {
		if (raw == null) {
			return null;
		}
		if (raw.indexOf('\'') >= 0 || raw.indexOf('"') >= 0 || raw.indexOf(';') >= 0
				|| raw.indexOf("--") >= 0 || raw.indexOf("/*") >= 0 || raw.indexOf("*/") >= 0) {
			throw new InvalidParameterException("Unsafe identifier: [" + raw + "]");
		}
		return raw;
	}
}
