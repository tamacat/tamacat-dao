/*
 * Copyright 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.test.User;

public class SortTest {

	Sort sort;
	
	@Before
	public void setUp() throws Exception {
		sort = new Sort();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAsc() {
		sort.asc(User.USER_ID);
		assertEquals("users.user_id asc", sort.getSortString());
	}
	
	@Test
	public void testDesc() {
		sort.desc(User.USER_ID);
		assertEquals("users.user_id desc", sort.getSortString());
	}

	//	@Test
	public void testSort() {
		sort.sort(User.USER_ID, "asc");
		System.out.println(sort.getSortString());
	}

	// --- U5 identifier-safety: non-Column key path (AC-10b, ORDER BY) ---

	@Test
	// ID-1: single quote in a raw (non-Column) sort key is rejected.
	public void testSort_NonColumnKey_SingleQuote_Throws() {
		try {
			sort.sort("col'; DROP TABLE users; --", Sort.Order.ASC);
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-2: double quote.
	public void testSort_NonColumnKey_DoubleQuote_Throws() {
		try {
			sort.sort("col\"name", Sort.Order.ASC);
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-3: semicolon.
	public void testSort_NonColumnKey_Semicolon_Throws() {
		try {
			sort.sort("col;name", Sort.Order.ASC);
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-4: line comment.
	public void testSort_NonColumnKey_LineComment_Throws() {
		try {
			sort.sort("col--comment", Sort.Order.ASC);
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-5: block comment.
	public void testSort_NonColumnKey_BlockComment_Throws() {
		try {
			sort.sort("col/*comment*/", Sort.Order.ASC);
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// BR-11: legitimate raw expressions without dangerous literals still pass.
	public void testSort_NonColumnKey_LegitimateExpression_Passes() {
		sort.sort("RAND()", Sort.Order.ASC);
		assertEquals("RAND() asc", sort.getSortString());
	}

}
