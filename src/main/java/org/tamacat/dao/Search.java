/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.util.ArrayList;
import java.util.List;

import org.tamacat.dao.meta.Column;
import org.tamacat.sql.BindSqlBuilder;
import org.tamacat.sql.SQLParser;

/**
 * Search condition for Database access.
 * (SQL: where, limit)
 */
public class Search {

	public interface Conditions {

		String getReplaceHolder();

		String getCondition();
	}

	protected StringBuilder search = new StringBuilder();
	/**
	 * Bind-path counterpart of {@link #search} - the {@code ?}-bearing predicate
	 * text, kept in sync with {@link #search} and {@link #bindValues} by the
	 * private {@code append(...)} method.
	 * @since 2.0
	 */
	protected StringBuilder bindSearch = new StringBuilder();
	/**
	 * The ordered bind values corresponding to {@link #bindSearch}'s {@code ?}
	 * placeholders.
	 * @since 2.0
	 */
	protected List<BindValue> bindValues = new ArrayList<>();
	protected ValueConvertFilter valueConvertFilter;

	protected int start;
	protected int max;
	protected boolean unique;

	SQLParser parser;
	BindSqlBuilder builder;

	public Search() {
		parser = new SQLParser(new DefaultValueConvertFilter());
		builder = new BindSqlBuilder();
	}

	public Search(ValueConvertFilter valueConvertFilter) {
		parser = new SQLParser(valueConvertFilter);
		builder = new BindSqlBuilder();
	}

	/**
	 * The single entry point that keeps {@link #search}, {@link #bindSearch}, and
	 * {@link #bindValues} synchronized. {@code literalSql} and {@code bindParam}
	 * must be evaluated by the caller as arguments to this method so that, if
	 * either throws, none of the three states are changed (business-rules.md
	 * BR-1).
	 */
	private void append(String connector, String literalSql, Param bindParam) {
		if (search.length() > 0) {
			search.append(" ").append(connector).append(" ");
			bindSearch.append(" ").append(connector).append(" ");
		}
		search.append(literalSql);
		bindSearch.append(bindParam.getSql());
		bindValues.addAll(bindParam.getValues());
	}

	public Search and(Column column, Conditions condition, String... values) {
		append("and", parser.value(column, condition, values),
				builder.value(column, condition, values));
		return this;
	}

	public Search or(Column column, Conditions condition, String... values) {
		append("or", parser.value(column, condition, values),
				builder.value(column, condition, values));
		return this;
	}

	public Search and(Search append) {
		Param p = append.getSearchParam();
		append("and", "(" + append.getSearchString() + ")",
				Param.of("(" + p.getSql() + ")", p.getValues()));
		return this;
	}

	public Search or(Search append) {
		Param p = append.getSearchParam();
		append("or", "(" + append.getSearchString() + ")",
				Param.of("(" + p.getSql() + ")", p.getValues()));
		return this;
	}

	/**
	 * Set the using SELECT DISTINCT.
	 * Already set a Query#distinct(true), can not override Search#distinct(false).
	 * @since 1.4
	 * @param unique
	 */
	public Search unique(boolean unique) {
		this.unique = unique;
		return this;
	}

	/**
	 * Using SELECT DISTINCT
	 * @since 1.4
	 * @return unique
	 */
	public boolean isUnique() {
		return unique;
	}
	
	/**
	 * @deprecated use {@link #getSearchParam()}. Return value unchanged.
	 */
	@Deprecated
	public String getSearchString() {
		return search.toString();
	}

	/**
	 * Bind-path equivalent of {@link #getSearchString()} - the {@code ?}-bearing
	 * predicate text paired with its ordered bind values.
	 *
	 * <p>{@code public} rather than package-private (unlike {@link #parser} /
	 * {@link #builder}) because {@link org.tamacat.dao.impl.QueryImpl}, which
	 * calls this, is in a different package.
	 * @since 2.0
	 */
	public Param getSearchParam() {
		return Param.of(bindSearch.toString(), bindValues);
	}

	/**
	 * Value Convert Filter Interface
	 */
	public static interface ValueConvertFilter {
		String convertValue(String value);
	}

	public static class DefaultValueConvertFilter implements ValueConvertFilter {
		public String convertValue(String value) {
			if (value != null) {
				return value.replace("'", "''");
			} else {
				return value;
			}
		}
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}
	
	public Search start(int start) {
		this.start = start;
		return this;
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
	}
	
	public Search max(int max) {
		this.max = max;
		return this;
	}
}
