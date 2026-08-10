/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.DataType;

/**
 * A complete, executable SQL statement - {@code ?}-bearing SQL text paired with its
 * ordered bind values.
 *
 * <p>Two factories produce different provenance:
 * <ul>
 *   <li>{@link #of(String, List)} (checked) - verifies the placeholder count against
 *       {@code values.size()} at construction time. {@link #hasUnboundPlaceholders()}
 *       is always {@code false} for a checked instance.</li>
 *   <li>{@link #ofLiteral(String)} (unchecked) - a compatibility shim for the legacy
 *       literal path; performs no construction-time check. The placeholder count is
 *       evaluated lazily on first call to {@link #getPlaceholderCount()}.</li>
 * </ul>
 *
 * <p>Immutable and unrelated by inheritance to {@link Param} - a {@code Param}
 * fragment cannot be passed where a {@code PreparedSql} is required, and vice versa.
 *
 * @since 2.0
 */
public final class PreparedSql {

	private static final int UNCOUNTED = Integer.MIN_VALUE;

	private final String sql;
	private final List<BindValue> values;
	private final boolean checked;
	private int placeholderCount;

	private PreparedSql(String sql, List<BindValue> values, int placeholderCount, boolean checked) {
		this.sql = sql;
		this.values = values;
		this.placeholderCount = placeholderCount;
		this.checked = checked;
	}

	/**
	 * Creates a checked {@code PreparedSql}, verifying that the number of {@code ?}
	 * placeholders in {@code sql} (outside of quoted text) matches
	 * {@code values.size()}.
	 * @param sql the complete SQL text. Must not be {@code null}
	 * @param values the ordered bind values. May be {@code null}, treated as empty
	 * @return a new checked {@code PreparedSql}
	 * @throws InvalidParameterException if {@code sql} is {@code null}, if the
	 *         placeholder count cannot be determined (unterminated quote), or if it
	 *         does not match {@code values.size()}
	 */
	public static PreparedSql of(String sql, List<BindValue> values) {
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
		return new PreparedSql(sql, Collections.unmodifiableList(v), count, true);
	}

	/**
	 * Creates an unchecked {@code PreparedSql} from a legacy literal-path SQL string.
	 * A compatibility shim: this instance carries no values, and the placeholder scan
	 * is deferred until {@link #getPlaceholderCount()} is first called.
	 * @param sql the complete SQL text. Must not be {@code null}
	 * @return a new unchecked {@code PreparedSql}
	 * @throws InvalidParameterException if {@code sql} is {@code null}
	 */
	public static PreparedSql ofLiteral(String sql) {
		if (sql == null) {
			throw new InvalidParameterException("sql is required.");
		}
		return new PreparedSql(sql, Collections.<BindValue>emptyList(), UNCOUNTED, false);
	}

	public String getSql() {
		return sql;
	}

	public List<BindValue> getValues() {
		return values;
	}

	/**
	 * @return the number of {@code ?} placeholders in {@link #getSql()} outside of
	 *         quoted text, or {@code -1} if this could not be determined (unterminated
	 *         quote). For a checked instance this is the value fixed at construction
	 *         time; for an unchecked instance the scan runs lazily on first call.
	 */
	public int getPlaceholderCount() {
		if (!checked && placeholderCount == UNCOUNTED) {
			placeholderCount = PlaceholderScanner.countPlaceholders(sql);
		}
		return placeholderCount;
	}

	/**
	 * Returns the 1-based bind position of the {@code occurrence}-th (1-based) value
	 * of type {@code type} in {@link #getValues()}, scanning from the start.
	 * @param type the {@code DataType} to look for
	 * @param occurrence the 1-based occurrence to find
	 * @return the 1-based bind position, or {@code -1} if there is no such occurrence
	 */
	public int getBindIndexOf(DataType type, int occurrence) {
		int seen = 0;
		for (int i = 0; i < values.size(); i++) {
			if (values.get(i).getType() == type) {
				seen++;
				if (seen == occurrence) {
					return i + 1;
				}
			}
		}
		return -1;
	}

	/**
	 * @return {@code true} if this {@code PreparedSql} cannot safely be executed as-is:
	 *         for a checked instance, always {@code false}; for an unchecked instance,
	 *         {@code true} if the placeholder scan is unterminated ({@code -1}) or if
	 *         the placeholder count exceeds {@link #getValues()}'s size
	 */
	public boolean hasUnboundPlaceholders() {
		if (checked) {
			return false;
		}
		int count = getPlaceholderCount();
		if (count < 0) {
			return true;
		}
		return count > values.size();
	}
}
