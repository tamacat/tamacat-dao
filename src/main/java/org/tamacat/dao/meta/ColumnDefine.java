/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.meta;

import java.io.Serializable;

class ColumnDefine implements Serializable {

    private static final long serialVersionUID = 1L;

    static final ColumnDefine PRIMARY_KEY = new ColumnDefine("primary key");
    static final ColumnDefine FOREIGN_KEY = new ColumnDefine("foreign key");
    static final ColumnDefine AUTO_GENERATE_ID = new ColumnDefine("AutoGenerateId");
    static final ColumnDefine AUTO_TIMESTAMP = new ColumnDefine("AutoTimestamp");
    static final ColumnDefine NOT_NULL = new ColumnDefine("not null");
    static final ColumnDefine FUNCTION = new ColumnDefine("function");
    
    private String defineName;

    public ColumnDefine(String defineName) {
        this.defineName = defineName;
    }

    public String getDefineName() {
        return defineName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((defineName == null) ? 0 : defineName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof ColumnDefine))
            return false;
        final ColumnDefine other = (ColumnDefine) obj;
        if (defineName == null) {
            if (other.defineName != null)
                return false;
        } else if (!defineName.equals(other.defineName))
            return false;
        return true;
    }
}
