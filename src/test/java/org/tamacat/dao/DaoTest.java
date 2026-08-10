/*
 * Copyright 2009 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.impl.LoggingDaoExecuterHandler;
import org.tamacat.dao.impl.LoggingDaoTransactionHandler;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.test.FileData;
import org.tamacat.dao.test.User;
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

	// --- prepare() / executeQuery(PreparedSql, ResultSetHandler) (U2 select-path) ---

	@Test
	public void testPrepare() {
		Param p = dao.prepare(User.USER_ID, Condition.EQUAL, "admin");
		assertEquals("users.user_id=?", p.getSql());
		assertEquals(1, p.getValues().size());
		assertEquals("admin", p.getValues().get(0).getValue());
	}

	@Test
	public void testExecuteQueryPreparedSql_CallbackHandlesResultSet() {
		PreparedSql sql = PreparedSql.of("select * from users where user_id=?",
			Collections.singletonList(BindValue.of(DataType.STRING, "admin")));
		String result = dao.executeQuery(sql, rs -> "handled");
		assertEquals("handled", result);
	}

	@Test
	public void testExecuteQueryPreparedSql_UnboundPlaceholderPropagatesAsDaoException() {
		// no values for a '?' -> DBAccessManager#checkBindable rejects it, and the
		// exception propagates out of Dao#executeQuery(PreparedSql,...) unchanged.
		PreparedSql sql = PreparedSql.ofLiteral("select * from users where user_id=?");
		try {
			dao.executeQuery(sql, rs -> "handled");
			fail();
		} catch (DaoException e) {
			assertTrue(e.getMessage().contains("Unbound placeholder"));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSearch_UsesPrepareAndCallbackPath() {
		Dao<User> userDao = new Dao<>(DBAccessManager.getInstance("default"));
		userDao.setExecuteHandler(new LoggingDaoExecuterHandler());
		userDao.setTransactionHandler(new LoggingDaoTransactionHandler());
		userDao.setPrototype(User.class);
		try {
			Query<User> query = userDao.createQuery()
				.select(User.TABLE.getColumns())
				.where(userDao.prepare(User.USER_ID, Condition.EQUAL, "admin"));
			User result = userDao.search(query);
			assertNotNull(result);

			String executed = userDao.getExecutedQuery().get(userDao.getExecutedQuery().size() - 1);
			assertTrue(executed.indexOf("?") >= 0);
		} finally {
			userDao.release();
		}
	}

	// --- executeUpdate(PreparedSql) / executeUpdate(PreparedSql,int,InputStream)
	// (U3 write-path, business-logic-model.md sec 5.1, BR-9a, BR-16) ---

	@Test
	// Dao.executeUpdate(PreparedSql) (1-arg, BR-16) goes through the full 4-step
	// procedure (DaoEvent / ExecuteHandler / TransactionStateManager / setResult) -
	// same as the String-based executeUpdate(String) - and reaches DBAccessManager
	// with the bind-form SQL text intact.
	public void testExecuteUpdatePreparedSql_FullProcedure() {
		PreparedSql sql = PreparedSql.of("update users set password=? where user_id=?",
				Arrays.asList(BindValue.of(DataType.STRING, "secret"), BindValue.of(DataType.STRING, "admin")));
		int result = dao.executeUpdate(sql);
		assertEquals(0, result); // MockPreparedStatement#executeUpdate() always returns 0 (BR-35)

		String executed = dao.getExecutedQuery().get(dao.getExecutedQuery().size() - 1);
		assertEquals("update users set password=? where user_id=?", executed);
	}

	@Test
	// Dao.executeUpdate(PreparedSql,int,InputStream) (BLOB, BR-9a) also goes through
	// the full 4-step procedure and reaches DBAccessManager#executeUpdate(PreparedSql,int,InputStream).
	public void testExecuteUpdatePreparedSqlIntInputStream_FullProcedure() {
		PreparedSql sql = PreparedSql.of("update file set data=? where file_id=?",
				Arrays.asList(BindValue.ofNull(DataType.OBJECT), BindValue.of(DataType.STRING, "f1")));
		InputStream in = new ByteArrayInputStream(new byte[] { 1, 2 });
		int result = dao.executeUpdate(sql, 1, in);
		assertEquals(0, result); // MockPreparedStatement#executeUpdate() always returns 0 (BR-35)

		String executed = dao.getExecutedQuery().get(dao.getExecutedQuery().size() - 1);
		assertEquals("update file set data=? where file_id=?", executed);
	}

	// --- BLOB override integration (U3 write-path, BR-11, R-27): a DaoAdapter
	// subclass overrides create(T), obtains a bind-path PreparedSql from QueryImpl
	// directly (not DaoAdapter's own default getInsertPreparedSql, which would still
	// be PreparedSql.ofLiteral(...) and make getBindIndexOf always -1 - R-27), and
	// calls executeUpdate(PreparedSql,int,InputStream) explicitly. Real DAOs in this
	// repository all extend DaoAdapter (business-logic-model.md sec 5.2). ---

	static class BlobFileDataDao extends DaoAdapter<FileData> {
		BlobFileDataDao() {
			super("default");
		}

		@Override
		public int create(FileData data) {
			Query<FileData> query = createQuery().addUpdateColumns(FileData.TABLE.getColumns());
			PreparedSql sql = query.getInsertPreparedSql(data);
			int blobIndex = sql.getBindIndexOf(DataType.OBJECT, 1);
			InputStream in = new ByteArrayInputStream(new byte[] { 7, 7 });
			return executeUpdate(sql, blobIndex, in);
		}
	}

	@Test
	public void testBlobOverride_CreateUsesExecuteUpdatePreparedSqlIntInputStream() {
		BlobFileDataDao blobDao = new BlobFileDataDao();
		try {
			FileData data = new FileData();
			data.setValue(FileData.FILE_NAME, "a.bin");
			int result = blobDao.create(data);
			assertEquals(0, result); // MockPreparedStatement#executeUpdate() always returns 0 (BR-35)

			String executed = blobDao.getExecutedQuery().get(blobDao.getExecutedQuery().size() - 1);
			assertTrue(executed.startsWith("INSERT INTO file"));
			assertTrue(executed.indexOf("?") >= 0);
		} finally {
			blobDao.release();
		}
	}

}
