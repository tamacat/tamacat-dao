/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.mock.sql;

import static org.junit.Assert.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;

public class MockConnectionTest {

	MockConnection con;

	@Before
	public void setUp() {
		con = new MockConnection();
	}

	@Test
	public void testPrepareStatementRetainsSql() throws SQLException {
		PreparedStatement ps = con.prepareStatement("select 1");
		assertEquals("select 1", ((MockPreparedStatement) ps).getPreparedSql());
	}

	@Test
	public void testGetPreparedStatementsAccumulates() throws SQLException {
		con.prepareStatement("select 1");
		con.prepareStatement("select 2");
		assertEquals(2, con.getPreparedStatements().size());
	}

	@Test
	public void testGetLastPreparedStatement() throws SQLException {
		con.prepareStatement("select 1");
		con.prepareStatement("select 2");
		assertEquals("select 2", con.getLastPreparedStatement().getPreparedSql());
	}

	@Test
	public void testGetLastPreparedStatementWhenNoneReturnsNull() {
		assertNull(con.getLastPreparedStatement());
	}

	@Test
	public void testClearPreparedStatements() throws SQLException {
		con.prepareStatement("select 1");
		con.clearPreparedStatements();
		assertTrue(con.getPreparedStatements().isEmpty());
		assertNull(con.getLastPreparedStatement());
	}
}
