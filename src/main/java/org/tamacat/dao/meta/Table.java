/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

import java.util.Collection;

public interface Table extends Cloneable {

	String getSchemaName();

	String getTableName();

	/**
	 * @since 1.4-20181122
	 */
	String getAliasName();
	 
	String getTableOrAliasName();

	String getTableNameWithSchema();

	Collection<Column> getPrimaryKeys();

	Collection<Column> getColumns();
	
	Column[] columns();

	Table registerColumn(Column... columns);

	boolean equalsTable(Object target);
	
	Column find(String columnName);
	
	Table clone();
}
