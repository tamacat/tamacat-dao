/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.meta.Table;
import org.tamacat.dao.orm.MapBasedORMappingBean;
import org.tamacat.util.DateUtils;
import org.tamacat.util.StringUtils;

public class MappingUtils {

	static String DATE_FORMAT = "yyyy-MM-dd";
	static String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
	static String TIME_MS_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

	/**
	 * mapping ResultSet to Object from DataType
	 * 
	 * @param type
	 * @param rs
	 * @param index
	 * @return DATE->java.sql.Date, STRING/TIME->String, NUMERIC->Long,
	 *         FLOAT->Double
	 * @throws SQLException
	 */
	public static Object mapping(DataType type, ResultSet rs, int index) throws SQLException {
		switch (type) {
		case STRING:
		case BOOLEAN:
			return rs.getString(index);
		case DATE:
			return rs.getDate(index);
		case TIME:
			return rs.getString(index); // 2014-01-01 00:00:00.0 (MySQL)
		case NUMERIC:
			String val = rs.getString(index);
			if (StringUtils.isNotEmpty(val)) {
				return StringUtils.parse(val, 0L);
			} else {
				return val;
			}
			// return rs.getLong(index);
		case FLOAT:
			String fval = rs.getString(index);
			if (StringUtils.isNotEmpty(fval)) {
				return StringUtils.parse(fval, 0d);
			} else {
				return fval;
			}
			// return rs.getDouble(index);
		case OBJECT:
			return rs.getObject(index);
		case FUNCTION:
			return rs.getObject(index);
		default:
			break;
		}
		return null;
	}

	/**
	 * parse Object to Date.
	 * 
	 * @param value
	 * @return java.util.Date
	 */
	public static Date parseDate(Object value) {
		if (value instanceof Date) {
			return (Date) value;
		} else if (value instanceof Long) {
			return new Date((Long) value);
		} else if (value instanceof String) {
			String val = (String) value;
			if (val.indexOf(":") > 0) {
				if (val.indexOf(".") > 0) {
					return DateUtils.parse((String) value, TIME_MS_FORMAT);
				} else {
					return DateUtils.parse((String) value, TIME_FORMAT);
				}
			} else {
				return DateUtils.parse((String) value, DATE_FORMAT);
			}
		}
		return null;
	}

	@Deprecated
	public static String parse(Column column, Object value) {
		return parseString(column, value);
	}
	
	//rename parse -> parseString
	public static String parseString(Column column, Object value) {
		DataType type = column.getType();
		String format = column.getFormat();
		if (value == null) return null;
		if (type == DataType.DATE && value instanceof Date) {
			if (StringUtils.isNotEmpty(format)) {
				return DateUtils.getTime((Date) value, format);
			} else {
				return DateUtils.getTime((Date) value, DATE_FORMAT);
			}
		} else if (type == DataType.TIME && value instanceof Date) {
			if (StringUtils.isNotEmpty(format)) {
				return DateUtils.getTime((Date) value, format);
			} else {
				return DateUtils.getTime((Date) value, TIME_FORMAT);
			}
		} else if (value instanceof String) {
			return (String) value;
		} else {
			return value.toString();
		}
	}

	public static String getColumnName(Column col) {
		Table table = col.getTable();
		if (table == null) {
			return col.getColumnName();
			// throw new IllegalArgumentException(
			// "Column [" + col.getColumnName() + "] is not registered Table.");
		} else {
			return table.getTableNameWithSchema() + "." + col.getColumnName();
		}
	}

	/**
	 * @sinse 1.3
	 */
	public static Collection<String> values(Collection<? extends MapBasedORMappingBean<?>> beans, Column col) {
		ArrayList<String> list = new ArrayList<>();
		for (MapBasedORMappingBean<?> bean : beans) {
			list.add(bean.val(col));
		}
		return list;
	}
}
