/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.mock.sql;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Types;

import org.junit.Before;
import org.junit.Test;

public class MockPreparedStatementTest {

	MockConnection con;
	MockPreparedStatement ps;

	@Before
	public void setUp() {
		con = new MockConnection();
		ps = new MockPreparedStatement(con, "select * from users where user_id=?");
	}

	@Test
	public void testGetPreparedSql() {
		assertEquals("select * from users where user_id=?", ps.getPreparedSql());
	}

	@Test
	public void testTwoArgConstructorPreservesOneArgBehavior() {
		MockPreparedStatement legacy = new MockPreparedStatement(con);
		assertNull(legacy.getPreparedSql());
	}

	@Test
	public void testSetStringRecordsPositionAndValue() throws SQLException {
		ps.setString(1, "admin");
		assertEquals("admin", ps.getBoundValue(1));
		assertEquals(1, ps.getBoundValues().size());
	}

	@Test
	public void testMultiplePositionsInOrder() throws SQLException {
		ps.setString(2, "b");
		ps.setString(1, "a");
		ps.setString(3, "c");
		assertEquals("a", ps.getBoundValue(1));
		assertEquals("b", ps.getBoundValue(2));
		assertEquals("c", ps.getBoundValue(3));
		assertEquals(java.util.Arrays.asList("a", "b", "c"), ps.getBoundValues());
	}

	@Test
	public void testSamePositionLastWins() throws SQLException {
		ps.setNull(1, Types.BLOB);
		InputStream in = new ByteArrayInputStream(new byte[] { 1, 2 });
		ps.setBinaryStream(1, in);
		assertSame(in, ps.getBoundValue(1));
	}

	@Test
	public void testExecuteUpdateAlwaysReturnsZero() throws SQLException {
		ps.setString(1, "admin");
		assertEquals(0, ps.executeUpdate());
	}

	@Test
	public void testGetBoundValueForUnsetPositionIsNull() {
		assertNull(ps.getBoundValue(1));
	}
}
