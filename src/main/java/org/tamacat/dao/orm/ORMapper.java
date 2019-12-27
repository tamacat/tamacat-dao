/*
 * Copyright (c) 2008-2012 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.orm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;

import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.util.MappingUtils;
import org.tamacat.util.ClassUtils;

public class ORMapper<T extends ORMappingSupport<T>> {

	private Class<T> type;

	public ORMapper() {
	}

	public void setPrototype(Class<T> type) {
		this.type = type;
	}

	public T getMappedObject() {
		return ClassUtils.newInstance(type);
	}

	public T mapping(Collection<Column> columns, ResultSet rs) {
		T data = getMappedObject();
		try {
			int index = 1;
			for (Column column : columns) {
				DataType type = column.getType();
				data.mapping(column, MappingUtils.mapping(type, rs, index));
				index++;
			}
		} catch (SQLException e) {
			throw new DaoException(e);
		}
		return data;
	}
}
