/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.orm;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.tamacat.dao.exception.InvalidValueLengthException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.util.JSONUtils;
import org.tamacat.dao.util.MappingUtils;
import org.tamacat.dao.validation.Validator;
import org.tamacat.util.StringUtils;

/**
 * Map based ORMaping bean. (extends LinkedHashMap)
 */
public class MapBasedORMappingBean<T extends MapBasedORMappingBean<T>>
		extends LinkedHashMap<String, Object> implements ORMappingSupport<T> {

	private static final long serialVersionUID = 1L;

	protected String defaultTableName;
	protected Set<String> updated = new LinkedHashSet<>();
	protected GetFilter getfilter;
	protected SetFilter setfilter;

	public String getValue(Column column) {
		return val(column);
	}

	@Override
	public String val(Column column) {
		String key = MappingUtils.getColumnName(column);
		return MappingUtils.parseString(column, get(key));
	}
	
	/**
	 * Get value of Date.
	 * @since 1.4
	 */
	public Date date(Column column) {
		return MappingUtils.parseDate(val(column));
	}
	
	/**
	 * Delete column
	 * @since 1.4
	 * @param column
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T del(Column column) {
		String key = MappingUtils.getColumnName(column);
		super.remove(key);
		return (T)this;
	}

	@Override
	public T setValue(Column column, String value) {
		return val(column, value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public T val(Column column, Object value) {
		put(MappingUtils.getColumnName(column), value);
		Validator validator = column.getValidator();
		if (validator != null) {
			validator.validate(column, value);
		}
		int maxLength = column.getMaxLength();
		if (maxLength > 0 && value != null && value instanceof String && ((String)value).length() > maxLength) {
			int len = ((String)value).length();
			throw new InvalidValueLengthException(
				"The ["+column.getName() +"] exceeds the maximum length of character."
				+" (length=" +len+", max="+ column.getMaxLength() + ")",
				column, (String)value);
		}
		return (T)this;
	}

	@Override
	/**
	 * JSP EL Map#get() using this method.
	 * 
	 * "column_name:date" -> returns java.util.Date
	 */
	public Object get(Object name) {
		if (name instanceof String) {
			String key = (String) name;
			int idx = key.indexOf(":date");
			if (idx >= 0) {
				return MappingUtils.parseDate(super.get(defaultTableName + "."
						+ key.substring(0, idx)));
			}
			if (getfilter != null) {
				return getfilter.get(key);
			} else if (defaultTableName != null && key.indexOf(".") == -1) {
				return originalGet(defaultTableName + "." + key);
			}
		}
		return originalGet(name);
	}

	public Object originalGet(Object name) {
		return super.get(name);
	}

	@Override
	public Object put(String name, Object value) {
		if (setfilter != null) {
			return setfilter.put(name, value);
		} else {
			return originalPut(name, value);
		}
	}

	public Object originalPut(String name, Object value) {
		updated.add(name);
		return super.put(name, value);
	}

	@SuppressWarnings("unchecked")
	public T mapping(Object name, Object value) {
		String val = value != null ? value.toString() : "";
		put(parse(name), val);
		return (T)this;
	}

	public boolean isUpdate(Object name) {
		if (name instanceof Column) {
			return updated.contains(MappingUtils.getColumnName((Column) name));
		} else {
			return updated.contains(name);
		}
	}

	public boolean isEmpty(Column column) {
		return StringUtils.isEmpty(val(column));
	}

	public boolean isNotEmpty(Column column) {
		return StringUtils.isNotEmpty(val(column));
	}

	public static String parse(Object data) {
		if (data instanceof Column) {
			return (MappingUtils.getColumnName((Column) data));
		} else {
			return data.toString();
		}
	}
	
	public JsonObjectBuilder toJson(Column... columns) {
		return JSONUtils.toJson(this, columns);
	}

	public void parseJson(Reader reader, Column... columns) {
		JSONUtils.parse(this, Json.createParser(reader), columns);
	}
	
	public boolean startsWith(String target, String prefix) {
		return target != null && target.startsWith(prefix);
	}
	
	public String getSize(String value) {
		long length = StringUtils.parse(value, 0L);
		if (length < 1024) {
			return length+" byte";
		} else if (length <1024*1024) {
			return BigDecimal.valueOf((double)length/1024).setScale(1, BigDecimal.ROUND_HALF_UP) +" KB";
		} else if (length <1024*1024*1024) {
			return BigDecimal.valueOf((double)length/1024/1024).setScale(1, BigDecimal.ROUND_HALF_UP) +" MB";
		} else {
			return BigDecimal.valueOf((double)length/1024/1024/1024).setScale(1, BigDecimal.ROUND_HALF_UP) +" GB";
		}
	}
	
	/**
	 * Set the KeyValueFilter.
	 * 
	 * @param filter
	 */
	public void setKeyValueFilter(KeyValueFilter filter) {
		if (filter instanceof GetFilter) {
			this.getfilter = (GetFilter) filter;
		}
		if (filter instanceof SetFilter) {
			this.setfilter = (SetFilter) filter;
		}
	}

	/**
	 * Marker interface for Get/Set Filter.
	 */
	public static interface KeyValueFilter {
	}

	/**
	 * Interceptor for Map#get method.
	 */
	public static interface GetFilter extends KeyValueFilter {
		Object get(String name);
	}

	/**
	 * Interceptor for Map#put method.
	 */
	public static interface SetFilter extends KeyValueFilter {
		Object put(String name, Object value);
	}
}
