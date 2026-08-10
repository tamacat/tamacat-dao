/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tamacat.dao.Search.Conditions;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.util.StringUtils;

/**
 * Value classification, validation, and LIKE-escaping rules shared by the literal
 * path ({@link SQLParser}) and the bind path ({@code BindSqlBuilder}). Extracted
 * verbatim from {@code SQLParser} (formerly {@code SQLParser.java:39-150}) - the
 * output of every method here must match the original byte-for-byte
 * (business-rules.md BR-31).
 *
 * @since 2.0
 */
final class ValueRules {

	static final char[] ESCAPE_CANDIDATES = { '$', '#', '~', '!', '^' };

	private ValueRules() {
	}

	/**
	 * @since 1.4-20180217
	 * @param value
	 */
	static boolean isNumeric(String value) {
		if (StringUtils.isEmpty(value)) return false;
		Pattern p = Pattern.compile("^\\-?[0-9]*\\.?[0-9]+$");
		Matcher m = p.matcher(value);
		return m.find();
	}

	/**
	 * Validates a NUMERIC/FLOAT column's value. No-op for other types, and for
	 * empty values (which are treated as SQL NULL by {@link #isNullValue}).
	 * @throws InvalidParameterException if the value is non-empty and not numeric
	 */
	static void validate(Column column, String value) {
		DataType t = column.getType();
		if (t != DataType.NUMERIC && t != DataType.FLOAT) return;
		if (StringUtils.isEmpty(value)) return;
		if (isNumeric(value)) return;
		throw new InvalidParameterException("value is not numeric.");
	}

	/**
	 * The required-check predicate. Applies to the single-value path only
	 * (business-rules.md BR-1) - the caller is responsible for throwing, since the
	 * message needs the table-qualified column name that the caller already has.
	 */
	static boolean isRequiredButEmpty(Column column, String value) {
		return StringUtils.isEmpty(value) && column.isNotNull();
	}

	static boolean isNullValue(Column column, String value) {
		switch (column.getType()) {
			case NUMERIC:
			case FLOAT:
				return StringUtils.isEmpty(value);
			case DATE:
			case TIME:
				return StringUtils.isEmpty(value)
					|| SQLParser.NULL_VALUE.equalsIgnoreCase(value);
			case OBJECT:
				return false;
			default:
				return value == null;
		}
	}

	static boolean isSqlFunction(Column column, String value) {
		DataType t = column.getType();
		return (t == DataType.DATE || t == DataType.TIME)
			&& value != null
			&& value.equalsIgnoreCase("current_timestamp");
	}

	/**
	 * Builds the LIKE-predicate value: wraps {@code value} with the condition's
	 * template and escapes {@code %}/{@code _} if present, choosing the first escape
	 * candidate not already present in the raw value. The bound value returned
	 * includes the wrap and the escape substitution, but not quote-escaping
	 * ({@code ValueConvertFilter}) or quoting - those are applied by the literal
	 * path only (business-rules.md BR-13, BR-18).
	 */
	static LikeEscape escapeLike(Conditions condition, Column column, String value) {
		String v = (value == null) ? "" : value;
		if (v.indexOf('%') >= 0 || v.indexOf('_') >= 0) {
			for (char e : ESCAPE_CANDIDATES) {
				if (v.indexOf(e) == -1) {
					String escaped = v.replace("%", e + "%").replace("_", e + "_");
					String wrapped = condition.getReplaceHolder()
							.replace(SQLParser.VALUE1, escaped);
					return LikeEscape.of(wrapped, e);
				}
			}
			// all 5 candidates are present in the value; fall through unescaped.
		}
		String wrapped = condition.getReplaceHolder().replace(SQLParser.VALUE1, v);
		return LikeEscape.noEscape(wrapped);
	}
}
