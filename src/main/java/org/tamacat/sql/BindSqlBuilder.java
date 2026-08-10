/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.tamacat.dao.BindValue;
import org.tamacat.dao.Param;
import org.tamacat.dao.Search.Conditions;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.util.MappingUtils;

/**
 * Builds {@code ?}-bearing predicate fragments ({@link Param}) instead of literal
 * SQL text - the bind-path mirror of {@link SQLParser#value(Column, Conditions, String...)}.
 *
 * <p>Reproduces {@code SQLParser.value(...)}'s structure exactly, including its three
 * known defects (BETWEEN with 3+ values, BETWEEN with 1 value, IS_NULL/NOT_NULL with a
 * value) - these are preserved on purpose, not fixed, per FR-2's "preserve current
 * behavior" mandate.
 *
 * @since 2.0
 */
public final class BindSqlBuilder {

	/**
	 * A text token plus the {@link BindValue} it corresponds to (or {@code null} if
	 * the token is not bound - e.g. a SQL function or FUNCTION-column literal).
	 */
	private static final class Token {
		final String text;
		final BindValue bind;

		Token(String text, BindValue bind) {
			this.text = text;
			this.bind = bind;
		}
	}

	/**
	 * Classifies a single value and produces its text token and (if any) bind value.
	 * See business-logic-model.md sec 2.7 for the decision tree.
	 */
	private Token tokenFor(Column column, String value) {
		DataType t = column.getType();
		if (t == DataType.OBJECT) {
			return new Token("?", BindValue.ofNull(DataType.OBJECT));
		}
		if (t == DataType.FUNCTION) {
			return new Token(value == null ? "null" : value, null);
		}
		if (ValueRules.isNullValue(column, value)) {
			return new Token("?", BindValue.ofNull(t));
		}
		if (ValueRules.isSqlFunction(column, value)) {
			return new Token(value, null);
		}
		ValueRules.validate(column, value);
		return new Token("?", BindValue.of(t, value));
	}

	private static void append(StringBuilder text, List<BindValue> binds, String template, String marker, Token tk) {
		text.append(template.replace(marker, tk.text));
		if (tk.bind != null) {
			binds.add(tk.bind);
		}
	}

	/**
	 * Bind-path equivalent of {@code SQLParser.value(Column, Conditions, String...)}.
	 */
	public Param value(Column column, Conditions condition, String... values) {
		String colName = MappingUtils.getColumnName(column);
		StringBuilder text = new StringBuilder(colName).append(condition.getCondition());
		List<BindValue> binds = new ArrayList<>();
		if (values != null) {
			if (values.length == 1) {
				String value = values[0];
				if (ValueRules.isRequiredButEmpty(column, value)) {
					throw new InvalidParameterException("Column [" + colName + "] is required.");
				}
				if ((column.getType() == DataType.STRING || column.getType() == DataType.BOOLEAN)
						&& condition.getCondition().indexOf(" like ") >= 0) {
					LikeEscape le = ValueRules.escapeLike(condition, column, value);
					text.append("?");
					if (le.hasEscape()) {
						text.append(" escape '").append(le.getEscapeChar()).append("'");
					}
					binds.add(BindValue.of(column.getType(), le.getBoundValue()));
				} else if (condition.getCondition().equals(" in ")) {
					append(text, binds, condition.getReplaceHolder(),
							SQLParser.MULTI_VALUE, tokenFor(column, value));
				} else {
					append(text, binds, condition.getReplaceHolder(),
							SQLParser.VALUE1, tokenFor(column, value == null ? "" : value));
				}
			} else if (values.length >= 2) {
				if (condition.getCondition().indexOf(" between ") >= 0) {
					String t = condition.getReplaceHolder();
					for (int i = 0; i < values.length; i++) {
						Token tk = tokenFor(column, values[i]);
						t = t.replace(SQLParser.VALUES[i], tk.text);
						if (tk.bind != null) {
							binds.add(tk.bind);
						}
					}
					text.append(t);
				} else { // IN
					StringBuilder tokens = new StringBuilder();
					for (String v : values) {
						if (tokens.length() > 0) {
							tokens.append(",");
						}
						Token tk = tokenFor(column, v);
						tokens.append(tk.text);
						if (tk.bind != null) {
							binds.add(tk.bind);
						}
					}
					text.append(condition.getReplaceHolder()
							.replace(SQLParser.MULTI_VALUE, tokens.toString()));
				}
			}
		}
		return Param.of(text.toString(), binds);
	}

	/**
	 * Bind-path equivalent of {@code SQLParser.parseValue(Column, String)}, for use
	 * in the VALUES clause of an INSERT. Unlike {@link #value}, does not replace a
	 * {@code null} value with an empty string (that substitution belongs to the
	 * single-value predicate path only - business-rules.md BR-5).
	 */
	public Param placeholder(Column column, String value) {
		Token tk = tokenFor(column, value);
		return Param.of(tk.text, tk.bind == null
				? Collections.<BindValue>emptyList() : Collections.singletonList(tk.bind));
	}

	/**
	 * Emits {@code function} as literal SQL text with no bind values. The caller must
	 * not include {@code ?} in {@code function} - {@link Param#of} would then detect a
	 * placeholder with zero values and throw {@link InvalidParameterException}.
	 */
	public Param sqlFunction(Column column, String function) {
		return Param.of(function == null ? "null" : function,
				Collections.<BindValue>emptyList());
	}
}
