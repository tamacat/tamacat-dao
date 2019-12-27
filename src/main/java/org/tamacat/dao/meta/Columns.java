/*
 * Copyright (c) 2015 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

public class Columns {

	/**
	 * Create a new Column object.
	 * default DataType is STRING
	 * @param name
	 * @return Column
	 */
	public static Column create(String name) {
		DefaultColumn col = new DefaultColumn(name);
		//if (name.indexOf("date")>=0 || name.indexOf("time")>=0) {
		//	col.type(DataType.TIME);
		//} else {
		//	col.type(DataType.STRING);
		//}
		return col;
	}

}
