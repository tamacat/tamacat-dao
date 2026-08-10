/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.DataType;

public class BindValueTest {

	@Test
	public void testOf() {
		BindValue v = BindValue.of(DataType.STRING, "abc");
		assertEquals(DataType.STRING, v.getType());
		assertEquals("abc", v.getValue());
		assertNull(v.getStream());
		assertFalse(v.isNull());
	}

	@Test
	public void testOfRejectsNullType() {
		try {
			BindValue.of(null, "abc");
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testOfRejectsNullValue() {
		try {
			BindValue.of(DataType.STRING, null);
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testOfStream() {
		InputStream in = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
		BindValue v = BindValue.ofStream(in);
		assertEquals(DataType.OBJECT, v.getType());
		assertNull(v.getValue());
		assertSame(in, v.getStream());
		assertFalse(v.isNull());
	}

	@Test
	public void testOfStreamRejectsNull() {
		try {
			BindValue.ofStream(null);
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testOfNull() {
		BindValue v = BindValue.ofNull(DataType.NUMERIC);
		assertEquals(DataType.NUMERIC, v.getType());
		assertNull(v.getValue());
		assertNull(v.getStream());
		assertTrue(v.isNull());
	}

	@Test
	public void testOfNullRejectsNullType() {
		try {
			BindValue.ofNull(null);
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testOfNullForObjectColumn() {
		BindValue v = BindValue.ofNull(DataType.OBJECT);
		assertEquals(DataType.OBJECT, v.getType());
		assertTrue(v.isNull());
		assertNull(v.getStream());
	}
}
