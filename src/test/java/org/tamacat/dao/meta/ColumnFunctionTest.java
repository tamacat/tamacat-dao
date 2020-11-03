package org.tamacat.dao.meta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ColumnFunctionTest {

	static final Column COL1 = Columns.create("col1").type(DataType.FUNCTION).functionName("max");
	static final Column COL2 = Columns.create("col2").type(DataType.FUNCTION);
	static final Column COL3 = Columns.create("col3").type(DataType.FUNCTION);
	static final Column COL4 = Columns.create("col4").type(DataType.FUNCTION);
	
	static {
		COL2.functionName("max");
	}
	
	static final Table TABLE = Tables.create("tab1").registerColumn(COL1, COL2, COL3, COL4);
	
	static {
		COL4.functionName("max"); // != TABLE.find("col4")
		TABLE.find("col3").functionName("max"); // != COL3
	}
	
	@Test
	public void testFunctionName() {
		assertEquals("max", COL1.getFunctionName());
		assertEquals("max", COL2.getFunctionName());
		assertNull(COL3.getFunctionName());
		assertEquals("max", COL4.getFunctionName());
		
		assertEquals("max", TABLE.find("col1").getFunctionName());
		assertEquals("max", TABLE.find("col2").getFunctionName());
		assertEquals("max", TABLE.find("col3").getFunctionName());
		assertNull(TABLE.find("col4").getFunctionName());
	}

}
