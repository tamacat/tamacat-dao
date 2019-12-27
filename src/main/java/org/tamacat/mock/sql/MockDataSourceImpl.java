/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.mock.sql;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.sql.DataSource;

public class MockDataSourceImpl implements DataSource, Referenceable {

	private PrintWriter out;
	Logger logger;

	public Connection getConnection() throws SQLException {
		return new MockConnection();
	}

	public Connection getConnection(String username, String password)
			throws SQLException {
		return new MockConnection();
	}

	public PrintWriter getLogWriter() throws SQLException {
		return out;
	}

	public int getLoginTimeout() throws SQLException {
		return 0;
	}

	public void setLogWriter(PrintWriter out) throws SQLException {
		this.out = out;
	}

	public void setLoginTimeout(int seconds) throws SQLException {
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return false;
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return null;
	}

	public Reference getReference() throws NamingException {
		return new Reference(getClass().getName());
	}

	//@Override (1.7)
	/** @since 1.7 */
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		if (logger == null) throw new SQLFeatureNotSupportedException();
		return logger;
	}

	public void setParentLogger(Logger logger) {
		this.logger = logger;
	}

}
