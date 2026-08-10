/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.BindValue;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.meta.DataType;
import org.tamacat.mock.sql.MockConnection;
import org.tamacat.mock.sql.MockPreparedStatement;

public class PreparedStatementBinderTest {

	MockConnection con;
	MockPreparedStatement ps;

	@Before
	public void setUp() throws SQLException {
		con = new MockConnection();
		ps = (MockPreparedStatement) con.prepareStatement("insert into t values (?,?,?)");
	}

	@Test
	public void testBindsInOneBasedPositionOrder() {
		List<BindValue> values = Arrays.asList(
				BindValue.of(DataType.STRING, "a"),
				BindValue.of(DataType.STRING, "b"),
				BindValue.of(DataType.STRING, "c"));
		PreparedStatementBinder.bind(ps, values);
		assertEquals("a", ps.getBoundValue(1));
		assertEquals("b", ps.getBoundValue(2));
		assertEquals("c", ps.getBoundValue(3));
	}

	@Test
	public void testTextValueUsesSetString() {
		PreparedStatementBinder.bind(ps, Collections.singletonList(BindValue.of(DataType.NUMERIC, "123")));
		assertEquals("123", ps.getBoundValue(1));
	}

	@Test
	public void testNullValueUsesSetNull() {
		PreparedStatementBinder.bind(ps, Collections.singletonList(BindValue.ofNull(DataType.STRING)));
		assertNull(ps.getBoundValue(1));
		assertEquals(1, ps.getBoundValues().size());
	}

	@Test
	public void testStreamValueUsesSetBinaryStream() {
		InputStream in = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
		PreparedStatementBinder.bind(ps, Collections.singletonList(BindValue.ofStream(in)));
		assertSame(in, ps.getBoundValue(1));
	}

	@Test
	public void testNullObjectValueUsesSetNullNotBinaryStream() {
		PreparedStatementBinder.bind(ps, Collections.singletonList(BindValue.ofNull(DataType.OBJECT)));
		assertNull(ps.getBoundValue(1));
	}

	@Test
	public void testSqlTypeOfMapping() {
		assertEquals(java.sql.Types.NUMERIC, PreparedStatementBinder.sqlTypeOf(DataType.NUMERIC));
		assertEquals(java.sql.Types.NUMERIC, PreparedStatementBinder.sqlTypeOf(DataType.FLOAT));
		assertEquals(java.sql.Types.DATE, PreparedStatementBinder.sqlTypeOf(DataType.DATE));
		assertEquals(java.sql.Types.TIMESTAMP, PreparedStatementBinder.sqlTypeOf(DataType.TIME));
		assertEquals(java.sql.Types.BLOB, PreparedStatementBinder.sqlTypeOf(DataType.OBJECT));
		assertEquals(java.sql.Types.VARCHAR, PreparedStatementBinder.sqlTypeOf(DataType.STRING));
		assertEquals(java.sql.Types.VARCHAR, PreparedStatementBinder.sqlTypeOf(DataType.BOOLEAN));
	}

	@Test
	public void testSqlExceptionWrappedAsDaoException() {
		java.sql.PreparedStatement throwing = new ThrowingPreparedStatement();
		try {
			PreparedStatementBinder.bind(throwing, Collections.singletonList(BindValue.of(DataType.STRING, "x")));
			fail();
		} catch (DaoException e) {
			assertTrue(e.getCause() instanceof SQLException);
		}
	}

	/**
	 * Minimal PreparedStatement stub whose setString throws, to verify
	 * SQLException -> DaoException translation without depending on Mock internals.
	 */
	private static class ThrowingPreparedStatement extends MockPreparedStatement {
		ThrowingPreparedStatement() {
			super(null, "insert into t values (?)");
		}

		@Override
		public void setString(int parameterIndex, String x) throws SQLException {
			throw new SQLException("boom");
		}
	}
}
