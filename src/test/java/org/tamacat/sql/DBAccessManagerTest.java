/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DBAccessManagerTest {

    DBAccessManager dbm;

    @Before
    public void setUp() throws Exception {
        dbm = DBAccessManager.getInstance("default");
    }

    @After
    public void tearDown() throws Exception {
        if (dbm != null) dbm.release();
    }

    @Test
    public void testGetConnection() throws Exception {
//        Connection con = dbm.getConnection();
//        assertNotNull(con);
//
//        Connection con2 = dbm.getConnection();
//        assertNotNull(con);
//        assertEquals(con, con2);

        dbm.executeQuery("select * from users");
    }

}
