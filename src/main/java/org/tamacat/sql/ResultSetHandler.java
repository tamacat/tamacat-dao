/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A callback invoked with an open {@link ResultSet} by
 * {@code DBAccessManager#executeQuery(org.tamacat.dao.PreparedSql, ResultSetHandler)}.
 * The {@code ResultSet} is closed by the caller immediately after this method
 * returns - implementations must not retain or return it.
 *
 * @param <R> the result type produced from the {@code ResultSet}
 * @since 2.0
 */
@FunctionalInterface
public interface ResultSetHandler<R> {

	R handle(ResultSet rs) throws SQLException;
}
