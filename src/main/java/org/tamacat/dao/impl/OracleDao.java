/*
 * Copyright (c) 2011 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import org.tamacat.dao.Dao;
import org.tamacat.dao.Query;
import org.tamacat.dao.Search;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.orm.ORMappingSupport;

public class OracleDao<T extends ORMappingSupport<T>> extends Dao<T> {

	public OracleDao() {}
	
    @Override
    public Search createSearch() {
        return new OracleSearch();
    }
    
    /**
     * TODO: bugfix
     */
    public Collection<T> searchListForOracle(Query<T> query, int start, int max) {
        Collection<Column>columns = query.getSelectColumns();
        String sql = query.getSelectSQL();
        if (start > 0) {
	        boolean forUpdate = false;
	        StringBuilder q = new StringBuilder();
	        if (sql.toLowerCase().endsWith("for update")) forUpdate = true;
	        if (start > 1) {
	        	q.append("select * from ( select row_.*, rownum rownum_ from ( ");
	        } else if (start > 0){
	        	q.append("select * from ( ");
	        }
	        q.append(sql);
	        if (start > 1) {
	        	q.append(" ) row_ ) where rownum_ <= ? and rownum_ > ?");
	        } else if (start > 0) {
	        	q.append(" ) where rownum <= ?");
	        }
	        if (forUpdate) q.append( " for update" );
        	sql = q.toString();
        }
        ResultSet rs = executeQuery(sql);
        ArrayList<T> list = new ArrayList<>();
        try {
        	//if (start > 0) {
        	//	for (int i=1; i<start; i++) rs.next();
        	//}
            int add = 0;
            while (rs.next()) {
                T o = mapping(columns, rs);
                list.add(o);
                add ++;
                if (max > 0 && add >= max) break;
            }
            //rs = executeQuery("SELECT FOUND_ROWS()");
            //if (rs.next()) {
            //	long hit = rs.getLong(1);
            //	System.out.println(hit);
            //}
        } catch (SQLException e) {
            handleException(e);
        }
        return list;
    }
}
