/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import org.tamacat.dao.Search;

public class MySQLSearch extends Search {

	static class MySQLValueConvertFilter implements Search.ValueConvertFilter {
		@Override
		public String convertValue(String value) {
			if (value != null) {
				return value.replace("'", "''").replace("\\", "\\\\");
			} else {
				return value;
			}
		}
	}

	public MySQLSearch() {
		super(new MySQLValueConvertFilter());
	}
}
