/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Search;
import org.tamacat.dao.Sort;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.test.Dept;
import org.tamacat.dao.test.User;
import org.tamacat.sql.SQLParser;

public class QueryImplTest {

	QueryImpl<User> query;
	
	@Before
	public void setUp() throws Exception {
		query = new QueryImpl<User>();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetSelectColumns_addSelectColumn() {
		query.select(User.USER_ID);
		assertEquals(1, query.getSelectColumns().size());
	}
	
	@Test
	public void testGetSelectColumns_addSelectColumns() {
		query.select(User.TABLE.getColumns());
		assertEquals(5, query.getSelectColumns().size());
	}
	
	@Test
	public void testGetUpdateColumns_addUpdateColumn() {
		query.addUpdateColumn(User.PASSWORD);
		assertEquals(1, query.getUpdateColumns().size());
	}

	@Test
	public void testGetUpdateColumns_addUpdateColumns() {
		query.addUpdateColumns(User.TABLE.getColumns());
		assertEquals(5, query.getUpdateColumns().size());
	}
	
	@Test
	public void testGetUpdateColumns_removeUpdateColumns() {
		query.addUpdateColumns(User.TABLE.getColumns()).removeUpdateColumns(User.PASSWORD);
		assertEquals(4, query.getUpdateColumns().size());
	}
	
	@Test
	public void testGetSelectSQL_AddSelectColumn() {
		query.select(User.USER_ID, User.PASSWORD, User.DEPT_ID);
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id FROM users",
			query.getSelectSQL()
		);
	}

	@Test
	public void testGetSelectSQL_AddSelectColumns() {
		query.select(User.TABLE.getColumns());
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_QueryDistinctTrue() {
		query.select(User.TABLE.getColumns()).distinct(true);
		assertEquals(
			"SELECT DISTINCT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_DistinctFalse_SearchUniqueTrue() {
		Search search = new MySQLSearch().unique(true); //override
		query.select(User.TABLE.getColumns()).distinct(false).and(search, (Sort)null);
		assertEquals(
			"SELECT DISTINCT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_DistinctTrue_SearchUniqueFalse() {
		Search search = new MySQLSearch().unique(false);
		query.select(User.TABLE.getColumns()).distinct(true).and(search, (Sort)null);
		assertEquals(
			"SELECT DISTINCT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetInsertSQL() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		user.setValue(User.DEPT_ID, "123");
		query.addUpdateColumns(User.TABLE.getColumns());
		
		assertEquals(
			"INSERT INTO users (user_id,password,dept_id,update_date,age)"
			+ " VALUES ('admin','test','123',null,null)", query.getInsertSQL(user));
	}

	@Test
	public void testGetUpdateSQL() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		query.addUpdateColumn(User.USER_ID);
		query.addUpdateColumn(User.PASSWORD);
		query.addWhere("and", 
			new SQLParser().value(User.USER_ID, Condition.EQUAL, "admin"));
		assertEquals(
			"UPDATE users SET password='test' WHERE users.user_id='admin'",
			query.getUpdateSQL(user));
	}

	@Test
	public void testGetDeleteSQL() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		
		try {
			query.getDeleteSQL(user);
			fail();
		} catch (InvalidParameterException e) {
			assertTrue(e instanceof InvalidParameterException);
		}
		
		query.addUpdateColumn(User.USER_ID);
		assertEquals(
			"DELETE FROM users WHERE users.user_id='admin'",
			query.getDeleteSQL(user));
	}

	@Test
	public void testAddConnectTable() {
		query.join(User.DEPT_ID, Dept.DEPT_ID);
		assertEquals(" WHERE users.dept_id=dept.dept_id", query.where.toString());
	}

	@Test
	public void testGetTimestampString() {
		assertEquals("current_timestamp", query.getTimestampString());
	}

	@Test
	public void testGetColumnName() {
		assertEquals("users.user_id", QueryImpl.getColumnName(User.USER_ID));
	}
}
