package org.tamacat.dao.test;

import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.meta.DefaultColumn;
import org.tamacat.dao.meta.DefaultTable;
import org.tamacat.dao.orm.MapBasedORMappingBean;

public class Data extends MapBasedORMappingBean<Data> {
	private static final long serialVersionUID = 1L;

	public static final DefaultTable TABLE = new DefaultTable("data");
	
	public static final DefaultColumn ID = new DefaultColumn("id");
	public static final DefaultColumn NAME = new DefaultColumn("name");
	public static final DefaultColumn NUM1 = new DefaultColumn("num1");
	public static final DefaultColumn NUM2 = new DefaultColumn("num2");
	public static final DefaultColumn UPDATE_DATE = new DefaultColumn("update_date");

	static {
		ID.type(DataType.STRING).primaryKey(true).autoGenerateId(true);
		NAME.type(DataType.STRING);
		NUM1.type(DataType.NUMERIC);
		NUM2.type(DataType.FLOAT);
		UPDATE_DATE.type(DataType.TIME);
		TABLE.registerColumn(ID, NAME, NUM1, NUM2, UPDATE_DATE);
	}
	
	public Data() {
		defaultTableName = TABLE.getTableNameWithSchema();
	}
}
