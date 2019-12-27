/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.mock.sql;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class MockDriver implements Driver {

	private Connection connection;
    private Logger logger;
    
    static {
        try {
            DriverManager.registerDriver(new MockDriver());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public MockDriver() {
        this.connection = new MockConnection();
    }

    public void setConnection(Connection connection) {
    	this.connection = connection;
    }
    
    public boolean acceptsURL(String url) throws SQLException {
        return true;
    }

    public Connection connect(String url, Properties info) throws SQLException {
        return connection;
    }

    public int getMajorVersion() {
        return 1;
    }

    public int getMinorVersion() {
        return 0;
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
            throws SQLException {
        return new DriverPropertyInfo[0];
    }

    public boolean jdbcCompliant() {
        return true;
    }

	/* @since 1.7 */
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		if (logger == null) {
			throw new SQLFeatureNotSupportedException("This method is not Supported.");
		}
		return logger;
	}
	
	/**
	 * (original method)
	 * @param logger
	 */
	public void setParentLogger(Logger logger) {
		this.logger = logger;
	}
}