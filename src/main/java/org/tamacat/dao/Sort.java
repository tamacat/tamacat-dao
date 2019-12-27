/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import org.tamacat.dao.meta.Column;
import org.tamacat.dao.util.MappingUtils;

/**
 * Sort condition for Database access.
 * (SQL: order by)
 */
public class Sort {

    public enum Order {

        ASC("asc"),
        DESC("desc");

        private String name;

        private Order(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    
    StringBuilder sort = new StringBuilder();

    public Sort asc(Object k) {
        return sort(k, Order.ASC);
    }

    public Sort desc(Object k) {
        return sort(k, Order.DESC);
    }

    public String getSortString() {
        return sort.toString();
    }

    public Sort sort(Object k, Object o) {
        if (sort.length() > 0) sort.append(",");
        if (k instanceof Column) {
        	Column col = (Column)k;
        	if (col.isFunction()) {
        		sort.append(col.getColumnName() + " " + o.toString());
        	} else {
        		sort.append(MappingUtils.getColumnName(col) + " " + o.toString());
        	}
        } else {
        	sort.append(k.toString() + " " + o.toString());
        }
        return this;
    }
}
