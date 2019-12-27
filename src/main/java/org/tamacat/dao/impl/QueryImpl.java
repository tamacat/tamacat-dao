/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.tamacat.dao.Condition;
import org.tamacat.dao.Query;
import org.tamacat.dao.Search;
import org.tamacat.dao.Sort;
import org.tamacat.dao.Search.ValueConvertFilter;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.meta.Table;
import org.tamacat.dao.orm.ORMappingSupport;
import org.tamacat.dao.util.MappingUtils;
import org.tamacat.sql.SQLParser;
import org.tamacat.util.StringUtils;
import org.tamacat.util.UniqueCodeGenerator;

public class QueryImpl<T extends ORMappingSupport<T>> implements Query<T> {

	static final String SELECT = "SELECT";
	static final String FROM = "FROM";
	static final String WHERE = "WHERE";
	static final String GROUP_BY = "GROUP BY";
	static final String ORDER_BY = "ORDER BY";
	static final String INSERT = "INSERT INTO ${TABLE} (${COLUMNS}) VALUES (${VALUES})";
	static final String UPDATE = "UPDATE ${TABLE} SET ${VALUES}";
	static final String DELETE = "DELETE FROM ${TABLE}";
	protected ValueConvertFilter valueConvertFilter;

	protected Collection<Column> selectColumns = new LinkedHashSet<>();
	protected Collection<Column> updateColumns = new LinkedHashSet<>();
	protected Set<Table> tables = new LinkedHashSet<>();
	protected Set<Table> removeFromTables = new LinkedHashSet<>();
	protected Map<Table, String> outerJoinTables = new LinkedHashMap<>();
	protected Set<String> uniqTableNames = new HashSet<>();

	protected StringBuilder where = new StringBuilder();
	protected StringBuilder groupBy = new StringBuilder();
	protected StringBuilder orderBy = new StringBuilder();

	protected boolean useAutoPrimaryKeyUpdate = true;
	protected int blobIndex = 0;
	protected boolean distinct;

	public QueryImpl() {
		this.valueConvertFilter = new Search.DefaultValueConvertFilter();
	}

	public QueryImpl(ValueConvertFilter valueConvertFilter) {
		this.valueConvertFilter = valueConvertFilter;
	}

	@Override
	public Query<T> distinct(boolean distinct) {
		this.distinct = distinct;
		return this;
	}

	@Override
	public Query<T> addTable(Table table) {
		tables.add(table);
		return this;
	}

	@Override
	public Query<T> removeFromTables(Table... tables) {
		for (Table table : tables) {
			removeFromTables.add(table);
		}
		return this;
	}

	@Override
	public Query<T> select(Collection<Column> columns) {
		selectColumns.addAll(columns);
		return this;
	}

	@Override
	public Query<T> select(Column... columns) {
		for (Column column : columns) {
			selectColumns.add(column);
		}
		return this;
	}

	@Override
	public Collection<Column> getSelectColumns() {
		return selectColumns;
	}

	@Override
	public Query<T> addUpdateColumn(Column column) {
		updateColumns.add(column);
		return this;
	}

	@Override
	public Query<T> addUpdateColumns(Collection<Column> columns) {
		updateColumns.addAll(columns);
		return this;
	}

	@Override
	public Query<T> addUpdateColumns(Column... columns) {
		for (Column column : columns) {
			updateColumns.add(column);
		}
		return this;
	}

	public Query<T> removeUpdateColumns(Column... columns) {
		for (Column column : columns) {
			updateColumns.remove(column);
		}
		return this;
	}

	@Override
	public Collection<Column> getUpdateColumns() {
		return updateColumns;
	}

	@Override
	public String getSelectSQL() {
		blobIndex = 0;
		StringBuilder select = new StringBuilder();
		for (Column col : getSelectColumns()) {
			if (select.length() == 0) {
				select.append(SELECT + " ");
				if (distinct) {
					select.append("DISTINCT" + " ");
				}
			} else {
				select.append(",");
			}
			if (col.getType() == DataType.OBJECT) {
				blobIndex++;
			}
			if (col.isFunction()) {
				select.append(col.getFunctionName() + " " + col.getColumnName());
			} else {
				select.append(getColumnName(col));
			}
			tables.add(col.getTable());
		}
		StringBuilder from = new StringBuilder();
		for (Table tab : tables) {
			if (tab == null)
				continue;
			// skip outer join table
			if (removeFromTables.contains(tab))
				continue;

			if (from.length() == 0) {
				from.append(" " + FROM + " ");
			} else {
				from.append(",");
			}

			// outer join
			if (outerJoinTables.containsKey(tab)) {
				String table = outerJoinTables.get(tab);
				from.append(table);
			} else {
				from.append(getFromTableName(tab));
			}
			uniqTableNames.add(tab.getTableNameWithSchema());
		}
		return select.toString() + from.toString() + where.toString() + groupBy.toString() + orderBy.toString();
	}

