/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

import org.tamacat.dao.validation.Validator;

public interface Column extends Cloneable {

	ColumnDefine PRIMARY_KEY = ColumnDefine.PRIMARY_KEY;
	ColumnDefine FOREIGN_KEY = ColumnDefine.FOREIGN_KEY;
	ColumnDefine NOT_NULL = ColumnDefine.NOT_NULL;
	ColumnDefine AUTO_GENERATE_ID = ColumnDefine.AUTO_GENERATE_ID;
	ColumnDefine AUTO_TIMESTAMP = ColumnDefine.AUTO_TIMESTAMP;
	ColumnDefine FUNCTION = ColumnDefine.FUNCTION;

	String getName();

	/**
	 * @since 1.4
	 */
	Column name(String name);
	
	String getColumnName();
	
	/**
	 * @since 1.4
	 */
	Column columnName(String columnName);

	String getFunctionName();

	Column functionName(String functionName);

	DataType getType();

	Column type(DataType type);

	boolean isPrimaryKey();

	Column primaryKey(boolean primaryKey);
	
	boolean isNotNull();

	Column notNull(boolean notNull);
	
	boolean isAutoTimestamp();

	Column autoTimestamp(boolean autoTimestamp);
	
	boolean isAutoGenerateId();

	Column autoGenerateId(boolean autoGenerateId);
	
	boolean isFunction();

	int getMaxLength();

	Column maxLength(int maxLength);
	
	String getDefaultValue();
	
	/**
	 * @since 1.4
	 */
	Column defaultValue(String defaultValue);

	Column setTable(Table table);

	Table getTable();

	Validator getValidator();

	Column set(Validator validator);

	Column NULL = new NullColumn();

	/**
	 * @since 1.4
	 */
	Column format(String format);
	
	/**
	 * @since 1.4
	 */
	String getFormat();
	
	/**
	 * @since 1.4
	 */
	String getDescription();
	
	/**
	 * @since 1.4
	 */
	Column description(String description);
	
	/**
	 * @since 1.4
	 */
	String getOption();
	
	/**
	 * @since 1.4
	 */
	Column option(String option);
	
	Column clone();
	
	class NullColumn implements Column {
		Validator validator;
		DataType type;
		boolean primaryKey;
		boolean notNull;
		int maxLength;
		String functionName;
		boolean function;
		boolean autoTimestamp;
		boolean autoGenerateId;
		String format;
		String description;
		String option;
		
		@Override
		public String getName() {
			return "NULL";
		}
		
		@Override
		public Column name(String name) {
			return this;
		}

		@Override
		public String getColumnName() {
			return "NULL";
		}

		@Override
		public Column columnName(String columnName) {
			return this;
		}
		
		@Override
		public String getFunctionName() {
			return functionName;
		}

		@Override 
		public Column functionName(String functionName) {
			this.functionName = functionName;
			return this;
		}
		
		@Override
		public DataType getType() {
			return type;
		}

		@Override
		public Column type(DataType type) {
			this.type = type;
			return this;
		}

		@Override
		public boolean isPrimaryKey() {
			return false;
		}

		@Override
		public boolean isNotNull() {
			return false;
		}
		
		@Override
		public boolean isAutoTimestamp() {
			return autoTimestamp;
		}

		@Override
		public boolean isAutoGenerateId() {
			return autoGenerateId;
		}

		@Override
		public boolean isFunction() {
			return false;
		}

		@Override
		public String getDefaultValue() {
			return null;
		}
		
		@Override
		public Column defaultValue(String defaultValue) {
			return this;
		}

		@Override
		public Column setTable(Table table) {
			return null;
		}

		@Override
		public Table getTable() {
			return null;
		}

		@Override
		public int getMaxLength() {
			return maxLength;
		}

		@Override
		public Column set(Validator validator) {
			this.validator = validator;
			return this;
		}

		@Override
		public Validator getValidator() {
			return validator;
		}

		@Override
		public Column primaryKey(boolean primaryKey) {
			this.primaryKey = primaryKey;
			return this;
		}

		@Override
		public Column autoTimestamp(boolean autoTimestamp) {
			this.autoTimestamp = autoTimestamp;
			return this;
		}
		
		@Override
		public Column autoGenerateId(boolean autoGenerateId) {
			this.autoGenerateId = autoGenerateId;
			return this;
		}
		
		@Override
		public Column notNull(boolean notNull) {
			this.notNull = notNull;
			return this;
		}
		
		@Override
		public Column maxLength(int maxLength) {
			this.maxLength = maxLength;
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
		public String getDescription() {
			return description;
		}

		@Override
		public Column description(String description) {
			this.description = description;
			return this;
		}

		@Override
		public String getOption() {
			return option;
		}

		@Override
		public Column option(String option) {
			this.option = option;
			return this;
		}
		
		@Override
		public Column clone() {
			try {
				Column c = (Column) super.clone();
				c.type(type);
				return c;
			} catch (CloneNotSupportedException e) {
				return null;
			}
		}
	}
}
