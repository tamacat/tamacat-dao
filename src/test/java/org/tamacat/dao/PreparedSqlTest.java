/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.DataType;

public class PreparedSqlTest {

	@Test
	public void testOfChecked() {
		PreparedSql sql = PreparedSql.of("test1.name like ? escape '$'",
				Collections.singletonList(BindValue.of(DataType.STRING, "%tama%")));
		assertEquals(1, sql.getPlaceholderCount());
		assertFalse(sql.hasUnboundPlaceholders());
	}

	@Test
	public void testOfRejectsCountMismatch() {
		try {
			PreparedSql.of("test1.name=?", Collections.<BindValue>emptyList());
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testCountPlaceholdersScanTable() {
		assertEquals(1, PreparedSql.ofLiteral("test1.name like ? escape '$'").getPlaceholderCount());
		assertEquals(0, PreparedSql.ofLiteral(
				"INSERT INTO users (a,b,c,d,e) VALUES ('admin','password',null,null,null)").getPlaceholderCount());
		assertEquals(1, PreparedSql.ofLiteral("UPDATE file SET file.data=? WHERE file.id='1'").getPlaceholderCount());
		assertEquals(0, PreparedSql.ofLiteral("WHERE name='a?b'").getPlaceholderCount());
		assertEquals(0, PreparedSql.ofLiteral("WHERE name='a\\''b'").getPlaceholderCount());
		assertEquals(-1, PreparedSql.ofLiteral("WHERE name='a").getPlaceholderCount());
	}

	@Test
	public void testOfLiteralDoesNotCheckCount() {
		// unbound '?' with no values must not throw at construction time (ADR-012).
		PreparedSql sql = PreparedSql.ofLiteral("UPDATE file SET file.data=? WHERE file.id='1'");
		assertTrue(sql.getValues().isEmpty());
		assertTrue(sql.hasUnboundPlaceholders());
	}

	@Test
	public void testHasUnboundPlaceholdersForUnterminatedQuote() {
		PreparedSql sql = PreparedSql.ofLiteral("WHERE name='a");
		assertEquals(-1, sql.getPlaceholderCount());
		assertTrue(sql.hasUnboundPlaceholders());
	}

	@Test
	public void testGetBindIndexOf() {
		PreparedSql sql = PreparedSql.of("a=? and b=? and c=?", Arrays.asList(
				BindValue.of(DataType.STRING, "x"),
				BindValue.ofNull(DataType.OBJECT),
				BindValue.of(DataType.STRING, "y")));
		assertEquals(2, sql.getBindIndexOf(DataType.OBJECT, 1));
		assertEquals(-1, sql.getBindIndexOf(DataType.OBJECT, 2));
		assertEquals(1, sql.getBindIndexOf(DataType.STRING, 1));
		assertEquals(3, sql.getBindIndexOf(DataType.STRING, 2));
	}

	@Test
	public void testCheckedHasUnboundPlaceholdersAlwaysFalse() {
		PreparedSql sql = PreparedSql.of("a=?", Collections.singletonList(BindValue.of(DataType.STRING, "x")));
		assertFalse(sql.hasUnboundPlaceholders());
	}
}
