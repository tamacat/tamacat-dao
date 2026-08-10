/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.util.Collections;
import java.util.List;

import org.tamacat.dao.BindValue;

/**
 * A single execution record - the SQL text (with {@code ?} placeholders) together
 * with the bind values it was executed with. No masking, redaction, or omission is
 * applied to the values.
 *
 * @since 2.0
 */
public final class ExecutedStatement {

	private final String sql;
	private final List<BindValue> values;

	public ExecutedStatement(String sql, List<BindValue> values) {
		this.sql = sql;
		this.values = values == null ? Collections.<BindValue>emptyList()
				: Collections.unmodifiableList(values);
	}

	public String getSql() {
		return sql;
	}

	public List<BindValue> getValues() {
		return values;
	}
}
