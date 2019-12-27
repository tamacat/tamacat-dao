/*
 * Copyright (c) 2007, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.meta.DataType;
import org.tamacat.util.CloneUtils;

public class DefaultColumnMetaDataTest {

    DefaultColumn meta;

    @Before
    public void setUp() {
        meta = new DefaultColumn();
    }

    @Test
    public void testGetColumnName() {
        meta.setColumnName("user_name");
        assertEquals("user_name", meta.getColumnName());
    }

    @Test
    public void testGetDefaultValue() {
        assertEquals(null, meta.getDefaultValue());
        meta.setDefaultValue("0");
        assertEquals("0", meta.getDefaultValue());
    }

    @Test
    public void testIsAutoGenerateId() {
        assertEquals(false, meta.isAutoGenerateId());
        meta.setAutoGenerateId(true);
        assertEquals(true, meta.isAutoGenerateId());
        meta.setAutoGenerateId(false);
        assertEquals(false, meta.isAutoGenerateId());
    }

    @Test
    public void testIsAutoTimestamp() {
        assertEquals(false, meta.isAutoTimestamp());
        meta.setAutoTimestamp(true);
        assertEquals(true, meta.isAutoTimestamp());
        meta.setAutoTimestamp(false);
        assertEquals(false, meta.isAutoTimestamp());
    }

    @Test
    public void testIsNotNull() {
        assertEquals(false, meta.isNotNull());
        meta.setNotNull(true);
        assertEquals(true, meta.isNotNull());
        meta.setNotNull(false);
        assertEquals(false, meta.isNotNull());
    }

    @Test
    public void testIsPrimaryKey() {
        assertEquals(false, meta.isPrimaryKey());
        meta.setPrimaryKey(true);
        assertEquals(true, meta.isPrimaryKey());
        meta.setPrimaryKey(false);
        assertEquals(false, meta.isPrimaryKey());
    }

    @Test
    public void testGetName() {
        assertEquals(null, meta.getName());
        meta.setName("User Name");
        assertEquals("User Name", meta.getName());
    }

    @Test
    public void testGetType() {
        assertEquals(DataType.STRING, meta.getType());
        meta.setType(DataType.STRING);
        assertEquals(DataType.STRING, meta.getType());
        meta.setType(DataType.DATE);
        assertEquals(DataType.DATE, meta.getType());
        meta.setType(DataType.TIME);
        assertEquals(DataType.TIME, meta.getType());
        meta.setType(DataType.NUMERIC);
        assertEquals(DataType.NUMERIC, meta.getType());
    }
    
    @Test
    public void testClone() throws Exception {
    	System.out.println(CloneUtils.clone(meta));
    	System.out.println(meta.clone());
    }
}
