package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Query;
import org.tamacat.dao.test.User;

public class MySQLDaoTest {

	MySQLDao<User> dao;
	
	@Before
	public void setUp() throws Exception {
		dao = new MySQLDao<User>();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCreateRdbSearch() {
		assertTrue(dao.createSearch() instanceof MySQLSearch);
		
	}

	@Test
	public void testParam() {
		assertEquals("users.user_id='\\\\'", dao.param(User.USER_ID, Condition.EQUAL, "\\"));
	}
	
	@Test
	public void testSearchListRdbQueryOfTIntInt() {
		Query<User> query = dao.createQuery();
		//dao.searchList(query, 1, 5);
		assertNotNull(query);
	}
	
	@Test
	@SuppressWarnings("rawtypes")
	public void testCreateQuery() {
		Query<User> query = dao.createQuery();
		if (query instanceof QueryImpl) {
			assertTrue(((QueryImpl)query).valueConvertFilter instanceof MySQLSearch.MySQLValueConvertFilter);
		} else {
			fail();
		}
	}
	
	@Test
	public void testGetInsertSQL() {
		Query<User> query = dao.createQuery();
		query.addUpdateColumns(User.TABLE.columns());
		User user = new User();
		user.val(User.USER_ID, "guest");
		user.val(User.DEPT_ID, "Development");
		user.val(User.PASSWORD, null);
		assertEquals("INSERT INTO users (user_id,password,dept_id,update_date,age) VALUES ('guest',null,'Development',null,null)",
			query.getInsertSQL(user));
		
		user.val(User.PASSWORD, "'");
		assertEquals("INSERT INTO users (user_id,password,dept_id,update_date,age) VALUES ('guest','''','Development',null,null)",
			query.getInsertSQL(user));
		
		user.val(User.PASSWORD, "\''\"");
		assertEquals("INSERT INTO users (user_id,password,dept_id,update_date,age) VALUES ('guest','''''\"','Development',null,null)",
			query.getInsertSQL(user));
	}
	
	@Test
	public void testGetUpdateSQL() {
		Query<User> query = dao.createQuery();
		query.addUpdateColumns(User.TABLE.columns());
		User user = new User();
		user.val(User.USER_ID, "guest");
		user.val(User.DEPT_ID, "Development");
		user.val(User.PASSWORD, "");
		
		query.where(dao.param(User.USER_ID, Condition.EQUAL, user.val(User.USER_ID)));
		assertEquals("UPDATE users SET password='',dept_id='Development' WHERE users.user_id='guest'",
				query.getUpdateSQL(user));
	}
}
