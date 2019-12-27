/*
 * Copyright (c) 2009, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.sql;


import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DriverManagerDataSourceTest {

	DriverManagerDataSource ds;

	@Before
	public void setUp() throws Exception {
		ds = new DriverManagerDataSource();
		ds.setDriverClass("org.tamacat.mock.sql.MockDriver");
		ds.setUrl("jdbc:mock://localhost/test");
		ds.setUsername("test");
		ds.setPassword("test");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetConnection() {
		try {
			Connection con = ds.getConnection();
			Assert.assertNotNull(con);
			con.close();
		} catch (SQLException e) {
			Assert.fail(e.getMessage());
		}
	}
}
