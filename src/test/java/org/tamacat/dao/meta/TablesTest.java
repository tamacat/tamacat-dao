package org.tamacat.dao.meta;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TablesTest {

	static final Column C1 = Columns.create("c1");
	static final Column C2 = Columns.create("c2").type(DataType.TIME).format("yyyy-MM-dd HH:mm:ss.SSS");
	
	static final Column V_C1 = Columns.create("c1");
	static final Column V_C2 = Columns.create("c2").type(DataType.TIME);
	
	static final Table TAB1 = Tables.create("tab1").registerColumn(C1, C2);
	
	
	static final Table VIEW = Tables.create("alias", "tab1")
			.registerColumn(V_C1, V_C2);
	 
	@Test
	public void testColumn() {
//		for (Column col : TAB1.getColumns()) {
//			System.out.println(col.getColumnName()+", "+col.getTable().getTableName());
//		}
//		
//		for (Column col : VIEW.getColumns()) {
//			System.out.println(col.getColumnName()+", "+col.getTable().getTableName());
//		}
//		
//		for (Column col : TAB1.getColumns()) {
//			System.out.println(col.getColumnName()+", "+col.getTable().getTableOrAliasName());
//		}
		
		assertEquals(V_C1.getTable().getTableOrAliasName(), VIEW.getTableOrAliasName());
		assertEquals(V_C1.getTable().getTableName(), VIEW.getTableName());
	}
	
	
	@Test
	public void testColumnDate() {
		assertEquals(C2.getFormat(), TAB1.find("c2").getFormat());
	}
}
