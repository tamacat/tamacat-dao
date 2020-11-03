/*
 * Copyright 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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

}
