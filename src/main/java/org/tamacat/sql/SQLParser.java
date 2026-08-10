/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import org.tamacat.dao.Search;
import org.tamacat.dao.Search.Conditions;
import org.tamacat.dao.Search.ValueConvertFilter;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.util.MappingUtils;

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

	public String value(Column column, Conditions condition, String... values) {
		String colName = MappingUtils.getColumnName(column);
		StringBuffer search = new StringBuffer(colName + condition.getCondition());
		if (values != null) {
			if (values.length == 1) {
				String value = values[0];
				if (ValueRules.isRequiredButEmpty(column, value)) {
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
			if (ValueRules.isNullValue(column, value)) {
				return NULL_VALUE.toLowerCase();
			} else {
				ValueRules.validate(column, value);
				return parseValue;
			}
		} else if (column.getType() == DataType.TIME || column.getType() == DataType.DATE) {
			if (ValueRules.isNullValue(column, value)) {
				return NULL_VALUE.toLowerCase();
			} else if (ValueRules.isSqlFunction(column, value)) { //TODO
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

	protected String parseLikeStringValue(Conditions condition, Column column, String value) {
		LikeEscape le = ValueRules.escapeLike(condition, column, value);
		if (!le.hasEscape()) {
			return parseValue(column, le.getBoundValue());
		}
		String parsed = (valueConvertFilter == null)
				? le.getBoundValue() : valueConvertFilter.convertValue(le.getBoundValue());
		if (column.getType() == DataType.STRING || column.getType() == DataType.BOOLEAN) {
			parsed = "'" + parsed + "'";
		}
		return parsed + ESCAPE.replace('?', le.getEscapeChar());
	}

	/**
	 * @since 1.4-20180217
	 * @param value
	 */
	protected boolean isNumeric(String value) {
		return ValueRules.isNumeric(value);
	}
}
