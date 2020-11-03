package org.tamacat.dao.meta;

import static org.junit.Assert.*;

import org.junit.Test;

public class ColumnsTest {

	@Test
	public void testCreate() {
		assertEquals(DataType.STRING, Columns.create("test_data").getType());
		//Change Default STRING 1.4-20181219
		assertEquals(DataType.STRING, Columns.create("create_time").getType());
		assertEquals(DataType.STRING, Columns.create("update_date").getType());	
	}
	
	@Test
	public void testFunction() {
		Column c1 = Columns.create("max").type(DataType.FUNCTION).functionName("max");
		assertEquals("max", c1.getFunctionName());
	}
}
