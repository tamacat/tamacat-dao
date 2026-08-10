/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.tamacat.dao.Search.Conditions;
import org.tamacat.dao.event.DaoEvent;
import org.tamacat.dao.event.DaoExecuteHandler;
import org.tamacat.dao.event.DaoTransactionHandler;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.impl.NoneDaoExecuteHandler;
import org.tamacat.dao.impl.NoneDaoTransactionHandler;
import org.tamacat.dao.impl.DaoEventImpl;
import org.tamacat.dao.impl.QueryImpl;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.orm.MapBasedORMappingBean;
import org.tamacat.dao.orm.ORMapper;
import org.tamacat.dao.orm.ORMappingSupport;
import org.tamacat.dao.util.BlobUtils;
import org.tamacat.sql.BindSqlBuilder;
import org.tamacat.sql.DBAccessManager;
import org.tamacat.sql.DBUtils;
import org.tamacat.sql.IllegalTransactionStateException;
import org.tamacat.sql.ResultSetHandler;
import org.tamacat.sql.SQLParser;
import org.tamacat.sql.TransactionStateManager;
import org.tamacat.util.ClassUtils;

public class Dao<T extends ORMappingSupport<T>> implements AutoCloseable {

	protected static final String DEFAULT_DBNAME = "default";

	protected static final DaoTransactionHandler DEFAULT_TRANSACTION_HANDLER = new NoneDaoTransactionHandler();

	protected static final DaoExecuteHandler DEFAULT_EXECUTE_HANDLER = new NoneDaoExecuteHandler();

	protected Class<?> callerDao;// = getClass();
	protected String dbname = DEFAULT_DBNAME;

	protected DBAccessManager dbm;
	protected ORMapper<T> orm;
	protected SQLParser parser = new SQLParser();
	/**
	 * @since 2.0
	 */
	protected BindSqlBuilder bindSqlBuilder = new BindSqlBuilder();

	protected DaoExecuteHandler executeHandler;
	protected DaoTransactionHandler transactionHandler;
	protected DaoEvent event;
	protected long hitCount;
	protected boolean useHitCount = true;

	public Dao() {
		orm = new ORMapper<>();
	}

	public Dao(DBAccessManager dbm) {
		this.dbm = dbm;
		orm = new ORMapper<>();
	}

	public void setORMapper(ORMapper<T> orm) {
		this.orm = orm;
	}

	public void useHitCount(boolean use) {
		this.useHitCount = use;
	}

	public long getHitCount() {
		return hitCount;
	}

	public void setHitCount(long hitCount) {
		this.hitCount = hitCount;
	}

	public List<String> getExecutedQuery() {
		return dbm.getExecutedQuery();
	}
	
	protected DaoExecuteHandler getExecuteHandler() {
		if (executeHandler == null)
			executeHandler = DEFAULT_EXECUTE_HANDLER;
		return executeHandler;
	}

	public void setExecuteHandler(DaoExecuteHandler executeHandler) {
		this.executeHandler = executeHandler;
	}

	protected DaoTransactionHandler getTransactionHandler() {
		if (transactionHandler == null)
			transactionHandler = DEFAULT_TRANSACTION_HANDLER;
		return transactionHandler;
	}

	public void setTransactionHandler(DaoTransactionHandler transactionHandler) {
		this.transactionHandler = transactionHandler;
	}

	@SuppressWarnings("unchecked")
	public void setDatabase(String dbname) {
		this.dbname = dbname;
		dbm = DBAccessManager.getInstance(dbname);
		Type[] types = ClassUtils.getParameterizedTypes(getCallerDaoClass());
		if (types.length > 0) {
			for (Type type : types) {
				Object obj = ClassUtils.newInstance(ClassUtils.forName(type.getTypeName()));
				if (obj != null && MapBasedORMappingBean.class.isInstance(obj)) {
					orm.setPrototype((Class<T>) type);
					break;
				}
			}
		}
	}

	public String getDatabase() {
		return dbname;
	}
	
	public void setPrototype(Class<T> prototype) {
		orm.setPrototype(prototype);
	}

	public DBAccessManager getDBAccessManager() {
		if (dbm == null) {
			dbm = DBAccessManager.getInstance(dbname);
		}
		return dbm;
	}

	public String param(Column column, Conditions condition, String... values) {
		return parser.value(column, condition, values);
	}

	/**
	 * Bind-path equivalent of {@link #param(Column, Conditions, String...)}. Named
	 * differently because Java does not allow overloads that differ only by
	 * return type.
	 * @since 2.0
	 */
	public Param prepare(Column column, Conditions condition, String... values) {
		return bindSqlBuilder.value(column, condition, values);
	}

	public Search createSearch() {
		return new Search();
	}

