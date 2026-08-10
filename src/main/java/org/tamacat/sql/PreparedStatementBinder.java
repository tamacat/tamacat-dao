/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.tamacat.dao.BindValue;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.meta.DataType;

/**
 * Applies an ordered {@link BindValue} list to a JDBC {@link PreparedStatement},
 * 1-based position by position, in list order (no reordering - business-rules.md
 * BR-15).
 *
 * @since 2.0
 */
final class PreparedStatementBinder {

	private PreparedStatementBinder() {
	}

	static void bind(PreparedStatement stmt, List<BindValue> values) {
		try {
			for (int i = 0; i < values.size(); i++) {
				BindValue v = values.get(i);
				int pos = i + 1;
				if (v.isNull()) {
					stmt.setNull(pos, sqlTypeOf(v.getType()));
				} else if (v.getType() == DataType.OBJECT) {
					stmt.setBinaryStream(pos, v.getStream());
				} else {
					stmt.setString(pos, v.getValue());
				}
			}
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	static int sqlTypeOf(DataType type) {
		switch (type) {
			case NUMERIC:
			case FLOAT:
				return Types.NUMERIC;
			case DATE:
				return Types.DATE;
			case TIME:
				return Types.TIMESTAMP;
			case OBJECT:
				return Types.BLOB;
			case FUNCTION:
				return Types.OTHER; // unreachable (BR-9): FUNCTION never produces a BindValue
			case STRING:
			case BOOLEAN:
			default:
				return Types.VARCHAR;
		}
	}
}
