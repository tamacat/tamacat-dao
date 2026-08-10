/*
 * Copyright (c) 2011, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import org.tamacat.dao.Search;

public class OracleSearch extends Search {
	
    static class OracleValueConvertFilter implements Search.ValueConvertFilter {
        public String convertValue(String value) {
            if (value != null) {
                return value.replace("'", "''");
            } else {
                return value;
            }
        }
    }

    public OracleSearch() {
        super(new OracleValueConvertFilter());
    }
}
