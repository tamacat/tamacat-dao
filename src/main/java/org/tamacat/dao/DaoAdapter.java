/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.io.InputStream;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;

import org.tamacat.dao.Search.Conditions;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.impl.LoggingDaoExecuterHandler;
import org.tamacat.dao.impl.LoggingDaoTransactionHandler;
import org.tamacat.dao.impl.MySQLDao;
import org.tamacat.dao.impl.OracleDao;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.orm.ORMapper;
import org.tamacat.dao.orm.ORMappingSupport;
import org.tamacat.di.DI;
import org.tamacat.di.DIContainer;
import org.tamacat.sql.DBAccessManager;
import org.tamacat.sql.JdbcConfig;
import org.tamacat.sql.ResultSetHandler;

public class DaoAdapter<T extends ORMappingSupport<T>> implements AutoCloseable {

	protected DIContainer di;

	protected Dao<T> delegate;
	
	protected DaoAdapter() {
		this("default");
	}

	/**
	 * @param dbname
	 * @since 1.4-20160408
	 */
	protected DaoAdapter(String dbname) {
		setDatabase(dbname);
	}
	
	/**
	 * @param di
	 * @param dbname
	 * @since 1.4-20160408
	 */
	protected DaoAdapter(DIContainer di, String dbname) {
		setDatabase(di, dbname);
	}
	
	/**
	 * use setDatabase(String)
	 * @param di
	 * @since 1.4-20160408
	 */
	protected DaoAdapter(DIContainer di) {
		this.di = di;
	}
	
	protected DaoAdapter(Dao<T> delegate) {
		setDao(delegate);
	}

	public String getDatabase() {
		return delegate.getDatabase();
	}
	
	public void setDatabase(String dbname) {
		if (di == null) {
			di = DI.configure("db.xml");
		}
		setDatabase(di, dbname);
	}
	
	/**
	 * @param dbname
	 * @since 1.4-20160408
	 */
	public void setDatabase(DIContainer di, String dbname) {
		if (di == null) {
			throw new DaoException("Please set a database configuration.");
		}
		JdbcConfig config = di.getBean(dbname, JdbcConfig.class);
		if (config.getDriverClass().toLowerCase().indexOf("mysql") >= 0) {
			delegate = new MySQLDao<>();
		} else if (config.getDriverClass().toLowerCase().indexOf("oracle") >= 0) {
			delegate = new OracleDao<>();
		} else {
			delegate = new Dao<>();
		}
		delegate.callerDao = getClass();
		delegate.setDatabase(dbname);
		setDao(delegate);
	}
	
	public void setORMapper(ORMapper<T> orm) {
		delegate.setORMapper(orm);
	}

	public void setDao(Dao<T> delegate) {
		this.delegate = delegate;
		delegate.setExecuteHandler(new LoggingDaoExecuterHandler());
		delegate.setTransactionHandler(new LoggingDaoTransactionHandler());
	}

	protected void setDBAccessManager(DBAccessManager dbm) {
		delegate.dbm = dbm;
	}

	public List<String> getExecutedQuery() {
		return delegate.getExecutedQuery();
	}
	
	public DBAccessManager getDBAccessManager() {
		return delegate.getDBAccessManager();
	}

	public String param(Column column, Conditions condition, String... values) {
		return delegate.param(column, condition, values);
	}

	/**
	 * @since 2.0
	 */
	public Param prepare(Column column, Conditions condition, String... values) {
		return delegate.prepare(column, condition, values);
	}

	public Query<T> createQuery() {
		return delegate.createQuery();
	}

	public Search createSearch() {
		return delegate.createSearch();
	}

	public Sort createSort() {
		return delegate.createSort();
	}

	public T search(Query<T> query) {
		return delegate.search(query);
	}

	public Collection<T> searchList(Query<T> query, int start, int max) {
		return delegate.searchList(query, start, max);
	}

	public Collection<T> searchList(Query<T> query) {
		return delegate.searchList(query);
	}

	public void handleException(Throwable cause) throws DaoException {
		delegate.handleException(cause);
	}

	protected String getInsertSQL(T data) {
		throw new RuntimeException(new NoSuchMethodException());
	}

