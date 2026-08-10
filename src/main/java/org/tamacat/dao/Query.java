/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.util.Collection;

import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.Table;
import org.tamacat.dao.orm.ORMappingSupport;

/**
 * Interface of the internal query used inside Dao class.
 * 
 * ex)
 * <pre>
 * Query&lt;User&gt; query = createQuery().select(User.TABLE.getColumns()).and(search, sort);
 * </pre>
 * 
 * @param <T> extends ORMappingSupport
 */
public interface Query<T extends ORMappingSupport<T>> {

	Query<T> select(Collection<Column> columns);

	Query<T> select(Column... columns);

	Query<T> distinct(boolean distinct);

	/**
	 * Get the target columns for SELECT statement.
	 */
	Collection<Column> getSelectColumns();

	/**
	 * The column included in INSERT/UPDATE/DELETE statement is added.
	 * 
	 * @param column
	 */
	Query<T> addUpdateColumn(Column column);

	/**
	 * The columns included in INSERT/UPDATE/DELETE statement is added.
	 * 
	 * @param columns
	 */
	Query<T> addUpdateColumns(Collection<Column> columns);

	/**
	 * The columns included in INSERT/UPDATE/DELETE statement is added.
	 * 
	 * @param columns
	 */
	Query<T> addUpdateColumns(Column... columns);

	/**
	 * Remove columns from getUpdateColumns()
	 * 
	 * @param columns
	 * @return
	 * @sinse 1.3
	 */
	Query<T> removeUpdateColumns(Column... columns);

	Collection<Column> getUpdateColumns();

	Query<T> addTable(Table table);

	Query<T> removeFromTables(Table... tables);

	/**
	 * Add the inner join condition into the Query.
	 * 
	 * @param col1
	 *            connect Key1
	 * @param col2
	 *            connect Key2
	 */
	Query<T> join(Column col1, Column col2);

	/**
	 * Add the outer join condition into the Query. (LEFT JOIN)
	 * 
	 * @param col1
	 *            connect key1 (required data)
	 * @param col2
	 *            connect key2 (optional data)
	 */
	Query<T> outerJoin(Column col1, Column col2);

	/**
	 * Add the outer join condition into the Query, append "AND" conditon with
	 * Search object.
	 * 
	 * @param table
	 *            required table
	 * @param search
	 *            append Search object
	 */
	/**
	 * @deprecated use {@link #andOuterJoin(Table, Param)}. This method keeps the
	 *             legacy literal-embedding behavior unchanged and is outside the
	 *             SM-1 prepared-statement path.
	 */
	@Deprecated
	Query<T> andOuterJoin(Table table, Search search);

	/**
	 * Bind-path equivalent of {@link #andOuterJoin(Table, Search)}. Adds
	 * {@code param}'s text (and values) as an {@code AND} condition onto the outer
	 * join expression previously registered for {@code table} via
	 * {@link #outerJoin(Column, Column)}. Does nothing if no such outer join exists
	 * yet, matching the "key not present" no-op of the legacy method.
	 *
	 * <p>The default implementation does nothing ({@code return this;}) - there is
	 * no way to derive a {@link Search} from a {@link Param}'s text, so this default
	 * cannot delegate to {@link #andOuterJoin(Table, Search)} the way the other
	 * {@code default} methods delegate to their legacy counterparts.
	 * @since 2.0
	 */
	default Query<T> andOuterJoin(Table table, Param param) {
		return this;
	}

	/**
	 * Add the WHERE condition into the Query.
	 * 
	 * @param search
	 * @param sort
	 * @return
	 */
	Query<T> where(Search search, Sort sort);

	/**
	 * Add the "Search" and "Sort" object with "AND" prefix condition into the
	 * Query. ("WHERE xxx=123 AND yyy=456 ORDER BY xxx" statement.)
	 * 
	 * @param search
	 * @param sort
	 */
	Query<T> and(Search search, Sort sort);

	/**
	 * Add the "Search" and "Sort" object with "OR prefix connect to Query.
	 * ("WHERE xxx=123 OR yyy=456 ORDER BY xxx" statement.)
	 * 
	 * @param search
	 * @param sort
	 */
	Query<T> or(Search search, Sort sort);

	Query<T> where(String sql);

	Query<T> and(String sql);

	Query<T> or(String sql);

