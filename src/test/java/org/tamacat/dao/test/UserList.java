/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.test;

import java.util.ArrayList;

import org.tamacat.dao.orm.MapBasedORMappingBean;

public class UserList extends ArrayList<MapBasedORMappingBean<User>> {

    private static final long serialVersionUID = 1L;

    public UserList() {
    }
}