	protected String getUpdateSQL(T data) {
		throw new RuntimeException(new NoSuchMethodException());
	}

	protected String getDeleteSQL(T data) {
		throw new RuntimeException(new NoSuchMethodException());
	}

	/**
	 * Bind-path equivalent of {@link #getInsertSQL(ORMappingSupport)}. {@code DaoAdapter}
	 * does not extend {@link Dao} (it holds a {@code delegate: Dao<T>} field instead), so
	 * this extension point is added independently of {@link Dao#getInsertPreparedSql},
	 * wrapping {@code DaoAdapter}'s own {@link #getInsertSQL(ORMappingSupport)} - the same
	 * asymmetry the existing {@link #create(ORMappingSupport)} already has (SQL from
	 * {@code DaoAdapter} itself, execution from {@code delegate}) (BR-17).
	 * @since 2.0
	 */
	protected PreparedSql getInsertPreparedSql(T data) {
		return PreparedSql.ofLiteral(getInsertSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getUpdateSQL(ORMappingSupport)}. See
	 * {@link #getInsertPreparedSql(ORMappingSupport)}.
	 * @since 2.0
	 */
	protected PreparedSql getUpdatePreparedSql(T data) {
		return PreparedSql.ofLiteral(getUpdateSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getDeleteSQL(ORMappingSupport)}. See
	 * {@link #getInsertPreparedSql(ORMappingSupport)}.
	 * @since 2.0
	 */
	protected PreparedSql getDeletePreparedSql(T data) {
		return PreparedSql.ofLiteral(getDeleteSQL(data));
	}

	public int create(T data) {
		return delegate.executeUpdate(getInsertPreparedSql(data));
	}

	public int update(T data) {
		return delegate.executeUpdate(getUpdatePreparedSql(data));
	}

	public int delete(T data) {
		return delegate.executeUpdate(getDeletePreparedSql(data));
	}

	protected ResultSet executeQuery(String sql) throws DaoException {
		return delegate.executeQuery(sql);
	}

	/**
	 * @since 2.0
	 */
	protected <R> R executeQuery(PreparedSql sql, ResultSetHandler<R> handler) throws DaoException {
		return delegate.executeQuery(sql, handler);
	}

	protected int executeUpdate(String sql) throws DaoException {
		return delegate.executeUpdate(sql);
	}

	protected int executeUpdate(String sql, int index, InputStream in) throws DaoException {
		return delegate.executeUpdate(sql, index, in);
	}

	/**
	 * Bind-path equivalent of {@link #executeUpdate(String)}. Forwards to
	 * {@code delegate}, independently of {@link Dao#executeUpdate(PreparedSql)} since
	 * {@code DaoAdapter} does not extend {@link Dao} (BR-17).
	 * @since 2.0
	 */
	protected int executeUpdate(PreparedSql sql) throws DaoException {
		return delegate.executeUpdate(sql);
	}

	/**
	 * Bind-path equivalent of {@link #executeUpdate(String, int, InputStream)}, for BLOB
	 * writes. Forwards to {@code delegate}. The override point for a BLOB-writing
	 * subclass in this repository is here (all real DAOs extend {@code DaoAdapter}) -
	 * see {@code MIGRATION.md}.
	 * @since 2.0
	 */
	protected int executeUpdate(PreparedSql sql, int index, InputStream in) throws DaoException {
		return delegate.executeUpdate(sql, index, in);
	}

	public void commit() {
		delegate.commit();
	}

	public void rollback() {
		delegate.rollback();
	}

	/**
	 * @since 1.3
	 */
	public boolean isTransactionStarted() {
		return delegate.isTransactionStarted();
	}

	public void startTransaction() {
		delegate.startTransaction();
	}

	public void endTransaction() {
		delegate.endTransaction();
	}

	public void release() {
		delegate.release();
	}

	public long getHitCount() {
		return delegate.getHitCount();
	}

	public void useHitCount(boolean use) {
		delegate.useHitCount(use);
	}

	/**
	 * @since 1.3
	 */
	public String getTimestampString() {
		return createQuery().getTimestampString();
	}
	
	@Override
	public void close() {
		release();
	}
}
