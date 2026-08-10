/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.tamacat.dao.PreparedSql;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

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
	protected ThreadLocal<List<ExecutedStatement>> executedStatements = new ThreadLocal<>();
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

	/**
	 * Executes {@code sql}, applying its bind values, and passes the resulting
	 * {@link ResultSet} to {@code handler}. The {@code PreparedStatement} and
	 * {@code ResultSet} are both closed (via try-with-resources) before this method
	 * returns - {@code handler} must not retain or return the {@code ResultSet}.
	 * @since 2.0
	 */
	public <R> R executeQuery(PreparedSql sql, ResultSetHandler<R> handler) {
		record(sql);
		checkBindable(sql);
		try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
			PreparedStatementBinder.bind(ps, sql.getValues());
			try (ResultSet rs = ps.executeQuery()) {
				return handler.handle(rs);
			}
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	/**
	 * Executes {@code sql} as an update, applying its bind values.
	 * @since 2.0
	 */
	public int executeUpdate(PreparedSql sql) {
		record(sql);
		checkBindable(sql);
		try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
			PreparedStatementBinder.bind(ps, sql.getValues());
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	/**
	 * Executes {@code sql} as an update, applying its bind values, then binds
	 * {@code in} as a binary stream at {@code blobIndex} (1-based) - overwriting the
	 * {@code setNull} that {@link PreparedStatementBinder#bind} applied for the
	 * {@code DataType.OBJECT} placeholder at that position. The overwrite relies on the
	 * JDBC contract that the last {@code set*} call at a given parameter position wins
	 * (business-rules.md BR-10).
	 * @param sql the checked {@link PreparedSql} (its OBJECT placeholder already carries
	 *        a NULL {@link org.tamacat.dao.BindValue})
	 * @param blobIndex the 1-based bind position of the BLOB column, typically obtained
	 *        via {@link PreparedSql#getBindIndexOf(org.tamacat.dao.meta.DataType, int)}
	 * @param in the binary content to bind
	 * @since 2.0
	 */
	public int executeUpdate(PreparedSql sql, int blobIndex, InputStream in) {
		record(sql);
		checkBindable(sql);
		try (PreparedStatement ps = getConnection().prepareStatement(sql.getSql())) {
			PreparedStatementBinder.bind(ps, sql.getValues());
			ps.setBinaryStream(blobIndex, in);
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new DaoException(e);
		}
	}

	private void checkBindable(PreparedSql sql) {
		if (!sql.hasUnboundPlaceholders()) {
			return;
		}
		if (sql.getPlaceholderCount() < 0) {
			throw new DaoException(
					"Cannot verify placeholders: unterminated quote in SQL text. sql=[" + sql.getSql() + "]");
		}
		throw new DaoException(
				"Unbound placeholder(s): " + sql.getPlaceholderCount() + " placeholder(s) but "
				+ sql.getValues().size() + " value(s). For a BLOB update, use "
				+ "Dao#executeUpdate(String,int,InputStream) or override getUpdatePreparedSql(T). "
				+ "sql=[" + sql.getSql() + "]");
	}

	private void record(PreparedSql sql) {
		getExecutedQuery().add(sql.getSql());
		getExecutedStatements().add(new ExecutedStatement(sql.getSql(), sql.getValues()));
	}

	/**
	 * @return the {@link ExecutedStatement} records (SQL text + bind values) for the
	 *         current thread, in execution order
	 * @since 2.0
	 */
	public List<ExecutedStatement> getExecutedStatements() {
		List<ExecutedStatement> list = executedStatements.get();
		if (list == null) {
			list = new ArrayList<>();
			executedStatements.set(list);
		}
		return list;
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
				dba.executedStatements.remove();
				dba.stmt.remove();
				dba.con.remove();
				dba.running.remove();
			}
		}
	}
}
