/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import org.tamacat.dao.meta.Column;
import org.tamacat.sql.SQLParser;

/**
 * Search condition for Database access.
 * (SQL: where, limit)
 */
public class Search {

	protected StringBuilder search = new StringBuilder();
	protected ValueConvertFilter valueConvertFilter;

	protected int start;
	protected int max;
	protected boolean unique;
	
	SQLParser parser;

	public Search() {
		parser = new SQLParser(new DefaultValueConvertFilter());
	}

	public Search(ValueConvertFilter valueConvertFilter) {
		parser = new SQLParser(valueConvertFilter);
	}

	public Search and(Column column, Condition condition, String... values) {
		if (search.length() > 0)
			search.append(" and ");
		search.append(parser.value(column, condition, values));
		return this;
	}

	public Search or(Column column, Condition condition, String... values) {
		if (search.length() > 0)
			search.append(" or ");
		search.append(parser.value(column, condition, values));
		return this;
	}

	public Search and(Search append) {
		if (search.length() > 0)
			search.append(" and ");
		search.append("(" + append.getSearchString() + ")");
		return this;
	}

	public Search or(Search append) {
		if (search.length() > 0)
			search.append(" or ");
		search.append("(" + append.getSearchString() + ")");
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
	
	public String getSearchString() {
		return search.toString();
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

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
	}
}