	/**
	 * Bind-path equivalent of {@link #where(String)}. The default implementation
	 * delegates to {@link #where(String)} with {@code param.getSql()}, which
	 * discards {@code param}'s bind values (ADR-006) - override to route the
	 * values through as well.
	 * @since 2.0
	 */
	default Query<T> where(Param param) {
		return where(param.getSql());
	}

	/**
	 * Bind-path equivalent of {@link #and(String)}. See {@link #where(Param)} for
	 * the default's value-discarding caveat.
	 * @since 2.0
	 */
	default Query<T> and(Param param) {
		return and(param.getSql());
	}

	/**
	 * Bind-path equivalent of {@link #or(String)}. See {@link #where(Param)} for
	 * the default's value-discarding caveat.
	 * @since 2.0
	 */
	default Query<T> or(Param param) {
		return or(param.getSql());
	}

	Query<T> andIn(Column column, Query<T> query);

	Query<T> andNotIn(Column column, Query<T> query);

	Query<T> andExists(Query<T> query);

	Query<T> andNotExists(Query<T> query);

	Query<T> groupBy(Column... columns);

	Query<T> orderBy(Sort sort);

	/**
	 * @deprecated use {@link #getSelectPreparedSql()}. Return value unchanged.
	 */
	@Deprecated
	String getSelectSQL();

	/**
	 * @deprecated use {@link #getInsertPreparedSql(ORMappingSupport)}. Return
	 *             value unchanged.
	 */
	@Deprecated
	String getInsertSQL(T data);

	/**
	 * @deprecated use {@link #getUpdatePreparedSql(ORMappingSupport)}. Return
	 *             value unchanged.
	 */
	@Deprecated
	String getUpdateSQL(T data);

	/**
	 * @deprecated use {@link #getDeletePreparedSql(ORMappingSupport)}. Return
	 *             value unchanged.
	 */
	@Deprecated
	String getDeleteSQL(T data);

	/**
	 * @deprecated use {@link #getDeleteAllPreparedSql(Table)}. Return value
	 *             unchanged.
	 */
	@Deprecated
	String getDeleteAllSQL(Table table);

	/**
	 * Bind-path equivalent of {@link #getSelectSQL()}. The default implementation
	 * wraps {@link #getSelectSQL()}'s result via {@link PreparedSql#ofLiteral(String)}
	 * (ADR-006) - no values, unbound-placeholder checks deferred to execution time.
	 * @since 2.0
	 */
	default PreparedSql getSelectPreparedSql() {
		return PreparedSql.ofLiteral(getSelectSQL());
	}

	/**
	 * Bind-path equivalent of {@link #getInsertSQL(ORMappingSupport)}. See
	 * {@link #getSelectPreparedSql()} for the default's fallback behavior.
	 * @since 2.0
	 */
	default PreparedSql getInsertPreparedSql(T data) {
		return PreparedSql.ofLiteral(getInsertSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getUpdateSQL(ORMappingSupport)}. See
	 * {@link #getSelectPreparedSql()} for the default's fallback behavior.
	 * @since 2.0
	 */
	default PreparedSql getUpdatePreparedSql(T data) {
		return PreparedSql.ofLiteral(getUpdateSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getDeleteSQL(ORMappingSupport)}. See
	 * {@link #getSelectPreparedSql()} for the default's fallback behavior.
	 * @since 2.0
	 */
	default PreparedSql getDeletePreparedSql(T data) {
		return PreparedSql.ofLiteral(getDeleteSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getDeleteAllSQL(Table)}. See
	 * {@link #getSelectPreparedSql()} for the default's fallback behavior.
	 * @since 2.0
	 */
	default PreparedSql getDeleteAllPreparedSql(Table table) {
		return PreparedSql.ofLiteral(getDeleteAllSQL(table));
	}

	int getBlobIndex();

	/**
	 * @since 1.3
	 */
	String getTimestampString();

	/**
	 * Set the primary key auto making update SQL statement feature using.
	 * 
	 * @param useAutoPrimaryKeyUpdate
	 *            ture:use, false:unuse
	 * @deprecated @see autoPrimaryKeyUpdate(boolean useAutoPrimaryKeyUpdate)
	 */
	Query<T> setUseAutoPrimaryKeyUpdate(boolean useAutoPrimaryKeyUpdate);

	/**
	 * Set the primary key auto making update SQL statement feature using.
	 * 
	 * @param useAutoPrimaryKeyUpdate
	 *            ture:use, false:unuse
	 */
	Query<T> autoPrimaryKeyUpdate(boolean useAutoPrimaryKeyUpdate);
}
