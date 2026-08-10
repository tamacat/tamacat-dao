/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.tamacat.dao.BindValue;
import org.tamacat.dao.meta.DataType;

public class ExecutedStatementTest {

	@Test
	public void testGetSqlAndValues() {
		List<BindValue> values = Arrays.asList(BindValue.of(DataType.STRING, "admin"));
		ExecutedStatement es = new ExecutedStatement("select * from users where user_id=?", values);
		assertEquals("select * from users where user_id=?", es.getSql());
		assertEquals(1, es.getValues().size());
		assertEquals("admin", es.getValues().get(0).getValue());
	}

	@Test
	public void testNullValuesBecomeEmptyList() {
		ExecutedStatement es = new ExecutedStatement("select 1", null);
		assertTrue(es.getValues().isEmpty());
	}

	@Test
	public void testValuesUnmodifiable() {
		ExecutedStatement es = new ExecutedStatement("select 1",
				Collections.singletonList(BindValue.of(DataType.STRING, "x")));
		try {
			es.getValues().add(BindValue.of(DataType.STRING, "y"));
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
	}

	@Test
	public void testDoesNotMaskValues() {
		ExecutedStatement es = new ExecutedStatement("select 1",
				Collections.singletonList(BindValue.of(DataType.STRING, "secret")));
		assertEquals("secret", es.getValues().get(0).getValue());
	}
}
