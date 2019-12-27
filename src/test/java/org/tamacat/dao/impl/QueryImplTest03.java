/*
 * Copyright (c) 2014 TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.test.UserStat;

public class QueryImplTest03 {

	QueryImpl<UserStat> query;
	
	@Before
	public void setUp() throws Exception {
		query = new QueryImpl<UserStat>();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetSelectSQL_UseFunction() {
		query.select(UserStat.TABLE.getColumns());
		assertEquals(
			"SELECT user_stat.user_id,sum(score) total_score FROM user_stat",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_UseFunction_GROUP_BY() {
		query.select(UserStat.TABLE.getColumns()).groupBy(UserStat.USER_ID);
		assertEquals(
			"SELECT user_stat.user_id,sum(score) total_score FROM user_stat GROUP BY user_stat.user_id",
			query.getSelectSQL()
		);
	}
}
