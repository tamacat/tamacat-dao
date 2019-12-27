/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tamacat.dao.Condition;
import org.tamacat.dao.Search;
import org.tamacat.dao.Search.ValueConvertFilter;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.util.MappingUtils;
import org.tamacat.util.StringUtils;

public class SQLParser {

	public static final String VALUE1 = "#{value1}";
	public static final String VALUE2 = "#{value2}";
	public static final String MULTI_VALUE = "#{values}";
	public static final String[] VALUES = { VALUE1, VALUE2 };
	public static final String NULL_VALUE = "NULL";

	protected static final String ESCAPE = " escape '?'";

	protected ValueConvertFilter valueConvertFilter;

	public SQLParser() {
		this.valueConvertFilter = new Search.DefaultValueConvertFilter();
	}

	public SQLParser(ValueConvertFilter valueConvertFilter) {
		this.valueConvertFilter = valueConvertFilter;
	}

	public String value(Column column, Condition condition, String... values) {
		String colName = MappingUtils.getColumnName(column);
		StringBuffer search = new StringBuffer(colName + condition.getCondition());
		if (values != null) {
			if (values.length == 1) {
				String value = values[0];
				if (StringUtils.isEmpty(value) && column.isNotNull()) {
					throw new InvalidParameterException("Column [" + colName + "] is required.");
				}
				if ((column.getType() == DataType.STRING || column.getType() == DataType.BOOLEAN) // for LIKE 'String
						&& condition.getCondition().indexOf(" like ") >= 0) { // Data'
					search.append(parseLikeStringValue(condition, column, value));
				} else if (condition.getCondition().equals(" in ")) { // IN
					search.append(parseMultiValue(column, condition.getReplaceHolder(), value));
				} else {
					if (value == null) {
						search.append(parseValue(column, condition.getReplaceHolder().replace(VALUE1, "")));
					} else {
						search.append(parseValue(column, condition.getReplaceHolder().replace(VALUE1, value)));
					}
				}
			} else if (values.length >= 2) {
				String v = condition.getReplaceHolder(); // for BETWEEN
				if (condition.getCondition().indexOf(" between ") >= 0) {
					for (int i = 0; i < values.length; i++) {
						v = v.replace(VALUES[i], parseValue(column, values[i]));
					}
				} else { // for IN
					v = parseMultiValue(column, v, values);
				}
				search.append(v);
			}
		}
		return search.toString();
	}

	String parseMultiValue(Column column, String v, String... values) {
		StringBuffer parsed = new StringBuffer();
		for (int i = 0; i < values.length; i++) {
			if (parsed.length() > 0)
				parsed.append(",");
			parsed.append(parseValue(column, values[i]));
		}
		return v.replace(MULTI_VALUE, parsed.toString());
	}

	public String parseValue(Column column, String value) {
		String parseValue = (valueConvertFilter == null) ? value : valueConvertFilter.convertValue(value);
		if (column.getType() == DataType.STRING || column.getType() == DataType.BOOLEAN) {
			if (value == null) {
				return parseValue;
			} else {
				return "'" + parseValue + "'";
			}
		} else if (column.getType() == DataType.NUMERIC || column.getType() == DataType.FLOAT) {
			if (StringUtils.isEmpty(value)) {
				return NULL_VALUE.toLowerCase();
			} else {
				if (isNumeric(value)) {
					return parseValue;
				} else {
					//return NULL_VALUE.toLowerCase();
					throw new InvalidParameterException("value is not numeric.");
				}
			}
		} else if (column.getType() == DataType.TIME || column.getType() == DataType.DATE) {
			if (StringUtils.isEmpty(value) || value.equalsIgnoreCase(NULL_VALUE)) {
				return NULL_VALUE.toLowerCase();
			} else if (value.equalsIgnoreCase("current_timestamp")) { //TODO
				return parseValue;
			} else {
				return "'" + parseValue + "'";
			}
		} else if (column.getType() == DataType.OBJECT) {
			return "?";
		} else {
			return parseValue;
		}
	}
	
	protected String parseLikeStringValue(Condition condition, Column column, String value) {
		if (value == null) {
			//like null -> like ''
			value = "";
		}
		if (value.indexOf('%') >= 0 || value.indexOf('_') >= 0) {
			char[] esc = new char[] { '$', '#', '~', '!', '^' };
			for (char e : esc) {
				if (value.indexOf(e) == -1) {
					String val = value.replace("%", e + "%").replace("_", e + "_");
					val = condition.getReplaceHolder().replace(VALUE1, val);
					String parseValue = (valueConvertFilter == null) ? val : valueConvertFilter.convertValue(val);
					if (column.getType() == DataType.STRING || column.getType() == DataType.BOOLEAN) {
						parseValue = "'" + parseValue + "'";
					}
					return parseValue + ESCAPE.replace('?', e);
				}
			}
		}
		return parseValue(column, condition.getReplaceHolder().replace(VALUE1, value));
	}
	
	/**
	 * @since 1.4-20180217
	 * @param value
	 */
	protected boolean isNumeric(String value) {
		if (StringUtils.isEmpty(value)) return false;
	    Pattern p = Pattern.compile("^\\-?[0-9]*\\.?[0-9]+$");
	    Matcher m = p.matcher(value);
	    return m.find();
	}
}
