/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

import java.io.Serializable;

import org.tamacat.dao.validation.Validator;

public class DefaultColumn implements Column, Serializable, Cloneable {

	private static final long serialVersionUID = 4721350387373477367L;
	
	protected String columnName;
	protected String defaultValue;
	protected String name;
	protected String functionName;
	
	protected DataType type = DataType.STRING;
	
	protected boolean isAutoGenerateId;
	protected boolean isAutoTimestamp;
	protected boolean isNotNull;
	protected boolean isPrimaryKey;
	protected boolean isFunction;
	
	protected int maxLength = -1;
	protected Validator validator;
	protected Table table;

	protected String format; //add v1.4
	protected String description; //add v1.4
	protected String option; //add v1.4
	
	public DefaultColumn() {
	}

	public DefaultColumn(String columnName) {
		this.columnName = columnName;
		this.name = columnName;
	}

	public DefaultColumn(Table table, String columnName, DataType type,
			String name, ColumnDefine... defines) {
		this.columnName = columnName;
		this.type = type;
		this.name = name;
		this.table = table;
		table.registerColumn(this);
		if (defines != null) {
			for (ColumnDefine def : defines) {
				if (PRIMARY_KEY.equals(def))
					this.isPrimaryKey = true;
				if (AUTO_GENERATE_ID.equals(def))
					this.isAutoGenerateId = true;
				if (AUTO_TIMESTAMP.equals(def))
					this.isAutoTimestamp = true;
				if (NOT_NULL.equals(def))
					this.isNotNull = true;
				if (FUNCTION.equals(def))
					this.isFunction = true;
			}
		}
	}

	@Override
	public String getColumnName() {
		return columnName;
	}

	@Override
	public Column columnName(String columnName) {
		return setColumnName(columnName);
	}
	
	public Column setColumnName(String columnName) {
		this.columnName = columnName;
		return this;
	}

	//@Deprecated
	//public Column column(String columnName) {
	//	this.columnName = columnName;
	//	return this;
	//}

	@Override
	public String getDefaultValue() {
		return defaultValue;
	}
	
	@Override
	public Column defaultValue(String defaultValue) {
		return setDefaultValue(defaultValue);
	}
	
	public Column setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	@Override
	public boolean isAutoGenerateId() {
		return isAutoGenerateId;
	}

	@Override
	public Column autoGenerateId(boolean isAutoGenerateId) {
		this.isAutoGenerateId = isAutoGenerateId;
		return this;
	}
	
	public Column setAutoGenerateId(boolean isAutoGenerateId) {
		return autoGenerateId(isAutoGenerateId);
	}
	
	@Override
	public boolean isAutoTimestamp() {
		return isAutoTimestamp;
	}

	@Override
	public Column autoTimestamp(boolean isAutoTimestamp) {
		this.isAutoTimestamp = isAutoTimestamp;
		return this;
	}
	
	public Column setAutoTimestamp(boolean isAutoTimestamp) {
		return autoTimestamp(isAutoTimestamp);
	}

	@Override
	public boolean isNotNull() {
		return isNotNull;
	}

	@Override
	public Column notNull(boolean isNotNull) {
		this.isNotNull = isNotNull;
		return this;
	}
	
	public Column setNotNull(boolean isNotNull) {
		return notNull(isNotNull);
	}

	@Override
	public boolean isPrimaryKey() {
		return isPrimaryKey;
	}
	
	@Override
	public Column primaryKey(boolean isPrimaryKey) {
		this.isPrimaryKey = isPrimaryKey;
		return this;
	}
	
	public Column setPrimaryKey(boolean isPrimaryKey) {
		return primaryKey(isPrimaryKey);
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public Column name(String name) {
		return setName(name);
	}
	
	public Column setName(String name) {
		this.name = name;
		return this;
	}

	@Override
	public boolean isFunction() {
		return isFunction;
	}
	
	@Override
	public String getFunctionName() {
		return functionName;
	}

	@Override
	public Column functionName(String functionName) {
		return setFunctionName(functionName);
	}
	
	public Column setFunctionName(String functionName) {
		this.functionName = functionName;
		this.isFunction = true;
		return this;
	}
	
	@Override
	public DataType getType() {
		return type;
	}

	public DefaultColumn setType(DataType type) {
		this.type = type;
		return this;
	}

	@Override
	public DefaultColumn type(DataType type) {
		this.type = type;
		return this;
	}

	public Column setTable(Table table) {
		this.table = table; //.clone();
		return this;
	}

	@Override
	public Table getTable() {
		return table;
	}
	
	@Override
	public Column maxLength(int maxLength) {
		this.maxLength = maxLength;
		return this;
	}
	
	@Override
	public int getMaxLength() {
		return maxLength;
	}

	@Override
	public Validator getValidator() {
		return validator;
	}
	
	@Override
	public Column set(Validator validator) {
		this.validator = validator;
		return this;
	}
	
	@Override
	public Column format(String format) {
		this.format = format;
		return this;
	}

	@Override
	public String getFormat() {
		return format;
	}
	
	@Override
	public String getOption() {
		return option;
	}
	
	public Column option(String option) {
		this.option = option;
		return this;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public Column description(String description) {
		this.description = description;
		return this;
	}
	
	@Override
	public Column clone() {
		try {
			DefaultColumn c = (DefaultColumn) super.clone();
			c.type = type;
			c.validator = validator;
			if (table != null) {
				c.table = table;//.clone();
			}
			return c;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((columnName == null) ? 0 : columnName.hashCode());
		result = prime * result + ((functionName == null) ? 0 : functionName.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((table == null) ? 0 : table.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DefaultColumn other = (DefaultColumn) obj;
		if (columnName == null) {
			if (other.columnName != null)
				return false;
		} else if (!columnName.equals(other.columnName))
			return false;
		if (functionName == null) {
			if (other.functionName != null)
				return false;
		} else if (!functionName.equals(other.functionName))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (table == null) {
			if (other.table != null)
				return false;
		} else if (!table.equals(other.table))
			return false;
		if (type != other.type)
			return false;
		return true;
	}
}
