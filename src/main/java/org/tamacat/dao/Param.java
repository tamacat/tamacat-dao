/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.tamacat.dao.exception.InvalidParameterException;

/**
 * A predicate fragment - {@code ?}-bearing SQL text paired with its ordered bind
 * values. Not executable on its own; fragments are concatenated by callers into a
 * complete {@link PreparedSql}.
 *
 * <p>Immutable. {@link #and(Param)} and {@link #or(Param)} return a new instance and
 * never modify {@code this}.
 *
 * @since 2.0
 */
public final class Param {

	private final String sql;
	private final List<BindValue> values;
	private final int placeholderCount;

	private Param(String sql, List<BindValue> values, int placeholderCount) {
		this.sql = sql;
		this.values = values;
		this.placeholderCount = placeholderCount;
	}

	/**
	 * Creates a {@code Param}, verifying that the number of {@code ?} placeholders in
	 * {@code sql} (outside of quoted text) matches {@code values.size()}.
	 * @param sql the predicate fragment text. Must not be {@code null}
	 * @param values the ordered bind values. May be {@code null}, treated as empty
	 * @return a new {@code Param}
	 * @throws InvalidParameterException if {@code sql} is {@code null}, if the
	 *         placeholder count cannot be determined (unterminated quote), or if it
	 *         does not match {@code values.size()}
	 */
	public static Param of(String sql, List<BindValue> values) {
		if (sql == null) {
			throw new InvalidParameterException("sql is required.");
		}
		List<BindValue> v = values == null ? Collections.<BindValue>emptyList()
				: new ArrayList<>(values);
		int count = PlaceholderScanner.countPlaceholders(sql);
		if (count < 0) {
			throw new InvalidParameterException("Cannot verify placeholders: unterminated quote in SQL text. sql=[" + sql + "]");
		}
		if (count != v.size()) {
			throw new InvalidParameterException("Placeholder count mismatch: " + count
					+ " placeholder(s) but " + v.size() + " value(s). sql=[" + sql + "]");
		}
		return new Param(sql, Collections.unmodifiableList(v), count);
	}

	public String getSql() {
		return sql;
	}

	public List<BindValue> getValues() {
		return values;
	}

	/**
	 * @return the number of bind values (equal to the placeholder count)
	 */
	public int size() {
		return values.size();
	}

	/**
	 * Concatenates {@code this} and {@code other} with {@code " and "}. If
	 * {@code other} is {@code null}, returns {@code this}.
	 * @param other the other fragment, or {@code null}
	 * @return a new {@code Param} representing the conjunction
	 */
	public Param and(Param other) {
		if (other == null) {
			return this;
		}
		return concat(" and ", other);
	}

	/**
	 * Concatenates {@code this} and {@code other} with {@code " or "}. If
	 * {@code other} is {@code null}, returns {@code this}.
	 * @param other the other fragment, or {@code null}
	 * @return a new {@code Param} representing the disjunction
	 */
	public Param or(Param other) {
		if (other == null) {
			return this;
		}
		return concat(" or ", other);
	}

	private Param concat(String joiner, Param other) {
		String combinedSql = this.sql + joiner + other.sql;
		List<BindValue> combined = new ArrayList<>(this.values.size() + other.values.size());
		combined.addAll(this.values);
		combined.addAll(other.values);
		return new Param(combinedSql, Collections.unmodifiableList(combined),
				this.placeholderCount + other.placeholderCount);
	}
}
