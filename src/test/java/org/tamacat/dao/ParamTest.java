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

public class ParamTest {

	@Test
	public void testOfWithMatchingCount() {
		Param p = Param.of("test1.name=?", Collections.singletonList(BindValue.of(DataType.STRING, "abc")));
		assertEquals("test1.name=?", p.getSql());
		assertEquals(1, p.size());
		assertEquals(1, p.getValues().size());
	}

	@Test
	public void testOfWithNoPlaceholders() {
		Param p = Param.of("test1.name is null", null);
		assertEquals(0, p.size());
		assertTrue(p.getValues().isEmpty());
	}

	@Test
	public void testOfRejectsCountMismatch() {
		try {
			Param.of("test1.name=?", Collections.<BindValue>emptyList());
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testOfRejectsUnterminatedQuote() {
		try {
			Param.of("test1.name='a", null);
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testOfSkipsQuotedPlaceholders() {
		// '?' inside a quoted literal is not counted.
		Param p = Param.of("test1.name='a?b'", null);
		assertEquals(0, p.size());
	}

	@Test
	public void testValuesIsUnmodifiable() {
		Param p = Param.of("test1.name=?", Collections.singletonList(BindValue.of(DataType.STRING, "abc")));
		try {
			p.getValues().add(BindValue.of(DataType.STRING, "x"));
			fail();
		} catch (UnsupportedOperationException e) {
			// expected
		}
	}

	@Test
	public void testAndConcatenatesTextAndValues() {
		Param left = Param.of("a=?", Collections.singletonList(BindValue.of(DataType.STRING, "1")));
		Param right = Param.of("b=?", Collections.singletonList(BindValue.of(DataType.STRING, "2")));
		Param joined = left.and(right);
		assertEquals("a=? and b=?", joined.getSql());
		assertEquals(2, joined.size());
	}

	@Test
	public void testOrConcatenatesTextAndValues() {
		Param left = Param.of("a=?", Collections.singletonList(BindValue.of(DataType.STRING, "1")));
		Param right = Param.of("b=?", Collections.singletonList(BindValue.of(DataType.STRING, "2")));
		Param joined = left.or(right);
		assertEquals("a=? or b=?", joined.getSql());
		assertEquals(2, joined.size());
	}

	@Test
	public void testAndWithNullReturnsSelf() {
		Param left = Param.of("a=?", Arrays.asList(BindValue.of(DataType.STRING, "1")));
		assertSame(left, left.and(null));
		assertSame(left, left.or(null));
	}
}
