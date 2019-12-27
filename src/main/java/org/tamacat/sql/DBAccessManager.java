/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.tamacat.dao.exception.DaoException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.sql.LifecycleSupport;

public final class DBAccessManager implements LifecycleSupport {

	static final Log LOG = LogFactory.getLog(DBAccessManager.class);

	private static final HashMap<String, DBAccessManager> MANAGER = new HashMap<>();

	public static synchronized DBAccessManager getInstance(String name) {
		DBAccessManager dm = MANAGER.get(name);
		if (dm == null) {
			dm = new DBAccessManager(name);
			MANAGER.put(name, dm);
		}
		return dm;
	}

	protected ThreadLocal<List<String>> executedQuery = new ThreadLocal<>();
	private ThreadLocal<Boolean> running = new ThreadLocal<>();
	private ThreadLocal<Connection> con = new ThreadLocal<>();
	private ThreadLocal<Statement> stmt = new ThreadLocal<>();
	private String name;

	private DBAccessManager(String name) {
		this.name = name;
		if (running.get() == null) { // initialize running flag.
			running.set(false);
		}
		executedQuery.set(new ArrayList<>());
	}

	synchronized Connection getConnection() {
		Connection c = con.get();
		try {
			if (c == null || c.isClosed()) {
				c = ConnectionManager.getInstance(name).getObject();
				if (c != null) {
					con.set(c);
					start();
				}
			}
		} catch (SQLException e) {
			throw new DaoException(e);
		}
		return c;
	}

	synchronized Statement getStatement() {
		Statement s = stmt.get();
		try {
			if (s == null || s.isClosed()) {
				s = getConnection().createStatement();
				if (s != null) {
					stmt.set(s);
				}
			}
		} catch (SQLException e) {
			throw new DaoException(e);
		}
		return s;
	}

	public Statement createStatement() {
		try {
			return getConnection().createStatement();
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public PreparedStatement preparedStatement(String sql) {
		try {
			getExecutedQuery().add(sql);
			return getConnection().prepareStatement(sql);
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public ResultSet executeQuery(String sql) {
		try {
			getExecutedQuery().add(sql);
			return getStatement().executeQuery(sql);
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public int executeUpdate(String sql) {
		try {
			getExecutedQuery().add(sql);
			return getStatement().executeUpdate(sql);
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public void close(ResultSet rs) {
		try {
			if (rs != null)
				rs.close();
		} catch (SQLException e) {
			LOG.warn(e.getMessage());
		}
	}

	public void close(Statement st) {
		try {
			if (st != null)
				st.close();
		} catch (SQLException e) {
			LOG.warn(e.getMessage());
		}
	}

	public void setAutoCommit(boolean autoCommit) {
		try {
			if (getAutoCommit()) {
				getConnection().setAutoCommit(autoCommit);
			}
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public boolean getAutoCommit() {
		try {
			return getConnection().getAutoCommit();
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public void commit() {
		try {
			getConnection().commit();
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public void rollback() {
		try {
			getConnection().rollback();
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	public synchronized void release() {
		if (isRunning()) {
			Statement st = stmt.get();
			stmt.remove();
			close(st);
			Connection c = con.get();
			con.remove();
			ConnectionManager.getInstance(name).free(c);
			running.remove();
			MANAGER.remove(name);
			LOG.trace("released.");
		}
	}

	public List<String> getExecutedQuery() {
		List<String> query = executedQuery.get();
		if (query == null) {
			query = new ArrayList<>();
			executedQuery.set(query);
		}
		return query;
	}

	@Override
	public boolean isRunning() {
		return running != null && running.get() != null && running.get();
	}

	@Override
	public void start() {
		running.set(true);
	}

	@Override
	public void stop() {
		release();
		running.set(false);
	}

	public static void shutdown() {
		synchronized (MANAGER) {
			for (DBAccessManager dba : MANAGER.values()) {
				dba.executedQuery.remove();
				dba.stmt.remove();
				dba.con.remove();
				dba.running.remove();
			}
		}
	}
}
