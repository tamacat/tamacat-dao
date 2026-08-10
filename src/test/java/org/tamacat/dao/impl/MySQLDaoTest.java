package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Query;
import org.tamacat.dao.test.User;
import org.tamacat.sql.ExecutedStatement;

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

	// --- searchList() paging (U4 dialects, Step 4 migration: business-logic-model.md
	// sec 3.2, business-rules.md BR-6, sec 9-1) ---
	// FR-6.3: LIMIT is int-literal text, never bound as '?' - only WHERE-clause
	// predicates produce bind values.

	MySQLDao<User> pagedDao;

	@Before
	public void setUpPagedDao() throws Exception {
		pagedDao = new MySQLDao<>();
		pagedDao.setDatabase("default");
		pagedDao.setPrototype(User.class);
	}

	@After
	public void tearDownPagedDao() throws Exception {
		if (pagedDao != null) {
			pagedDao.close();
		}
	}

	@Test
	public void testSearchList_LimitIsLiteral_NotBound() {
		pagedDao.useHitCount(false);
		Query<User> query = pagedDao.createQuery().select(User.USER_ID)
			.where(pagedDao.prepare(User.USER_ID, Condition.EQUAL, "admin"));
		pagedDao.searchList(query, 1, 5);

		ExecutedStatement executed = pagedDao.getDBAccessManager().getExecutedStatements()
			.get(pagedDao.getDBAccessManager().getExecutedStatements().size() - 1);
		assertEquals(
			"SELECT users.user_id FROM users WHERE users.user_id=? limit 0,5",
			executed.getSql());
		// exactly one bind value (the WHERE predicate) - the LIMIT boundary values
		// (0, 5) never became bind values.
		assertEquals(1, executed.getValues().size());
		assertEquals("admin", executed.getValues().get(0).getValue());
	}
}
