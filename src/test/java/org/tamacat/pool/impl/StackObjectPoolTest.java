/*
 * Copyright 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.pool.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.pool.ObjectPool;

public class StackObjectPoolTest {

    ObjectPool<Connection> pool;

    @Before
    public void setUp() throws Exception {
        pool = new StackObjectPool<Connection>(new ConnectionFactory());
    }

    @After
    public void tearDown() throws Exception {
        if (pool != null) pool.release();
    }

    @Test
    public void testFree() {
        Connection con = pool.getObject();
        pool.free(con);
    }

    @Test
    public void testGetInstance() {
        Connection con = pool.getObject();

        pool.free(con);
    }
}
