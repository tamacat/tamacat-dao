/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;

public class DefaultTable implements Table, Serializable, Cloneable {

	private static final long serialVersionUID = -2461380378097186495L;

	private LinkedHashSet<Column> columns = new LinkedHashSet<>();
	private HashSet<Column> primaryKeys = new HashSet<>();

	private String schemaName;
	private String tableName;
	private String aliasName;

	public DefaultTable(String... name) {
		switch (name.length) {
		case 3:
			schemaName = name[0];
			tableName = name[1];
			aliasName = name[2];
			break;
		case 2:
			tableName = name[0];
			aliasName = name[1];
			break;
		case 1:
			tableName = name[0];
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<Column> getColumns() {
		return (Collection<Column>) columns.clone();
	}

	@Override
	public Column[] columns() {
		return columns.toArray(new Column[columns.size()]);
	}

	@Override
	public Collection<Column> getPrimaryKeys() {
		return primaryKeys;
	}

	@Override
	public String getTableName() {
		return tableName;
	}

	@Override
	/**
	 * @since 1.4-20181122
	 */
	public String getAliasName() {
		return aliasName;
	}
	
	@Override
	public String getTableOrAliasName() {
		if (aliasName != null)
			return aliasName;
		else
			return tableName;
	}

	@Override
	public String getSchemaName() {
		if (schemaName == null)
			return "";
		else
			return schemaName;
	}

	@Override
	public String getTableNameWithSchema() {
		if (schemaName != null) {
			return schemaName + "." + getTableOrAliasName();
		} else {
			return getTableOrAliasName();
		}
	}

	public DefaultTable setTableName(String tableName) {
		this.tableName = tableName;
		return this;
	}

	public DefaultTable setSchemaName(String schemaName) {
		this.schemaName = schemaName;
		return this;
	}

	@Override
	public Table registerColumn(Column... cols) {
		for (Column column : cols) {
			try {
				column.setTable(this);
				Column c = (Column) column.clone();
				if (c.isPrimaryKey()) {
					this.primaryKeys.add(c);
				}
				this.columns.add(c);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return this;
	}

	public boolean equalsTable(Object target) {
		if (target == null)
			return false;
		if (target instanceof Column) {
			return equals(((Column) target).getTable());
		} else if (target instanceof Table) {
			return equals(((Table) target));
		} else if (target instanceof String) {
			return ((String) target).startsWith(tableName + ".");
		} else {
			return equals(target);
		}
	}

	@Override
	public Column find(String columnName) {
		for (Column col : columns) {
			if (col.getColumnName().equalsIgnoreCase(columnName)) {
				return col;
			}
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Table clone() {
		try {
			DefaultTable t = (DefaultTable) super.clone();
			t.columns = (LinkedHashSet<Column>) columns.clone();
			t.primaryKeys = (HashSet<Column>) primaryKeys.clone();
			return t;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((aliasName == null) ? 0 : aliasName.hashCode());
		result = prime * result + ((schemaName == null) ? 0 : schemaName.hashCode());
		result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
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
		DefaultTable other = (DefaultTable) obj;
		if (aliasName == null) {
			if (other.aliasName != null)
				return false;
		} else if (!aliasName.equals(other.aliasName))
			return false;
		if (schemaName == null) {
			if (other.schemaName != null)
				return false;
		} else if (!schemaName.equals(other.schemaName))
			return false;
		if (tableName == null) {
			if (other.tableName != null)
				return false;
		} else if (!tableName.equals(other.tableName))
			return false;
		return true;
	}
}
