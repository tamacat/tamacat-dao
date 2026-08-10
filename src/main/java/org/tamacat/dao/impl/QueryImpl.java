/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tamacat.dao.BindValue;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Param;
import org.tamacat.dao.PreparedSql;
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
import org.tamacat.sql.BindSqlBuilder;
import org.tamacat.sql.IdentifierRules;
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
	/**
	 * Bind-path counterpart of {@link #outerJoinTables} - the {@code ?}-bearing
	 * outer join expression for each table, kept in sync by {@code putOuterJoin(...)}.
	 * @since 2.0
	 */
	protected Map<Table, Param> bindOuterJoinTables = new LinkedHashMap<>();
	protected Set<String> uniqTableNames = new HashSet<>();

	protected StringBuilder where = new StringBuilder();
	/**
	 * Bind-path counterpart of {@link #where} - the ordered list of WHERE
	 * predicate fragments, kept in sync by {@code appendWhere(...)}.
	 * @since 2.0
	 */
	protected List<WhereFragment> bindFragments = new ArrayList<>();
	protected StringBuilder groupBy = new StringBuilder();
	protected StringBuilder orderBy = new StringBuilder();

	protected boolean useAutoPrimaryKeyUpdate = true;
	protected int blobIndex = 0;
	protected boolean distinct;

	/**
	 * A single WHERE predicate on the bind path: the connector ({@code "and"} /
	 * {@code "or"}, ignored for the first fragment) paired with the {@code ?}-bearing
	 * text and values. Not public - it is {@code QueryImpl}'s internal representation;
	 * only {@link PreparedSql} (returned by {@link #getSelectPreparedSql()}) is
	 * exposed (business-rules.md BR-3, domain-entities.md sec 2).
	 * @since 2.0
	 */
	private static final class WhereFragment {
		final String connector;
		final Param param;

		WhereFragment(String connector, Param param) {
			this.connector = connector;
			this.param = param;
		}
	}

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
		String select = buildSelectClause();
		String from = buildFromClause(false, null);
		return select + from + where.toString() + groupBy.toString() + orderBy.toString();
	}

	/**
	 * Builds the SELECT clause (column list, with DISTINCT if set). Also resets
	 * and recomputes {@link #blobIndex} - the count of {@code DataType.OBJECT}
	 * columns in the select list, which is this clause's existing meaning
	 * (business-logic-model.md sec 4.1). Shared by {@link #getSelectSQL()} and
	 * {@link #getSelectPreparedSql()} since it carries no values.
	 */
	private String buildSelectClause() {
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
				select.append(IdentifierRules.validate(col.getFunctionName()) + " " + col.getColumnName());
			} else {
				select.append(getColumnName(col));
			}
			tables.add(col.getTable());
		}
		return select.toString();
	}

	/**
	 * Builds the FROM clause, including outer join expressions. When {@code bind}
	 * is {@code true}, uses {@link #bindOuterJoinTables}'s {@code ?}-bearing text
	 * and appends its values to {@code collected} in the same iteration that
	 * builds the text - text order and value order cannot drift apart
	 * (business-rules.md BR-8). When {@code bind} is {@code false}, uses
	 * {@link #outerJoinTables}'s literal text and does not touch {@code collected}
	 * (which may be {@code null} in that case).
	 */
	private String buildFromClause(boolean bind, List<BindValue> collected) {
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
			if (bind) {
				if (bindOuterJoinTables.containsKey(tab)) {
					Param p = bindOuterJoinTables.get(tab);
					from.append(p.getSql());
					collected.addAll(p.getValues());
				} else {
					from.append(getFromTableName(tab));
				}
			} else {
				if (outerJoinTables.containsKey(tab)) {
					from.append(outerJoinTables.get(tab));
				} else {
					from.append(getFromTableName(tab));
				}
			}
			uniqTableNames.add(tab.getTableNameWithSchema());
		}
		return from.toString();
	}

	/**
	 * Bind-path equivalent of {@link #getSelectSQL()}. Value order is
	 * {@code FROM clause values ++ each WHERE fragment's values} (fragment order),
	 * matching the text output order {@code select + from + where + groupBy +
	 * orderBy} (business-rules.md BR-6).
	 * @since 2.0
	 */
	@Override
	public PreparedSql getSelectPreparedSql() {
		List<BindValue> values = new ArrayList<>();
		String select = buildSelectClause();
		String from = buildFromClause(true, values);
		StringBuilder w = new StringBuilder();
		for (int i = 0; i < bindFragments.size(); i++) {
			WhereFragment f = bindFragments.get(i);
			w.append(i == 0 ? " " + WHERE + " " : " " + f.connector + " ");
			w.append(f.param.getSql());
			values.addAll(f.param.getValues());
		}
		return PreparedSql.of(select + from + w.toString() + groupBy.toString() + orderBy.toString(), values);
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

	/**
	 * Bind-path equivalent of {@link #getInsertSQL(ORMappingSupport)}. Value-selection
	 * logic (isUpdate/isAutoGenerateId/isAutoTimestamp branches) is unchanged from the
	 * literal version - only the value's destination changes, from text embedding to a
	 * {@link BindValue} (business-rules.md BR-2). Text token and value are produced from
	 * the same {@link Param} in the same loop iteration, so they cannot drift apart
	 * (BR-3, WV-4). {@link #blobIndex} is set to the bind position (not count) after the
	 * {@link PreparedSql} is built, via {@link PreparedSql#getBindIndexOf} (BR-6).
	 * @since 2.0
	 */
	@Override
	public PreparedSql getInsertPreparedSql(T data) {
		BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();
		StringBuilder columns = new StringBuilder();
		StringBuilder values = new StringBuilder();
		List<BindValue> insertValues = new ArrayList<>();
		String tableName = null;
		for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
			if (tableName == null)
				tableName = col.getTable().getTableName();
			if (columns.length() > 0) {
				columns.append(",");
				values.append(",");
			}
			columns.append(col.getColumnName());
			String value;
			if (data.isUpdate(col)) {
				value = data.getValue(col);
			} else if (col.isAutoGenerateId()) {
				String id = UniqueCodeGenerator.generate();
				data.setValue(col, id);
				value = id;
			} else if (col.isAutoTimestamp()) {
				value = getTimestampString();
			} else {
				value = data.getValue(col);
			}
			Param p = bindSqlBuilder.placeholder(col, value);
			values.append(p.getSql());
			insertValues.addAll(p.getValues());
		}
		String sql = INSERT.replace("${TABLE}", tableName).replace("${COLUMNS}", columns.toString())
				.replace("${VALUES}", values.toString());
		PreparedSql prepared = PreparedSql.of(sql, insertValues);
		blobIndex = prepared.getBindIndexOf(DataType.OBJECT, 1);
		return prepared;
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
				values.append(parser.value(col, Condition.EQUAL, "?").replaceFirst(tableName + ".", ""));
				blobIndex++;
			}
		}
		String query = UPDATE.replace("${TABLE}", tableName).replace("${VALUES}", values.toString());
		return query + where.toString();
	}

	/**
	 * Renders the WHERE clause's bind-path text and collects its values from
	 * {@link #bindFragments}, in a single pass (business-rules.md BR-4a, BR-9). Shared
	 * by {@link #getUpdatePreparedSql(ORMappingSupport)}, {@link #getDeletePreparedSql(ORMappingSupport)}
	 * and {@link #getDeleteAllPreparedSql(Table)} - mirrors the WHERE-rendering loop in
	 * {@link #getSelectPreparedSql()} without modifying it (U2's reviewed artifact).
	 * @param collected the (initially empty) list that the fragment values are appended to
	 * @return the {@code ?}-bearing WHERE clause text (including the leading {@code " WHERE "})
	 * @since 2.0
	 */
	private String buildBindWhere(List<BindValue> collected) {
		StringBuilder w = new StringBuilder();
		for (int i = 0; i < bindFragments.size(); i++) {
			WhereFragment f = bindFragments.get(i);
			w.append(i == 0 ? " " + WHERE + " " : " " + f.connector + " ");
			w.append(f.param.getSql());
			collected.addAll(f.param.getValues());
		}
		return w.toString();
	}

	/**
	 * Bind-path equivalent of {@link #getUpdateSQL(ORMappingSupport)}. The SET clause is
	 * built directly with {@code col.getColumnName()} (unqualified column name), so no
	 * table-qualification is ever introduced - unlike the literal path, there is nothing
	 * to strip and so FR-6.2's defect class cannot occur here (BR-5). {@code setValues}
	 * (SET clause) and the WHERE fragment values are kept separate until the single
	 * combination point right before {@link PreparedSql#of}, in the same order as the
	 * text concatenation ({@code SET <values>} then the WHERE clause) - this is what
	 * prevents the "all values shift by one" failure mode (BR-7, WV-3).
	 * @since 2.0
	 */
	@Override
	public PreparedSql getUpdatePreparedSql(T data) {
		BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();
		StringBuilder setText = new StringBuilder();
		List<BindValue> setValues = new ArrayList<>();
		String tableName = null;
		for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
			if (tableName == null)
				tableName = col.getTable().getTableName();
			if (col.isPrimaryKey()) {
				if (useAutoPrimaryKeyUpdate) {
					Param p = bindSqlBuilder.value(col, Condition.EQUAL, data.getValue(col));
					addWhere("and", getColumnName(col) + Condition.EQUAL.getCondition(), p);
				}
				continue;
			}
			if (col.isAutoGenerateId())
				continue;
			String value;
			if (data.isUpdate(col)) {
				value = data.getValue(col);
			} else if (col.isAutoTimestamp()) {
				value = getTimestampString();
			} else if (col.getType() == DataType.OBJECT) {
				value = null;
			} else {
				continue;
			}
			if (setText.length() > 0) {
				setText.append(",");
			}
			Param p = bindSqlBuilder.placeholder(col, value);
			setText.append(col.getColumnName()).append("=").append(p.getSql());
			setValues.addAll(p.getValues());
		}
		List<BindValue> bindWhereValues = new ArrayList<>();
		String bindWhere = buildBindWhere(bindWhereValues);
		String sql = UPDATE.replace("${TABLE}", tableName).replace("${VALUES}", setText.toString()) + bindWhere;
		List<BindValue> combined = new ArrayList<>(setValues);
		combined.addAll(bindWhereValues);
		PreparedSql prepared = PreparedSql.of(sql, combined);
		blobIndex = prepared.getBindIndexOf(DataType.OBJECT, 1);
		return prepared;
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

	/**
	 * Bind-path equivalent of {@link #getDeleteSQL(ORMappingSupport)}. Exception
	 * throwing and table-name resolution are unchanged from the literal version
	 * (BR-13). DELETE has no SET clause, so its values come solely from
	 * {@link #buildBindWhere(List)} (BR-8); {@link #blobIndex} is not updated here
	 * (primary keys are not OBJECT-typed - business-logic-model.md sec 4).
	 * @since 2.0
	 */
	@Override
	public PreparedSql getDeletePreparedSql(T data) {
		BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();
		String tableName = null;
		if (updateColumns != null) {
			for (Column col : updateColumns.toArray(new Column[updateColumns.size()])) {
				if (tableName == null)
					tableName = col.getTable().getTableName();
				if (useAutoPrimaryKeyUpdate && col.isPrimaryKey()) {
					Param p = bindSqlBuilder.value(col, Condition.EQUAL, data.getValue(col));
					addWhere("and", getColumnName(col) + Condition.EQUAL.getCondition(), p);
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
		List<BindValue> values = new ArrayList<>();
		String sql = DELETE.replace("${TABLE}", tableName) + buildBindWhere(values);
		return PreparedSql.of(sql, values);
	}

	@Override
	public String getDeleteAllSQL(Table table) {
		String tableName = table.getTableName();
		String query = DELETE.replace("${TABLE}", tableName);
		return query + where.toString();
	}

	/**
	 * Bind-path equivalent of {@link #getDeleteAllSQL(Table)}.
	 * @since 2.0
	 */
	@Override
	public PreparedSql getDeleteAllPreparedSql(Table table) {
		List<BindValue> values = new ArrayList<>();
		String sql = DELETE.replace("${TABLE}", table.getTableName()) + buildBindWhere(values);
		return PreparedSql.of(sql, values);
	}

	@Override
	public Query<T> join(Column col1, Column col2) {
		tables.add(col1.getTable());
		tables.add(col2.getTable());
		String sql = getColumnName(col1) + "=" + getColumnName(col2);
		// No values (column-to-column comparison). useAutoPrimaryKeyUpdate is
		// deliberately NOT set here - this asymmetry with addWhere(...) is the
		// existing behavior (business-rules.md BR-4).
		appendWhere("and", sql, Param.of(sql, Collections.<BindValue>emptyList()));
		return this;
	}

	/**
	 * The single entry point that keeps {@link #outerJoinTables} and
	 * {@link #bindOuterJoinTables} synchronized (business-rules.md BR-5).
	 */
	private void putOuterJoin(Table key, String literalExpr, Param bindExpr) {
		outerJoinTables.put(key, literalExpr);
		bindOuterJoinTables.put(key, bindExpr);
	}

	@Override
	public Query<T> outerJoin(Column col1, Column col2) {
		Table key = col2.getTable();
		if (outerJoinTables.containsKey(key)) {
			String expr = outerJoinTables.get(key) + " and " + getColumnName(col1) + "=" + getColumnName(col2);
			putOuterJoin(key, expr, Param.of(expr, Collections.<BindValue>emptyList()));
		} else {
			String expr = getFromTableName(col1.getTable()) + " left join "
					+ getFromTableName(col2.getTable()) + " on " + getColumnName(col1) + "="
					+ getColumnName(col2);
			putOuterJoin(key, expr, Param.of(expr, Collections.<BindValue>emptyList()));
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

	/**
	 * @deprecated use {@link #andOuterJoin(Table, Param)}. Return value unchanged
	 *             - the bind side is stored with the literal text and zero values
	 *             (business-rules.md BR-12; this path is outside SM-1).
	 */
	@Deprecated
	@Override
	public Query<T> andOuterJoin(Table tab1, Search search) {
		if (outerJoinTables.containsKey(tab1)) {
			String literal = outerJoinTables.get(tab1) + " and " + search.getSearchString();
			Param prev = bindOuterJoinTables.get(tab1);
			putOuterJoin(tab1, literal,
					Param.of(prev.getSql() + " and " + search.getSearchString(), prev.getValues()));
		}
		return this;
	}

	/**
	 * Bind-path equivalent of {@link #andOuterJoin(Table, Search)}.
	 * @since 2.0
	 */
	@Override
	public Query<T> andOuterJoin(Table tab1, Param param) {
		if (outerJoinTables.containsKey(tab1)) {
			Param prev = bindOuterJoinTables.get(tab1);
			List<BindValue> merged = new ArrayList<>(prev.getValues());
			merged.addAll(param.getValues());
			putOuterJoin(tab1,
					outerJoinTables.get(tab1) + " and " + param.getSql(),
					Param.of(prev.getSql() + " and " + param.getSql(), merged));
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

	/**
	 * Bind-path equivalent of {@link #where(String)}. Overrides the {@code Query}
	 * default (which would discard {@code param}'s values) so the values are
	 * routed through {@link #addWhere(String, Param)} instead
	 * (business-logic-model.md sec 6.4).
	 * @since 2.0
	 */
	@Override
	public Query<T> where(Param param) {
		return addWhere("and", param);
	}

	/**
	 * Bind-path equivalent of {@link #and(String)}. See {@link #where(Param)}.
	 * @since 2.0
	 */
	@Override
	public Query<T> and(Param param) {
		return addWhere("and", param);
	}

	/**
	 * Bind-path equivalent of {@link #or(String)}. See {@link #where(Param)}.
	 * @since 2.0
	 */
	@Override
	public Query<T> or(Param param) {
		return addWhere("or", param);
	}

	@Override
	public Query<T> andIn(Column column, Query<T> query) {
		PreparedSql child = query.getSelectPreparedSql();
		String literal = getColumnName(column) + " IN (" + query.getSelectSQL() + ")";
		Param bind = Param.of(getColumnName(column) + " IN (" + child.getSql() + ")", child.getValues());
		return addWhere("and", literal, bind);
	}

	@Override
	public Query<T> andNotIn(Column column, Query<T> query) {
		PreparedSql child = query.getSelectPreparedSql();
		String literal = getColumnName(column) + " NOT IN (" + query.getSelectSQL() + ")";
		Param bind = Param.of(getColumnName(column) + " NOT IN (" + child.getSql() + ")", child.getValues());
		return addWhere("and", literal, bind);
	}

	@Override
	public Query<T> andExists(Query<T> query) {
		PreparedSql child = query.getSelectPreparedSql();
		String literal = "EXISTS (" + query.getSelectSQL() + ")";
		Param bind = Param.of("EXISTS (" + child.getSql() + ")", child.getValues());
		return addWhere("and", literal, bind);
	}

	@Override
	public Query<T> andNotExists(Query<T> query) {
		PreparedSql child = query.getSelectPreparedSql();
		String literal = "NOT EXISTS (" + query.getSelectSQL() + ")";
		Param bind = Param.of("NOT EXISTS (" + child.getSql() + ")", child.getValues());
		return addWhere("and", literal, bind);
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
	 * The single entry point that keeps {@link #where} and {@link #bindFragments}
	 * synchronized. Prefixes {@code where} with {@code " WHERE "} for the first
	 * fragment; {@code bindFragments} carries no such prefix - it is rendered by
	 * {@link #getSelectPreparedSql()} (business-rules.md BR-3).
	 */
	private void appendWhere(String connector, String literalSql, Param bindParam) {
		if (where.length() == 0) {
			where.append(" ").append(WHERE).append(" ");
		} else {
			where.append(" ").append(connector).append(" ");
		}
		where.append(literalSql);
		bindFragments.add(new WhereFragment(connector, bindParam));
	}

	/**
	 * addWhere use then changes useAutoPrimaryKeyUpdate=false
	 * @param condition
	 * @param sql
	 * @return
	 */
	protected Query<T> addWhere(String condition, String sql) {
		if (sql != null && sql.trim().length() > 0) {
			appendWhere(condition, sql, Param.of(sql, Collections.<BindValue>emptyList()));
			useAutoPrimaryKeyUpdate = false;
		}
		return this;
	}

	/**
	 * Bind-path equivalent of {@link #addWhere(String, String)}. A thin wrapper
	 * over the internal 3-argument form - the literal side uses {@code param}'s
	 * own text, since there is no separate literal text to keep in sync
	 * (business-logic-model.md sec 3.3).
	 * @since 2.0
	 */
	protected Query<T> addWhere(String condition, Param param) {
		return addWhere(condition, param == null ? null : param.getSql(), param);
	}

	/**
	 * Internal 3-argument form used where the literal text and the bind text
	 * differ (e.g. {@link #addSearch(String, Search, Sort)}, where the literal
	 * side comes from {@link Search#getSearchString()} and the bind side from
	 * {@link Search#getSearchParam()}).
	 */
	private Query<T> addWhere(String condition, String literalSql, Param bindParam) {
		if (bindParam != null && literalSql != null && literalSql.trim().length() > 0) {
			appendWhere(condition, literalSql, bindParam);
			useAutoPrimaryKeyUpdate = false;
		}
		return this;
	}

	protected Query<T> addSearch(String condition, Search search, Sort sort) {
		if (distinct == false) {
			distinct(search.isUnique());
		}
		addWhere(condition, search.getSearchString(), search.getSearchParam());
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
