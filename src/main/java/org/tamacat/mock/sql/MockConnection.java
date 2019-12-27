/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.mock.sql;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;

public class MockConnection implements Connection {

    static final Log LOG = LogFactory.getLog(MockConnection.class);

    String url;
    Properties info;
    String schema;
    
    public MockConnection(String url, Properties info) {
    	this.url = url;
    	this.info = info;
    }
    
    public MockConnection() {
    }

    public void clearWarnings() throws SQLException {
    }

    public void close() throws SQLException {
        isClosed = true;
        LOG.info("close()");
    }

    public void commit() throws SQLException {
        LOG.info("commit()");
    }

    public Array createArrayOf(String typeName, Object[] elements)
            throws SQLException {
        return null;
    }

    public Blob createBlob() throws SQLException {
        return null;
    }

    public Clob createClob() throws SQLException {
        return null;
    }

    public NClob createNClob() throws SQLException {
        return null;
    }

    public SQLXML createSQLXML() throws SQLException {
        return null;
    }

    public Statement createStatement() throws SQLException {
        return new MockStatement(this);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return new MockStatement(this);
    }

    public Statement createStatement(int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return new MockStatement(this);
    }

    public Struct createStruct(String typeName, Object[] attributes)
            throws SQLException {
        return null;
    }

    private boolean autoCommit = true;
    public boolean getAutoCommit() throws SQLException {
        return autoCommit;
    }

    public String getCatalog() throws SQLException {
        return null;
    }

    public Properties getClientInfo() throws SQLException {
        return null;
    }

    public String getClientInfo(String name) throws SQLException {
        return null;
    }

    public int getHoldability() throws SQLException {
        return 0;
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return null;
    }

    int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
    public int getTransactionIsolation() throws SQLException {
        return transactionIsolation;
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return null;
    }

    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    boolean isClosed;
    public boolean isClosed() throws SQLException {
        return isClosed;
    }

    public boolean isReadOnly() throws SQLException {
        return false;
    }

    public boolean isValid(int timeout) throws SQLException {
        return false;
    }

    public String nativeSQL(String sql) throws SQLException {
        return null;
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return null;
    }

    public CallableStatement prepareCall(String sql, int resultSetType,
            int resultSetConcurrency) throws SQLException {
        return null;
    }

    public CallableStatement prepareCall(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return null;
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new MockPreparedStatement(this);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
            throws SQLException {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
            throws SQLException {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames)
            throws SQLException {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
            int resultSetConcurrency) throws SQLException {
        return null;
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return null;
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    }

    public void rollback() throws SQLException {
        LOG.info("rollback()");
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        LOG.info("rollback()");
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.autoCommit = autoCommit;
    }

    public void setCatalog(String catalog) throws SQLException {
    }

    public void setClientInfo(Properties properties)
            throws SQLClientInfoException {
    }

    public void setClientInfo(String name, String value)
            throws SQLClientInfoException {
    }

    public void setHoldability(int holdability) throws SQLException {
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
    }

    public Savepoint setSavepoint() throws SQLException {
        return null;
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        return null;
    }

    public void setTransactionIsolation(int level) throws SQLException {
        this.transactionIsolation = level;
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

  //@Override (1.7)
	/** @since 1.7 */
	public void setSchema(String schema) throws SQLException {
		this.schema = schema;
	}

	//@Override (1.7)
	/** @since 1.7 */
	public String getSchema() throws SQLException {
		return schema;
	}

	//@Override (1.7)
	/** @since 1.7 */
	public void abort(Executor executor) throws SQLException {
		
	}

	//@Override (1.7)
	/** @since 1.7 */
	public void setNetworkTimeout(Executor executor, int milliseconds)
			throws SQLException {
		
	}

	//@Override (1.7)
	/** @since 1.7 */
	public int getNetworkTimeout() throws SQLException {
		return 0;
	}
}
