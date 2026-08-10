/*
 * Copyright (c) 2008 Tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.test;

import static org.junit.Assert.*;

import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.DaoFactory;
import org.tamacat.dao.Search;
import org.tamacat.dao.Sort;

public class UserDaoTest {

	UserDao dao;

	@Before
	public void setUp() throws Exception {
		dao = DaoFactory.getDao(UserDao.class);
	}

	@After
	public void tearDown() throws Exception {
		if (dao != null)
			dao.release();
	}

	@Test
	// U3 write-path: create() now routes through DaoAdapter.executeUpdate(PreparedSql)
	// via UserDao's getInsertPreparedSql(T) override (FR-8.2). The executed SQL text is
	// now bind-form (?-bearing); the returned count is 0 because
	// MockPreparedStatement#executeUpdate() always returns 0 (BR-35, a pre-existing
	// mock limitation - not a production behavior change, see DBAccessManagerTest
	// testExecuteUpdateRecordsExecutedStatement for the same documented artifact).
	public void testCreate() {
		User user = new User();
		user.val(User.USER_ID, "admin").val(User.PASSWORD, "password");
		int result = dao.create(user);
		assertEquals(0, result);

		assertEquals(
			"INSERT INTO users (user_id,password,dept_id,update_date,age) VALUES (?,?,?,?,?)",
			dao.getExecutedQuery().get(0)
		);
	}

	@Test
	public void testSearchUser() {
		User user = new User();
		user.val(User.USER_ID, "admin");
		user = dao.search(user);
		assertNotNull(user);
		
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id,users.update_date,users.age"
			+" FROM users WHERE users.user_id=?",
			dao.getExecutedQuery().get(0)
		);
	}

	@Test
	public void testSearchListSearchSort() {
		Search search = dao.createSearch().and(User.USER_ID, Condition.LIKE_PART, "admin");
		search.setMax(10);
		Sort sort = dao.createSort();
		Collection<User> list = dao.searchList(search, sort);
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id,users.update_date,users.age"
			+" FROM users WHERE users.user_id like ?",
			dao.getExecutedQuery().get(0)
		);
		for (User u : list) {
			assertNotNull(u);
			// System.out.printf("user_id=%s, password=%s\n",
			// u.getValue(User.USER_ID), u.getValue(User.PASSWORD));
		}
		assertNotNull(list);
	}

	@Test
	// U3 write-path: see testCreate for the bind-form / mock-count rationale.
	public void testUpdate() {
		User user = new User();
		user.val(User.USER_ID, "admin").val(User.PASSWORD, "password");
		int result = dao.update(user);
		assertEquals(
			"UPDATE users SET password=? WHERE users.user_id=?",
			dao.getExecutedQuery().get(0)
		);
		assertEquals(0, result);
	}

	@Test
	// U3 write-path: see testCreate for the bind-form / mock-count rationale.
	public void testDelete() {
		User user = new User();
		user.val(User.USER_ID, "admin");
		int result = dao.delete(user);
		assertEquals(
			"DELETE FROM users WHERE users.user_id=?",
			dao.getExecutedQuery().get(0)
		);
		assertEquals(0, result);
	}
}