	public Sort createSort() {
		return new Sort();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Query<T> createQuery() {
		return new QueryImpl();
	}

	public T search(Query<T> query) {
		try {
			return executeQuery(query.getSelectPreparedSql(), rs -> {
				if (rs.next()) {
					return mapping(query.getSelectColumns(), rs);
				} else {
					return orm.getMappedObject();
				}
			});
		} catch (DaoException e) {
			// BR-16: handleException always throws; this call never returns.
			handleException(e.getCause() != null ? e.getCause() : e);
			return null;
		}
	}

	protected T mapping(Collection<Column> columns, ResultSet rs) {
		return orm.mapping(columns, rs);
	}

	public Collection<T> searchList(Query<T> query) {
		return searchList(query, -1, -1);
	}

	public Collection<T> searchList(Query<T> query, int start, int max) {
		Collection<Column> columns = query.getSelectColumns();
		try {
			return executeQuery(query.getSelectPreparedSql(), rs -> {
				ArrayList<T> list = new ArrayList<>();
				if (start > 0) {
					for (int i = 1; i < start; i++)
						rs.next();
				}
				int add = 0;
				while (rs.next()) {
					T o = mapping(columns, rs);
					list.add(o);
					add++;
					if (max > 0 && add >= max)
						break;
				}
				return list;
			});
		} catch (DaoException e) {
			// BR-16: handleException always throws; this call never returns.
			handleException(e.getCause() != null ? e.getCause() : e);
			return null;
		}
	}

	/**
	 * The exception is appropriately processed, the exception object is
	 * converted, and it throws out.
	 * 
	 * @param cause
	 * @throws DaoException
	 */
	public void handleException(Throwable cause) {
		DaoEvent event = createDaoEvent();
		getTransactionHandler().handleException(event, cause);
		throw new DaoException(cause);
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
	 * Bind-path equivalent of {@link #getInsertSQL(ORMappingSupport)}. Distinct from
	 * {@link org.tamacat.dao.impl.QueryImpl}'s method of the same name - this is
	 * {@code Dao}'s own extension point, overridden the same way subclasses currently
	 * override {@link #getInsertSQL(ORMappingSupport)} (e.g. {@code FileDataDao}). The
	 * default wraps the legacy override via {@link PreparedSql#ofLiteral(String)}
	 * (ADR-004) - a subclass that only overrides {@link #getInsertSQL(ORMappingSupport)}
	 * keeps generating the same literal SQL, unchanged, without recompiling (NFR-3).
	 * @since 2.0
	 */
	protected PreparedSql getInsertPreparedSql(T data) {
		return PreparedSql.ofLiteral(getInsertSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getUpdateSQL(ORMappingSupport)}. See
	 * {@link #getInsertPreparedSql(ORMappingSupport)} for the default's fallback
	 * behavior.
	 * @since 2.0
	 */
	protected PreparedSql getUpdatePreparedSql(T data) {
		return PreparedSql.ofLiteral(getUpdateSQL(data));
	}

	/**
	 * Bind-path equivalent of {@link #getDeleteSQL(ORMappingSupport)}. See
	 * {@link #getInsertPreparedSql(ORMappingSupport)} for the default's fallback
	 * behavior.
	 * @since 2.0
	 */
	protected PreparedSql getDeletePreparedSql(T data) {
		return PreparedSql.ofLiteral(getDeleteSQL(data));
	}

	public int create(T data) {
		return executeUpdate(getInsertPreparedSql(data));
	}

	public int update(T data) {
		return executeUpdate(getUpdatePreparedSql(data));
	}

	public int delete(T data) {
		return executeUpdate(getDeletePreparedSql(data));
	}

	//v1.4 changed protected -> public
	public Class<?> getCallerDaoClass() {
		return callerDao != null ? callerDao : getClass();
	}

	protected DaoEvent createDaoEvent(String sql) {
		return new DaoEventImpl(getCallerDaoClass(), sql);
	}

	protected DaoEvent createDaoEvent() {
		return new DaoEventImpl(getCallerDaoClass());
	}

	protected ResultSet executeQuery(String sql) throws DaoException {
		DaoEvent event = createDaoEvent(sql);
		getExecuteHandler().handleBeforeExecuteQuery(event);
		ResultSet rs = dbm.executeQuery(sql);
		getExecuteHandler().handleAfterExecuteQuery(event);
		return rs;
	}

	/**
	 * Bind-path equivalent of {@link #executeQuery(String)}. Unlike the legacy
	 * method, the {@code ResultSet} is not returned - it is closed by
	 * {@link DBAccessManager} before this method returns, so {@code handler} must
	 * consume it fully during the callback.
	 * @since 2.0
	 */
	protected <R> R executeQuery(PreparedSql sql, ResultSetHandler<R> handler) throws DaoException {
		DaoEvent event = createDaoEvent(sql.getSql());
		getExecuteHandler().handleBeforeExecuteQuery(event);
		R result = dbm.executeQuery(sql, handler);
		getExecuteHandler().handleAfterExecuteQuery(event);
		return result;
	}

	protected int executeUpdate(String sql) throws DaoException {
		DaoEvent event = createDaoEvent(sql);
		getExecuteHandler().handleBeforeExecuteUpdate(event);
		int result = dbm.executeUpdate(sql);
		TransactionStateManager.getInstance().executed();
		event.setResult(result);
		return getExecuteHandler().handleAfterExecuteUpdate(event);
	}

	protected int executeUpdate(String sql, int index, InputStream in) throws DaoException {
		DaoEvent event = createDaoEvent(sql);
		getExecuteHandler().handleBeforeExecuteUpdate(event);
		PreparedStatement stmt = dbm.preparedStatement(sql);
		int result = BlobUtils.executeUpdate(stmt, index, in);
		TransactionStateManager.getInstance().executed();
		event.setResult(result);
		return getExecuteHandler().handleAfterExecuteUpdate(event);
	}

	/**
	 * Bind-path equivalent of {@link #executeUpdate(String)}. Used by the default
	 * {@link #create(ORMappingSupport)} / {@link #update(ORMappingSupport)} /
	 * {@link #delete(ORMappingSupport)}. Goes through the same 4 steps as the other
	 * {@code executeUpdate} variants - {@link DaoEvent} creation, the
	 * {@link DaoExecuteHandler} before/after hooks, {@link TransactionStateManager}, and
	 * {@code event.setResult} - none of which are skipped for the bind path (BR-9a,
	 * BR-16).
	 * @since 2.0
	 */
	protected int executeUpdate(PreparedSql sql) throws DaoException {
		DaoEvent event = createDaoEvent(sql.getSql());
		getExecuteHandler().handleBeforeExecuteUpdate(event);
		int result = dbm.executeUpdate(sql);
		TransactionStateManager.getInstance().executed();
		event.setResult(result);
		return getExecuteHandler().handleAfterExecuteUpdate(event);
	}

	/**
	 * Bind-path equivalent of {@link #executeUpdate(String, int, InputStream)}, for BLOB
	 * writes. Not used by the default {@link #create(ORMappingSupport)} /
	 * {@link #update(ORMappingSupport)} - BLOB is never auto-detected (BR-11, BI-3). A
	 * subclass that writes a BLOB column overrides {@code create}/{@code update}, gets a
	 * {@link PreparedSql} from {@link #getInsertPreparedSql(ORMappingSupport)} /
	 * {@link #getUpdatePreparedSql(ORMappingSupport)}, obtains the bind position via
	 * {@link PreparedSql#getBindIndexOf(org.tamacat.dao.meta.DataType, int)} (or
	 * {@link Query#getBlobIndex()}), and calls this method directly with the data's
	 * {@link InputStream}. See {@code MIGRATION.md} for a full example.
	 * @since 2.0
	 */
	protected int executeUpdate(PreparedSql sql, int index, InputStream in) throws DaoException {
		DaoEvent event = createDaoEvent(sql.getSql());
		getExecuteHandler().handleBeforeExecuteUpdate(event);
		int result = dbm.executeUpdate(sql, index, in);
		TransactionStateManager.getInstance().executed();
		event.setResult(result);
		return getExecuteHandler().handleAfterExecuteUpdate(event);
	}

	protected void commit() throws DaoException {
		DaoEvent event = createDaoEvent();
		getTransactionHandler().handleBeforeCommit(event);
		dbm.commit();
		TransactionStateManager.getInstance().commit();
		getTransactionHandler().handleAfterCommit(event);
	}

	protected void rollback() throws DaoException {
		DaoEvent event = createDaoEvent();
		getTransactionHandler().handleBeforeRollback(event);
		dbm.rollback();
		TransactionStateManager.getInstance().rollback();
		getTransactionHandler().handleAfterRollback(event);
	}

	protected boolean isTransactionStarted() {
		return TransactionStateManager.getInstance().isTransactionStarted();
	}

	protected void startTransaction() throws DaoException {
		DBUtils.setAutoCommitFalse(dbm);
		if (isTransactionStarted() == false) {
			TransactionStateManager.getInstance().begin();
			event = createDaoEvent();
			getTransactionHandler().handleTransantionStart(event);
		}
	}

	protected void endTransaction() throws DaoException {
		if (isTransactionStarted() == false) {
			throw new IllegalTransactionStateException("Transaction is not started.");
		}
		if (TransactionStateManager.getInstance().isNotCommited()) {
			rollback();
			abortTransaction();
		}
		DBUtils.setAutoCommitTrue(dbm);
		TransactionStateManager.getInstance().end();
		DaoEvent event = createDaoEvent();
		getTransactionHandler().handleTransantionEnd(event);
	}

	protected void abortTransaction() {
		throw new IllegalTransactionStateException("Transaction is not commit or rollback. Force execute rollback.");
	}

	protected void release() {
		dbm.release();
		DaoEvent event = createDaoEvent();
		getTransactionHandler().handleRelease(event);
	}

	@Override
	public void close() {
		release();
	}
}
