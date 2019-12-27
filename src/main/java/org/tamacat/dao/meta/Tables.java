/*
 * Copyright (c) 2015 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

public class Tables {

	public static Table create(String... name) {
		return new DefaultTable(name);
	}

}
