/*
 * Copyright (c) 2011, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import org.tamacat.dao.Search;

public class OracleSearch extends Search {
	
    static class OracleValueConvertFilter implements Search.ValueConvertFilter {
        public String convertValue(String value) {
            return value.replace("'", "''");
        }
    }

    public OracleSearch() {
        super(new OracleValueConvertFilter());
    }
}
