/*
 * Copyright 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.impl.LoggingDaoExecuterHandler;
import org.tamacat.dao.impl.LoggingDaoTransactionHandler;
import org.tamacat.sql.DBAccessManager;

public class DaoTest {
	
	Dao<?> dao;
	
	@SuppressWarnings("rawtypes")
	@Before
	public void setUp() throws Exception {
		dao = new Dao(DBAccessManager.getInstance("default"));
		dao.setExecuteHandler(new LoggingDaoExecuterHandler());
		dao.setTransactionHandler(new LoggingDaoTransactionHandler());
	}

	@After
	public void tearDown() throws Exception {
		if (dao != null) dao.release();
	}

	@Test
	public void testStartTransaction() {
		assertFalse(dao.isTransactionStarted());
		dao.startTransaction();
		assertTrue(dao.isTransactionStarted());
		try {
			dao.executeQuery("insert into test (id, name) values (1,'test')");
			dao.executeQuery("update test set name='test2' where id='1'");
			dao.executeQuery("delete from test where id='1'");
			dao.commit();
			assertTrue(dao.isTransactionStarted());
		} catch (Exception e) {
			dao.rollback();
			dao.handleException(e);
		} finally {
			dao.endTransaction();
			assertFalse(dao.isTransactionStarted());
		}
	}
	
	@Test
	public void testTransaction() {
		assertFalse(dao.isTransactionStarted());
		dao.startTransaction();
		assertTrue(dao.isTransactionStarted());
		try {
			dao.executeUpdate("insert into test (id, name) values (1,'test')");
			dao.executeUpdate("update test set name='test2' where id='1'");
			dao.executeUpdate("delete from test where id='1'");
			dao.commit();
			assertTrue(dao.isTransactionStarted());
		} catch (Exception e) {
			dao.rollback();
			dao.handleException(e);
		} finally {
			dao.endTransaction();
			assertFalse(dao.isTransactionStarted());
		}
	}

}
