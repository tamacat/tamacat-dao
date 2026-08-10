/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.BindValue;
import org.tamacat.dao.PreparedSql;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.meta.DataType;
import org.tamacat.mock.sql.MockConnection;
import org.tamacat.mock.sql.MockPreparedStatement;

public class DBAccessManagerTest {

    DBAccessManager dbm;

    @Before
    public void setUp() throws Exception {
        dbm = DBAccessManager.getInstance("default");
    }

    @After
    public void tearDown() throws Exception {
        if (dbm != null) dbm.release();
    }

    @Test
    public void testGetConnection() throws Exception {
//        Connection con = dbm.getConnection();
//        assertNotNull(con);
//
//        Connection con2 = dbm.getConnection();
//        assertNotNull(con);
//        assertEquals(con, con2);

        dbm.executeQuery("select * from users");
    }

    @Test
    public void testExecuteQueryWithResultSetHandler() {
        PreparedSql sql = PreparedSql.of("select * from users where user_id=?",
                Collections.singletonList(BindValue.of(DataType.STRING, "admin")));
        String result = dbm.executeQuery(sql, rs -> "handled");
        assertEquals("handled", result);
    }

    @Test
    public void testExecuteQueryRecordsExecutedStatement() {
        PreparedSql sql = PreparedSql.of("select * from users where user_id=?",
                Collections.singletonList(BindValue.of(DataType.STRING, "admin")));
        dbm.executeQuery(sql, rs -> null);

        assertTrue(dbm.getExecutedQuery().contains("select * from users where user_id=?"));

        ExecutedStatement last = dbm.getExecutedStatements()
                .get(dbm.getExecutedStatements().size() - 1);
        assertEquals("select * from users where user_id=?", last.getSql());
        assertEquals(1, last.getValues().size());
        assertEquals("admin", last.getValues().get(0).getValue());
    }

    @Test
    public void testExecuteUpdateRecordsExecutedStatement() {
        PreparedSql sql = PreparedSql.of("update users set name=? where user_id=?",
                Arrays.asList(BindValue.of(DataType.STRING, "tama"), BindValue.of(DataType.STRING, "admin")));
        int result = dbm.executeUpdate(sql);
        assertEquals(0, result); // MockPreparedStatement#executeUpdate() always returns 0 (BR-35)

        ExecutedStatement last = dbm.getExecutedStatements()
                .get(dbm.getExecutedStatements().size() - 1);
        assertEquals(2, last.getValues().size());
    }

    @Test
    public void testCheckBindableRejectsUnboundPlaceholder() {
        // ofLiteral with a '?' and no values (e.g. the legacy BLOB path) is rejected.
        PreparedSql sql = PreparedSql.ofLiteral("update file set data=? where id='1'");
        try {
            dbm.executeUpdate(sql);
            fail();
        } catch (DaoException e) {
            assertTrue(e.getMessage().contains("Unbound placeholder(s)"));
        }
    }

    @Test
    public void testCheckBindableRejectsUnterminatedQuote() {
        PreparedSql sql = PreparedSql.ofLiteral("select * from users where name='a");
        try {
            dbm.executeUpdate(sql);
            fail();
        } catch (DaoException e) {
            assertTrue(e.getMessage().contains("Cannot verify placeholders"));
        }
    }

    @Test
    public void testCheckedPreparedSqlNeverRejected() {
        PreparedSql sql = PreparedSql.of("select 1 from dual", Collections.emptyList());
        // must not throw - checked instances always pass checkBindable.
        dbm.executeQuery(sql, rs -> null);
    }

    // --- executeUpdate(PreparedSql, int, InputStream) (U3 write-path, BLOB) ---

    @Test
    // BR-10: setBinaryStream(blobIndex, in) is applied after PreparedStatementBinder.bind
    // (which set NULL at that position) and overwrites it - JDBC "last set wins".
    public void testExecuteUpdatePreparedSqlIntInputStream_OverwritesNullWithStream() throws Exception {
        PreparedSql sql = PreparedSql.of("update file set file_name=?,data=? where file_id='f1'",
                Arrays.asList(BindValue.of(DataType.STRING, "a.bin"), BindValue.ofNull(DataType.OBJECT)));
        InputStream in = new ByteArrayInputStream(new byte[] { 1, 2, 3 });

        int result = dbm.executeUpdate(sql, 2, in);
        assertEquals(0, result); // MockPreparedStatement#executeUpdate() always returns 0 (BR-35)

        MockConnection con = (MockConnection) dbm.getConnection();
        MockPreparedStatement stmt = con.getLastPreparedStatement();
        assertSame(in, stmt.getBoundValue(2));
        assertEquals("a.bin", stmt.getBoundValue(1));
    }

    @Test
    public void testExecuteUpdatePreparedSqlIntInputStream_RecordsExecutedStatement() {
        PreparedSql sql = PreparedSql.of("update file set data=? where file_id=?",
                Arrays.asList(BindValue.ofNull(DataType.OBJECT), BindValue.of(DataType.STRING, "f1")));
        InputStream in = new ByteArrayInputStream(new byte[] { 9 });

        dbm.executeUpdate(sql, 1, in);

        ExecutedStatement last = dbm.getExecutedStatements()
                .get(dbm.getExecutedStatements().size() - 1);
        assertEquals("update file set data=? where file_id=?", last.getSql());
        assertEquals(2, last.getValues().size());
    }

    @Test
    // checkBindable still rejects an unbound-placeholder PreparedSql for the BLOB
    // overload, same as the non-BLOB executeUpdate(PreparedSql) (BR-9a).
    public void testExecuteUpdatePreparedSqlIntInputStream_RejectsUnboundPlaceholder() {
        PreparedSql sql = PreparedSql.ofLiteral("update file set data=? where id='1'");
        InputStream in = new ByteArrayInputStream(new byte[] { 1 });
        try {
            dbm.executeUpdate(sql, 1, in);
            fail();
        } catch (DaoException e) {
            assertTrue(e.getMessage().contains("Unbound placeholder(s)"));
        }
    }

    @Test
    public void testGetExecutedStatementsLazyInit() {
        assertNotNull(dbm.getExecutedStatements());
    }

    @Test
    public void testExistingExecuteQueryStringUnchanged() {
        // literal-path executeQuery(String) must not touch getExecutedStatements().
        int before = dbm.getExecutedStatements().size();
        dbm.executeQuery("select * from users");
        assertEquals(before, dbm.getExecutedStatements().size());
    }
}
