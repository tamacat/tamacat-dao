/*
 * Copyright 2021 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.tx;

import static org.junit.Assert.*;

import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.DaoFactory;
import org.tamacat.dao.test.User;
import org.tamacat.dao.test.UserDao;

public class TransactionTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCreate() {
		Transaction  tx = Transaction.create("db1", "db2");
		assertEquals(2, tx.names.size());
	}

	@Test
	public void testBegin() {
		Transaction  tx = Transaction.create("db1", "db2");
		assertEquals(2, tx.names.size());
		assertEquals(0, tx.dbm.size());

		tx.begin();
		
		assertEquals(2, tx.dbm.size());
	}

	@Test
	public void testCommit() throws Exception {
		Transaction  tx = Transaction.create("db1", "db2");
		try {
			tx.begin();
			UserDao dao1 = DaoFactory.create(UserDao.class);
			dao1.setDatabase("db1");
			
			UserDao dao2 = DaoFactory.create(UserDao.class);
			dao2.setDatabase("db2");
			
			User user = new User().val(User.USER_ID, "testuser01").val(User.PASSWORD, "password01");
			dao1.create(user);
			dao2.create(user);
			
			tx.commit();
		} catch (Exception e) {
			fail();
			tx.rollback();
			throw e;
		} finally {
			tx.release();
		}
		
	}

	@Test
	public void testRollback() throws Exception {
		Transaction  tx = Transaction.create("db1", "db2");
		try {
			tx.begin();
			UserDao dao1 = DaoFactory.create(UserDao.class);
			dao1.setDatabase("db1");
			
			UserDao dao2 = DaoFactory.create(UserDao.class);
			dao2.setDatabase("db2");
			
			User user = new User().val(User.USER_ID, "testuser01").val(User.PASSWORD, "password01");
			dao1.create(user);
			dao2.create(user);

			throw new SQLException("test");
			//tx.commit();
		} catch (Exception e) {
			tx.rollback();
			//throw e;
		} finally {
			tx.release();
		}
	}

	@Test
	public void testRelease() throws Exception {
		
	}
}
