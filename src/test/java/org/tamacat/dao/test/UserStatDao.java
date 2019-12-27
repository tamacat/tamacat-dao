/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.test;

import java.util.Collection;

import org.tamacat.dao.DaoAdapter;
import org.tamacat.dao.Query;
import org.tamacat.dao.Search;
import org.tamacat.dao.Sort;

public class UserStatDao extends DaoAdapter<UserStat> {

    public Collection<UserStat> searchList(Search search, Sort sort) {
        Query<UserStat> query = createQuery()
            .select(UserStat.TABLE.getColumns()).and(search, sort)
            .groupBy(UserStat.USER_ID);
        return super.searchList(query, search.getStart(), search.getMax());
    }

    @Override
    protected String getInsertSQL(UserStat data) {
        Query<UserStat> query = createQuery().addUpdateColumns(UserStat.TABLE.getColumns());
        return query.getInsertSQL(data);
    }

    @Override
    protected String getUpdateSQL(UserStat data) {
        Query<UserStat> query = createQuery().addUpdateColumns(UserStat.TABLE.getColumns());
        return query.getUpdateSQL(data);
    }

    @Override
    protected String getDeleteSQL(UserStat data) {
        Query<UserStat> query = createQuery().addUpdateColumn(UserStat.USER_ID);
        return query.getDeleteSQL(data);
    }
    
    public int createTable() {
    	return executeUpdate("CREATE TABLE user_stat (user_id varchar(32), score int)");
    }
    
    public int dropTable() {
    	return executeUpdate("DROP TABLE users");
    }
}
