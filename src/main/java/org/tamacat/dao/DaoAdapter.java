/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.io.InputStream;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;

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

	public String param(Column column, Condition condition, String... values) {
		return delegate.param(column, condition, values);
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

	public int create(T data) {
		return delegate.executeUpdate(getInsertSQL(data));
	}

	public int update(T data) {
		return delegate.executeUpdate(getUpdateSQL(data));
	}

	public int delete(T data) {
		return delegate.executeUpdate(getDeleteSQL(data));
	}

	protected ResultSet executeQuery(String sql) throws DaoException {
		return delegate.executeQuery(sql);
	}

	protected int executeUpdate(String sql) throws DaoException {
		return delegate.executeUpdate(sql);
	}

	protected int executeUpdate(String sql, int index, InputStream in) throws DaoException {
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
