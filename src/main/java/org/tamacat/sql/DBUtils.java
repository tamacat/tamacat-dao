/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DBUtils {

	public static void setAutoCommitFalse(DBAccessManager dbm) {
		if (dbm.getAutoCommit()) {
			dbm.setAutoCommit(false);
		}
	}
	
    public static void setAutoCommitTrue(DBAccessManager dbm) {
    	try {
    		dbm.setAutoCommit(true);
    	} catch (Exception e) {
    	}
    }
    
    public static void close(Statement stmt) {
    	try {
    		if (stmt != null) stmt.close();
    	} catch (SQLException e) {
    	}
    }
    
    public static void close(ResultSet rs) {
    	try {
    		if (rs != null) rs.close();
    	} catch (SQLException e) {
    	}
    }
}