	@Override
	public String getInsertSQL(T data) {
		SQLParser parser = new SQLParser(valueConvertFilter);
		StringBuilder columns = new StringBuilder();
		StringBuilder values = new StringBuilder();
		blobIndex = 0;
		String tableName = null;
		for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
			if (tableName == null)
				tableName = col.getTable().getTableName();
			if (columns.length() > 0) {
				columns.append(",");
				values.append(",");
			}
			columns.append(col.getColumnName());
			if (data.isUpdate(col)) {
				values.append(parser.parseValue(col, data.getValue(col)));
			} else {
				if (col.isAutoGenerateId()) {
					String id = UniqueCodeGenerator.generate();
					values.append(parser.parseValue(col, id));
					data.setValue(col, id);
				} else if (col.isAutoTimestamp()) {
					values.append(parser.parseValue(col, getTimestampString()));
				} else {
					values.append(parser.parseValue(col, data.getValue(col)));
				}
			}
			if (col.getType() == DataType.OBJECT) {
				blobIndex++;
			}
		}
		String query = INSERT.replace("${TABLE}", tableName).replace("${COLUMNS}", columns.toString())
				.replace("${VALUES}", values.toString());
		return query;
	}

	@Override
	/*
	 * @see autoPrimaryKeyUpdate(boolean useAutoPrimaryKeyUpdate)
	 */
	public Query<T> setUseAutoPrimaryKeyUpdate(boolean useAutoPrimaryKeyUpdate) {
		return autoPrimaryKeyUpdate(useAutoPrimaryKeyUpdate);
	}

	@Override
	public Query<T> autoPrimaryKeyUpdate(boolean useAutoPrimaryKeyUpdate) {
		this.useAutoPrimaryKeyUpdate = useAutoPrimaryKeyUpdate;
		return this;
	}

	@Override
	public String getUpdateSQL(T data) {
		SQLParser parser = new SQLParser(valueConvertFilter);
		StringBuilder values = new StringBuilder();
		String tableName = null;
		blobIndex = 0;
		for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
			if (tableName == null)
				tableName = col.getTable().getTableName();
			if (col.isPrimaryKey()) {
				if (useAutoPrimaryKeyUpdate) {
					addWhere("and", parser.value(col, Condition.EQUAL, data.getValue(col)));
				}
				continue;
			}
			if (col.isAutoGenerateId())
				continue;

			if (data.isUpdate(col)) {
				if (values.length() > 0) {
					values.append(",");
				}
				values.append(parser.value(col, Condition.EQUAL, data.getValue(col)).replaceFirst(tableName + ".", ""));
			} else if (col.isAutoTimestamp()) {
				if (values.length() > 0) {
					values.append(",");
				}
				values.append(parser.value(col, Condition.EQUAL, getTimestampString())
						.replaceFirst(tableName + ".", ""));
			} else if (col.getType() == DataType.OBJECT) {
				if (values.length() > 0) {
					values.append(",");
				}
				values.append(parser.value(col, Condition.EQUAL, "?"));
				blobIndex++;
			}
		}
		String query = UPDATE.replace("${TABLE}", tableName).replace("${VALUES}", values.toString());
		return query + where.toString();
	}

	@Override
	public String getDeleteSQL(T data) {
		SQLParser parser = new SQLParser(valueConvertFilter);
		String tableName = null;
		if (updateColumns != null) {
			for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
				if (tableName == null)
					tableName = col.getTable().getTableName();
				if (useAutoPrimaryKeyUpdate && col.isPrimaryKey()) {
					addWhere("and", parser.value(col, Condition.EQUAL, data.getValue(col)));
				}
			}
		} else {
			throw new InvalidParameterException("Set the UpdateColumns.");
		}
		if (tableName == null) {
			for (Table table : tables) {
				tableName = table.getTableName();
				break;
			}
			if (tableName == null) {
				throw new InvalidParameterException();
			}
		}
		String query = DELETE.replace("${TABLE}", tableName);
		return query + where.toString();
	}

	@Override
	public String getDeleteAllSQL(Table table) {
		String tableName = table.getTableName();
		String query = DELETE.replace("${TABLE}", tableName);
		return query + where.toString();
	}

	@Override
	public Query<T> join(Column col1, Column col2) {
		tables.add(col1.getTable());
		tables.add(col2.getTable());
		if (where.length() == 0) {
			where.append(" " + WHERE + " ");
		} else {
			where.append(" and ");
		}
		where.append(getColumnName(col1) + "=" + getColumnName(col2));
		return this;
	}

	@Override
	public Query<T> outerJoin(Column col1, Column col2) {
		if (outerJoinTables.containsKey(col2.getTable())) {
			outerJoinTables.put(col2.getTable(), outerJoinTables.get(col2.getTable()) + " and " + getColumnName(col1)
					+ "=" + getColumnName(col2));
		} else {
			outerJoinTables.put(col2.getTable(), getFromTableName(col1.getTable()) + " left join "
					+ getFromTableName(col2.getTable()) + " on " + getColumnName(col1) + "="
					+ getColumnName(col2));
			removeFromTables.add(col1.getTable());
			tables.add(col2.getTable());
		}

		uniqTableNames.add(col2.getTable().getTableNameWithSchema());
		uniqTableNames.add(col1.getTable().getTableNameWithSchema());
		return this;
	}
	
	String getFromTableName(Table table) {
		if (StringUtils.isNotEmpty(table.getAliasName())) {
			return table.getTableName() + " as " + table.getAliasName();
		} else {
			return table.getTableNameWithSchema();
		}
	}

	@Override
	public Query<T> andOuterJoin(Table tab1, Search search) {
		if (outerJoinTables.containsKey(tab1)) {
			outerJoinTables.put(tab1, outerJoinTables.get(tab1) + " and " + search.getSearchString());
		}
		return this;
	}

	@Override
	public Query<T> where(Search search, Sort sort) {
		return addSearch("and", search, sort);
	}

	@Override
	public Query<T> and(Search search, Sort sort) {
		return addSearch("and", search, sort);
	}

	@Override
	public Query<T> or(Search search, Sort sort) {
		return addSearch("or", search, sort);
	}

	@Override
	public Query<T> where(String sql) {
		return addWhere("and", sql);
	}

	@Override
	public Query<T> and(String sql) {
		return addWhere("and", sql);
	}

	@Override
	public Query<T> or(String sql) {
		return addWhere("or", sql);
	}

	@Override
	public Query<T> andIn(Column column, Query<T> query) {
		return addWhere("and", getColumnName(column) + " IN (" + query.getSelectSQL() + ")");
	}

	@Override
	public Query<T> andNotIn(Column column, Query<T> query) {
		return addWhere("and", getColumnName(column) + " NOT IN (" + query.getSelectSQL() + ")");
	}

	@Override
	public Query<T> andExists(Query<T> query) {
		return addWhere("and", "EXISTS (" + query.getSelectSQL() + ")");
	}

	@Override
	public Query<T> andNotExists(Query<T> query) {
		return addWhere("and", "NOT EXISTS (" + query.getSelectSQL() + ")");
	}

	@Override
	public Query<T> groupBy(Column... cols) {
		if (cols != null && cols.length > 0) {
			for (Column col : cols) {
				if (groupBy.length() == 0) {
					groupBy.append(" " + GROUP_BY + " ");
				} else {
					this.groupBy.append(",");
				}
				this.groupBy.append(getColumnName(col));
			}
		}
		return this;
	}

	@Override
	public Query<T> orderBy(Sort sort) {
		if (sort != null && sort.getSortString().length() > 0) {
			if (orderBy.length() == 0) {
				orderBy.append(" " + ORDER_BY + " ");
			} else {
				orderBy.append(",");
			}
			orderBy.append(sort.getSortString());
		}
		return this;
	}

	/**
	 * addWhere use then changes useAutoPrimaryKeyUpdate=false
	 * @param condition
	 * @param sql
	 * @return
	 */
	protected Query<T> addWhere(String condition, String sql) {
		if (sql != null && sql.trim().length() > 0) {
			if (where.length() == 0) {
				where.append(" " + WHERE + " ");
			} else {
				where.append(" " + condition + " ");
			}
			where.append(sql);
			useAutoPrimaryKeyUpdate = false;
		}
		return this;
	}

	protected Query<T> addSearch(String condition, Search search, Sort sort) {
		if (distinct == false) {
			distinct(search.isUnique());
		}
		addWhere(condition, search.getSearchString());
		return orderBy(sort);
	}

	@Override
	public String getTimestampString() {
		return "current_timestamp";
	}

	@Override
	public int getBlobIndex() {
		return blobIndex;
	}

	protected static String getColumnName(Column col) {
		return MappingUtils.getColumnName(col);
	}
}
