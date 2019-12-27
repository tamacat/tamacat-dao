/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.tamacat.dao.exception.DaoException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.pool.ObjectActivateException;
import org.tamacat.util.ClassUtils;

public class DriverManagerJdbcConfig implements JdbcConfig {

	static final Log LOG = LogFactory.getLog(DriverManagerJdbcConfig.class);

	private String driverClass;
	private String url;
	private String user;
	private String password;

	private int initPools;
	private int maxPools;

	private String activateSQL;
	private Class<?> driver;

	@Override
	public Connection getConnection() {
		if (driver == null) loadDriver();
		try {
			String url = getUrl();
			LOG.trace("JDBC URL: " + url);
			return DriverManager.getConnection(url, getUser(), getPassword());
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	@Override
	public void activate(Connection con) throws ObjectActivateException {
		if (con == null) throw new ObjectActivateException();
		try {
			con.setAutoCommit(true);
			if (activateSQL != null) {
				try (Statement stmt = con.createStatement()) {
					ResultSet rs = stmt.executeQuery(activateSQL);
					if (LOG.isDebugEnabled()) {
						LOG.debug(activateSQL);
					}
					DBUtils.close(rs);
				}
			}
		} catch (SQLException e) {
			LOG.warn("activate error. " + e.getMessage());
			throw new ObjectActivateException(e);
		}
	}

	synchronized void loadDriver() {
		String driverClass = getDriverClass();
		if (LOG.isDebugEnabled()) {
			LOG.debug("Loading... [" + driverClass + "]");
		}
		driver = ClassUtils.forName(driverClass);
	}

	@Override
	public String getDriverClass() {
		return driverClass.trim();
	}

	public void setDriverClass(String driverClass) {
		this.driverClass = driverClass;
	}

	@Override
	public String getUrl() {
		return url.trim();
	}
	public void setUrl(String url) {
		this.url = url;
	}

	public String getUser() {
		return user;
	}
	
	public void setUser(String user) {
		this.user = user;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public int getInitPools() {
		return initPools;
	}
	
	public void setInitPools(int initPools) {
		this.initPools = initPools;
	}
	
	/**
	 * same setInitPools()
	 * @param initPools
	 * @since 1.3-220160325
	 */
	public void setMinPools(int initPools) {
		this.initPools = initPools;
	}
	
	public int getMaxPools() {
		return maxPools;
	}
	
	public void setMaxPools(int maxPools) {
		this.maxPools = maxPools;
	}

	public void setActivateSQL(String activateSQL) {
		this.activateSQL = activateSQL;
	}
}
