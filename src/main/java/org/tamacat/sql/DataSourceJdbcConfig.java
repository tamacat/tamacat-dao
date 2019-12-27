/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.tamacat.dao.exception.DaoException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.pool.ObjectActivateException;

public class DataSourceJdbcConfig implements JdbcConfig {

	static final Log LOG = LogFactory.getLog(DataSourceJdbcConfig.class);

	private String activateSQL;
	private InitialContext ic;
	private String dataSourceName;
	private int maxPools;
	private int initPools;
	
	DataSource ds = null;

	@Override
	public Connection getConnection() {
		try {
			return getDataSource().getConnection();
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
			throw new ObjectActivateException(e);
		}
	}

	public synchronized void setDataSourceName(String dataSourceName) {
		this.dataSourceName = dataSourceName;
	}

	private synchronized DataSource getDataSource() {
		if (ds == null) {
			try {
				ic = new InitialContext();
				LOG.trace("DataSource: " + dataSourceName);
				ds = (DataSource) ic.lookup(dataSourceName);
			} catch (NamingException e) {
				throw new DaoException(e);
			}
		}
		return ds;
	}

	@Override
	/**
	 * null is always returned.
	 */
	public String getDriverClass() {
		return null;
	}
	
	@Override
	/**
	 * null is always returned.
	 */
	public String getUrl() {
		return null;
	}

	@Override
	public int getMaxPools() {
		return maxPools;
	}

	public void setMaxPools(int maxPools) {
		this.maxPools = maxPools;
	}
	
	@Override
	public int getInitPools() {
		return initPools;
	}
	
	public void setInitPools(int initPools) {
		this.initPools = initPools;
	}
}
