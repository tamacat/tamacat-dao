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
	Query<T> andOuterJoin(Table table, Search search);

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

	Query<T> andIn(Column column, Query<T> query);

	Query<T> andNotIn(Column column, Query<T> query);

	Query<T> andExists(Query<T> query);

	Query<T> andNotExists(Query<T> query);

	Query<T> groupBy(Column... columns);

	Query<T> orderBy(Sort sort);

	String getSelectSQL();

	String getInsertSQL(T data);

	String getUpdateSQL(T data);

	String getDeleteSQL(T data);

	String getDeleteAllSQL(Table table);

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
