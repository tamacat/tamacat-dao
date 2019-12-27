/*
 * Copyright (c) 2008 TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.test;

import org.tamacat.dao.meta.Table;
import org.tamacat.dao.meta.Tables;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.Columns;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.orm.MapBasedORMappingBean;

public class User extends MapBasedORMappingBean<User> {

	private static final long serialVersionUID = 1L;

	public static final Column USER_ID = Columns.create("user_id").primaryKey(true);
	public static final Column PASSWORD = Columns.create("password");
	public static final Column DEPT_ID = Columns.create("dept_id");
	public static final Column UPDATE_DATE = Columns.create("update_date").type(DataType.TIME).format("yyyy-MM-dd HH:mm:ss.SSS");
	public static final Column AGE = Columns.create("age").type(DataType.NUMERIC);
	
	public static final Table TABLE = Tables.create("users")
			.registerColumn(USER_ID, PASSWORD, DEPT_ID, UPDATE_DATE, AGE);
}
