/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Param;
import org.tamacat.dao.PreparedSql;
import org.tamacat.dao.Search;
import org.tamacat.dao.Sort;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.test.Dept;
import org.tamacat.dao.test.FileData;
import org.tamacat.dao.test.User;
import org.tamacat.mock.sql.MockConnection;
import org.tamacat.mock.sql.MockPreparedStatement;
import org.tamacat.sql.SQLParser;

public class QueryImplTest {

	QueryImpl<User> query;
	
	@Before
	public void setUp() throws Exception {
		query = new QueryImpl<User>();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetSelectColumns_addSelectColumn() {
		query.select(User.USER_ID);
		assertEquals(1, query.getSelectColumns().size());
	}
	
	@Test
	public void testGetSelectColumns_addSelectColumns() {
		query.select(User.TABLE.getColumns());
		assertEquals(5, query.getSelectColumns().size());
	}
	
	@Test
	public void testGetUpdateColumns_addUpdateColumn() {
		query.addUpdateColumn(User.PASSWORD);
		assertEquals(1, query.getUpdateColumns().size());
	}

	@Test
	public void testGetUpdateColumns_addUpdateColumns() {
		query.addUpdateColumns(User.TABLE.getColumns());
		assertEquals(5, query.getUpdateColumns().size());
	}
	
	@Test
	public void testGetUpdateColumns_removeUpdateColumns() {
		query.addUpdateColumns(User.TABLE.getColumns()).removeUpdateColumns(User.PASSWORD);
		assertEquals(4, query.getUpdateColumns().size());
	}
	
	@Test
	public void testGetSelectSQL_AddSelectColumn() {
		query.select(User.USER_ID, User.PASSWORD, User.DEPT_ID);
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id FROM users",
			query.getSelectSQL()
		);
	}

	@Test
	public void testGetSelectSQL_AddSelectColumns() {
		query.select(User.TABLE.getColumns());
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_QueryDistinctTrue() {
		query.select(User.TABLE.getColumns()).distinct(true);
		assertEquals(
			"SELECT DISTINCT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_DistinctFalse_SearchUniqueTrue() {
		Search search = new MySQLSearch().unique(true); //override
		query.select(User.TABLE.getColumns()).distinct(false).and(search, (Sort)null);
		assertEquals(
			"SELECT DISTINCT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetSelectSQL_DistinctTrue_SearchUniqueFalse() {
		Search search = new MySQLSearch().unique(false);
		query.select(User.TABLE.getColumns()).distinct(true).and(search, (Sort)null);
		assertEquals(
			"SELECT DISTINCT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			query.getSelectSQL()
		);
	}
	
	@Test
	public void testGetInsertSQL() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		user.setValue(User.DEPT_ID, "123");
		query.addUpdateColumns(User.TABLE.getColumns());
		
		assertEquals(
			"INSERT INTO users (user_id,password,dept_id,update_date,age)"
			+ " VALUES ('admin','test','123',null,null)", query.getInsertSQL(user));
	}

	@Test
	public void testGetUpdateSQL() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		query.addUpdateColumn(User.USER_ID);
		query.addUpdateColumn(User.PASSWORD);
		query.addWhere("and", 
			new SQLParser().value(User.USER_ID, Condition.EQUAL, "admin"));
		assertEquals(
			"UPDATE users SET password='test' WHERE users.user_id='admin'",
			query.getUpdateSQL(user));
	}

	@Test
	public void testGetDeleteSQL() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		
		try {
			query.getDeleteSQL(user);
			fail();
		} catch (InvalidParameterException e) {
			assertTrue(e instanceof InvalidParameterException);
		}
		
		query.addUpdateColumn(User.USER_ID);
		assertEquals(
			"DELETE FROM users WHERE users.user_id='admin'",
			query.getDeleteSQL(user));
	}

	@Test
	public void testAddConnectTable() {
		query.join(User.DEPT_ID, Dept.DEPT_ID);
		assertEquals(" WHERE users.dept_id=dept.dept_id", query.where.toString());
	}

	@Test
	public void testGetTimestampString() {
		assertEquals("current_timestamp", query.getTimestampString());
	}

	@Test
	public void testGetColumnName() {
		assertEquals("users.user_id", QueryImpl.getColumnName(User.USER_ID));
	}

	// --- getSelectPreparedSql() (U2 select-path, business-logic-model.md sec 4) ---

	private static final org.tamacat.sql.BindSqlBuilder BUILDER = new org.tamacat.sql.BindSqlBuilder();

	/** Binds {@code sql}'s values, position by position, onto a fresh mock PreparedStatement. */
	private static MockPreparedStatement bindOnMock(PreparedSql sql) throws Exception {
		MockConnection con = new MockConnection();
		MockPreparedStatement stmt = (MockPreparedStatement) con.prepareStatement(sql.getSql());
		int pos = 1;
		for (org.tamacat.dao.BindValue v : sql.getValues()) {
			if (v.isNull()) {
				stmt.setNull(pos, java.sql.Types.VARCHAR);
			} else {
				stmt.setString(pos, v.getValue());
			}
			pos++;
		}
		return stmt;
	}

	@Test
	// AC-1: single-value EQUAL predicate binds as a placeholder, in position.
	public void testGetSelectPreparedSql_AC1_Equal() throws Exception {
		Param param = BUILDER.value(User.USER_ID, Condition.EQUAL, "admin");
		query.select(User.USER_ID, User.PASSWORD);
		query.addWhere("and", param);
		PreparedSql sql = query.getSelectPreparedSql();

		assertEquals("SELECT users.user_id,users.password FROM users WHERE users.user_id=?", sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("admin", sql.getValues().get(0).getValue());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals(sql.getSql(), stmt.getPreparedSql());
		assertEquals("admin", stmt.getBoundValue(1));
	}

	@Test
	// AC-2: LIKE predicate binds the escaped value as a single placeholder.
	public void testGetSelectPreparedSql_AC2_Like() throws Exception {
		Param param = BUILDER.value(User.USER_ID, Condition.LIKE_PART, "admin");
		query.select(User.USER_ID);
		query.addWhere("and", param);
		PreparedSql sql = query.getSelectPreparedSql();

		assertEquals("SELECT users.user_id FROM users WHERE users.user_id like ?", sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("%admin%", sql.getValues().get(0).getValue());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals("%admin%", stmt.getBoundValue(1));
	}

	@Test
	// AC-3: IN predicate produces (?,?,?) with values in the original order.
	public void testGetSelectPreparedSql_AC3_In() throws Exception {
		Param param = BUILDER.value(User.USER_ID, Condition.IN, "a", "b", "c");
		query.select(User.USER_ID);
		query.addWhere("and", param);
		PreparedSql sql = query.getSelectPreparedSql();

		assertEquals("SELECT users.user_id FROM users WHERE users.user_id in (?,?,?)", sql.getSql());
		assertEquals(3, sql.getValues().size());
		assertEquals("a", sql.getValues().get(0).getValue());
		assertEquals("b", sql.getValues().get(1).getValue());
		assertEquals("c", sql.getValues().get(2).getValue());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals("a", stmt.getBoundValue(1));
		assertEquals("b", stmt.getBoundValue(2));
		assertEquals("c", stmt.getBoundValue(3));
	}

	@Test
	// Two AND-ed predicates: values combine in fragment order.
	public void testGetSelectPreparedSql_TwoFragments_ValueOrder() throws Exception {
		query.select(User.USER_ID, User.PASSWORD);
		query.addWhere("and", BUILDER.value(User.USER_ID, Condition.EQUAL, "admin"));
		query.addWhere("and", BUILDER.value(User.PASSWORD, Condition.EQUAL, "secret"));
		PreparedSql sql = query.getSelectPreparedSql();

		assertEquals(
			"SELECT users.user_id,users.password FROM users WHERE users.user_id=? and users.password=?",
			sql.getSql());
		assertEquals(2, sql.getValues().size());
		assertEquals("admin", sql.getValues().get(0).getValue());
		assertEquals("secret", sql.getValues().get(1).getValue());
	}

	@Test
	// getSelectSQL() and getSelectPreparedSql() must report the same predicate count
	// (business-rules.md 「規則が破れたときの失敗様式」#2 の検出手段).
	public void testGetSelectSQL_And_GetSelectPreparedSql_PredicateCountMatches() {
		query.select(User.USER_ID);
		query.addWhere("and", BUILDER.value(User.USER_ID, Condition.EQUAL, "admin"));
		query.addWhere("and", BUILDER.value(User.PASSWORD, Condition.EQUAL, "secret"));

		String literal = query.getSelectSQL();
		PreparedSql prepared = query.getSelectPreparedSql();

		int literalCount = countConnectors(literal) + 1;
		int preparedCount = countConnectors(prepared.getSql()) + 1;
		assertEquals(literalCount, preparedCount);
	}

	@Test
	// join() must appear in both the literal WHERE and the bind fragments -
	// dropping the bind side would silently remove the JOIN condition (BR-4).
	public void testGetSelectPreparedSql_Join() {
		query.select(User.USER_ID).join(User.DEPT_ID, Dept.DEPT_ID);
		PreparedSql sql = query.getSelectPreparedSql();

		assertEquals("SELECT users.user_id FROM users,dept WHERE users.dept_id=dept.dept_id", sql.getSql());
		assertEquals(0, sql.getValues().size());
	}

	@Test
	public void testGetSelectPreparedSql_NoWhere() {
		query.select(User.USER_ID);
		PreparedSql sql = query.getSelectPreparedSql();
		assertEquals("SELECT users.user_id FROM users", sql.getSql());
		assertEquals(0, sql.getValues().size());
	}

	// --- subqueries (U2 select-path, business-logic-model.md sec 5, AC-4) ---

	@Test
	// AC-4: the child's value(s) land at the correct position in the parent's
	// bind parameter list - immediately after the parent's own preceding values.
	public void testAndIn_AC4_ChildValuesAtCorrectPosition() {
		QueryImpl<User> child = new QueryImpl<User>();
		child.select(User.USER_ID);
		child.addWhere("and", BUILDER.value(User.DEPT_ID, Condition.EQUAL, "dev"));

		query.select(User.USER_ID);
		query.addWhere("and", BUILDER.value(User.PASSWORD, Condition.EQUAL, "secret"));
		query.andIn(User.USER_ID, child);

		PreparedSql sql = query.getSelectPreparedSql();
		assertEquals(
			"SELECT users.user_id FROM users WHERE users.password=? and users.user_id IN (SELECT users.user_id FROM users WHERE users.dept_id=?)",
			sql.getSql());
		assertEquals(2, sql.getValues().size());
		assertEquals("secret", sql.getValues().get(0).getValue());
		assertEquals("dev", sql.getValues().get(1).getValue());
	}

	@Test
	public void testAndNotIn_AC4_ChildValuesAtCorrectPosition() {
		QueryImpl<User> child = new QueryImpl<User>();
		child.select(User.USER_ID);
		child.addWhere("and", BUILDER.value(User.DEPT_ID, Condition.EQUAL, "dev"));

		query.select(User.USER_ID);
		query.andNotIn(User.USER_ID, child);

		PreparedSql sql = query.getSelectPreparedSql();
		assertEquals(
			"SELECT users.user_id FROM users WHERE users.user_id NOT IN (SELECT users.user_id FROM users WHERE users.dept_id=?)",
			sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("dev", sql.getValues().get(0).getValue());
	}

	@Test
	public void testAndExists_AC4_ChildValues() {
		QueryImpl<User> child = new QueryImpl<User>();
		child.select(User.USER_ID);
		child.addWhere("and", BUILDER.value(User.DEPT_ID, Condition.EQUAL, "dev"));

		query.select(User.USER_ID);
		query.andExists(child);

		PreparedSql sql = query.getSelectPreparedSql();
		assertEquals(
			"SELECT users.user_id FROM users WHERE EXISTS (SELECT users.user_id FROM users WHERE users.dept_id=?)",
			sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("dev", sql.getValues().get(0).getValue());
	}

	@Test
	public void testAndNotExists_AC4_ChildValues() {
		QueryImpl<User> child = new QueryImpl<User>();
		child.select(User.USER_ID);
		child.addWhere("and", BUILDER.value(User.DEPT_ID, Condition.EQUAL, "dev"));

		query.select(User.USER_ID);
		query.andNotExists(child);

		PreparedSql sql = query.getSelectPreparedSql();
		assertEquals(
			"SELECT users.user_id FROM users WHERE NOT EXISTS (SELECT users.user_id FROM users WHERE users.dept_id=?)",
			sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("dev", sql.getValues().get(0).getValue());
	}

	@Test
	// The subquery's values arrive as one contiguous block even when the child has
	// multiple predicates (no reordering across the block boundary).
	public void testAndIn_AC4_ChildWithMultiplePredicates() {
		QueryImpl<User> child = new QueryImpl<User>();
		child.select(User.USER_ID);
		child.addWhere("and", BUILDER.value(User.DEPT_ID, Condition.EQUAL, "dev"));
		child.addWhere("and", BUILDER.value(User.AGE, Condition.GREATER, "20"));

		query.select(User.USER_ID);
		query.addWhere("and", BUILDER.value(User.PASSWORD, Condition.EQUAL, "secret"));
		query.andIn(User.USER_ID, child);

		PreparedSql sql = query.getSelectPreparedSql();
		assertEquals(3, sql.getValues().size());
		assertEquals("secret", sql.getValues().get(0).getValue());
		assertEquals("dev", sql.getValues().get(1).getValue());
		assertEquals("20", sql.getValues().get(2).getValue());
	}

	// --- andOuterJoin / outerJoin (U2 select-path, business-logic-model.md sec 7) ---

	@Test
	// Both branches of outerJoin() (new key, existing key) must route through
	// putOuterJoin() - missing either would drop the JOIN condition from the
	// bind-path FROM clause (business-rules.md sec 7.3 note).
	public void testOuterJoin_BothBranchesKeepLiteralAndBindInSync() {
		query.select(User.USER_ID);
		query.outerJoin(User.DEPT_ID, Dept.DEPT_ID); // new-key branch
		query.outerJoin(User.USER_ID, Dept.DEPT_ID); // existing-key branch

		String literal = query.getSelectSQL();
		PreparedSql prepared = query.getSelectPreparedSql();

		assertEquals(literal, prepared.getSql());
		assertEquals(0, prepared.getValues().size());
		assertTrue(literal.indexOf("left join") >= 0);
		assertTrue(literal.indexOf(" and users.user_id=dept.dept_id") >= 0);
	}

	@Test
	// Deprecated andOuterJoin(Table, Search): literal unchanged; bind side gets
	// the same text embedded literally with zero values (BR-12, outside SM-1).
	public void testAndOuterJoin_Deprecated_BindSideLiteralZeroValues() {
		query.select(User.USER_ID);
		query.outerJoin(User.DEPT_ID, Dept.DEPT_ID);
		Search search = new Search();
		search.and(Dept.DEPT_NAME, Condition.EQUAL, "Development");
		query.andOuterJoin(Dept.TABLE, search);

		String literal = query.getSelectSQL();
		PreparedSql prepared = query.getSelectPreparedSql();

		assertEquals(literal, prepared.getSql());
		assertEquals(0, prepared.getValues().size());
		assertTrue(literal.indexOf("dept.dept_name='Development'") >= 0);
	}

	@Test
	// New andOuterJoin(Table, Param): the param's placeholder and value are
	// carried through to the FROM clause.
	public void testAndOuterJoin_Param_CarriesValueThrough() {
		query.select(User.USER_ID);
		query.outerJoin(User.DEPT_ID, Dept.DEPT_ID);
		query.andOuterJoin(Dept.TABLE, BUILDER.value(Dept.DEPT_NAME, Condition.EQUAL, "Development"));

		PreparedSql prepared = query.getSelectPreparedSql();
		assertEquals(1, prepared.getValues().size());
		assertEquals("Development", prepared.getValues().get(0).getValue());
		assertTrue(prepared.getSql().indexOf("dept.dept_name=?") >= 0);
	}

	@Test
	// Value combination order: FROM-clause values (outer join) precede WHERE
	// fragment values, matching the text output order (BR-6).
	public void testAndOuterJoin_Param_ValueOrder_BeforeWhereFragments() {
		query.select(User.USER_ID);
		query.outerJoin(User.DEPT_ID, Dept.DEPT_ID);
		query.andOuterJoin(Dept.TABLE, BUILDER.value(Dept.DEPT_NAME, Condition.EQUAL, "Development"));
		query.addWhere("and", BUILDER.value(User.PASSWORD, Condition.EQUAL, "secret"));

		PreparedSql prepared = query.getSelectPreparedSql();
		assertEquals(2, prepared.getValues().size());
		assertEquals("Development", prepared.getValues().get(0).getValue());
		assertEquals("secret", prepared.getValues().get(1).getValue());
	}

	@Test
	// BR-13: andOuterJoin(Table, Param) is a silent no-op when outerJoin() was
	// never called for that table (mirrors the deprecated method's behavior).
	public void testAndOuterJoin_Param_NoOpWhenKeyAbsent() {
		query.select(User.USER_ID);
		query.andOuterJoin(Dept.TABLE, BUILDER.value(Dept.DEPT_NAME, Condition.EQUAL, "Development"));

		PreparedSql prepared = query.getSelectPreparedSql();
		assertEquals(0, prepared.getValues().size());
		assertEquals("SELECT users.user_id FROM users", prepared.getSql());
	}

	@Test
	// getSelectSQL()'s literal output for andOuterJoin(Table, Search) is unchanged
	// from the pre-U2 implementation (BR-10).
	public void testAndOuterJoin_Deprecated_LiteralUnchanged() {
		query.select(User.USER_ID);
		query.outerJoin(User.DEPT_ID, Dept.DEPT_ID);
		Search search = new Search();
		search.and(Dept.DEPT_NAME, Condition.EQUAL, "Development");
		query.andOuterJoin(Dept.TABLE, search);

		assertEquals(
			"SELECT users.user_id FROM users left join dept on users.dept_id=dept.dept_id"
				+ " and dept.dept_name='Development'",
			query.getSelectSQL());
	}

	// --- U5 identifier-safety: Column.getFunctionName() path (AC-10b, SELECT) ---

	@Test
	// ID-1: single quote in functionName rejects at getSelectSQL() generation time.
	public void testGetSelectSQL_FunctionNameWithSingleQuote_Throws() {
		org.tamacat.dao.meta.Column col = org.tamacat.dao.meta.Columns.create("c").type(org.tamacat.dao.meta.DataType.FUNCTION)
			.functionName("max'; DROP TABLE users; --");
		query.select(col);
		try {
			query.getSelectSQL();
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// Same input via getSelectPreparedSql() - both entry points share buildSelectClause().
	public void testGetSelectPreparedSql_FunctionNameWithSingleQuote_Throws() {
		org.tamacat.dao.meta.Column col = org.tamacat.dao.meta.Columns.create("c").type(org.tamacat.dao.meta.DataType.FUNCTION)
			.functionName("max'; DROP TABLE users; --");
		query.select(col);
		try {
			query.getSelectPreparedSql();
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-3: semicolon in functionName.
	public void testGetSelectSQL_FunctionNameWithSemicolon_Throws() {
		org.tamacat.dao.meta.Column col = org.tamacat.dao.meta.Columns.create("c").type(org.tamacat.dao.meta.DataType.FUNCTION)
			.functionName("max;drop");
		query.select(col);
		try {
			query.getSelectSQL();
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-4: line comment in functionName.
	public void testGetSelectSQL_FunctionNameWithLineComment_Throws() {
		org.tamacat.dao.meta.Column col = org.tamacat.dao.meta.Columns.create("c").type(org.tamacat.dao.meta.DataType.FUNCTION)
			.functionName("max--comment");
		query.select(col);
		try {
			query.getSelectSQL();
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// Legitimate functionName (no dangerous literal) still works, unchanged.
	public void testGetSelectSQL_LegitimateFunctionName_Passes() {
		org.tamacat.dao.meta.Table table = org.tamacat.dao.meta.Tables.create("users");
		org.tamacat.dao.meta.Column col = org.tamacat.dao.meta.FunctionOnlyColumnFixture.createPlain(table, "c")
			.functionName("COUNT");
		query.select(col);
		assertEquals("SELECT COUNT c FROM users", query.getSelectSQL());
	}

	@Test
	// BR-14 regression: isFunction()==true with getFunctionName()==null (Column.FUNCTION
	// define only, no functionName(...) call) must not throw - existing "null <col>" output
	// is preserved.
	public void testGetSelectSQL_FunctionColumnWithNullFunctionName_DoesNotThrow_RegressionBR14() {
		org.tamacat.dao.meta.Table table = org.tamacat.dao.meta.Tables.create("users");
		org.tamacat.dao.meta.Column col = org.tamacat.dao.meta.FunctionOnlyColumnFixture.create(table, "c");
		assertTrue(col.isFunction());
		assertNull(col.getFunctionName());
		query.select(col);
		assertEquals("SELECT null c FROM users", query.getSelectSQL());
	}

	// --- FR-6.2 regression: literal getUpdateSQL's OBJECT branch table-qualification
	// (business-rules.md BR-12) ---

	@Test
	// :268 (OBJECT branch) must strip table-qualification exactly like the other two
	// SET branches (:257, :262) - before this fix it read "file.data=?".
	public void testGetUpdateSQL_ObjectColumn_NoTableQualification() {
		QueryImpl<FileData> q = new QueryImpl<FileData>();
		FileData data = new FileData();
		data.setValue(FileData.FILE_ID, "f1");
		data.setValue(FileData.FILE_NAME, "a.txt");
		q.addUpdateColumn(FileData.FILE_NAME);
		q.addUpdateColumn(FileData.DATA);
		q.addUpdateColumn(FileData.FILE_ID);

		String sql = q.getUpdateSQL(data);
		assertTrue(sql.indexOf("data=?") >= 0);
		assertTrue(sql.indexOf("file.data=?") < 0);
	}

	@Test
	// All three SET-clause branches (update value, auto-timestamp, OBJECT) now
	// produce unqualified column names consistently - no branch-to-branch mismatch.
	public void testGetUpdateSQL_AllSetBranches_ConsistentlyUnqualified() {
		QueryImpl<FileData> q = new QueryImpl<FileData>();
		FileData data = new FileData();
		data.setValue(FileData.FILE_ID, "f1");
		data.setValue(FileData.FILE_NAME, "a.txt");
		q.addUpdateColumn(FileData.FILE_NAME);
		q.addUpdateColumn(FileData.DATA);
		q.addUpdateColumn(FileData.UPDATE_DATE);
		q.addUpdateColumn(FileData.FILE_ID);

		assertEquals(
			"UPDATE file SET file_name='a.txt',data=?,update_date=current_timestamp WHERE file.file_id='f1'",
			q.getUpdateSQL(data));
	}

	// --- getInsertPreparedSql(T) / getUpdatePreparedSql(T) (U3 write-path, AC-3b, AC-7,
	// business-logic-model.md sec 2, sec 3) ---

	@Test
	// AC-3b: INSERT VALUES bind values land in column order.
	public void testGetInsertPreparedSql_AC3b_ValuesInColumnOrder() throws Exception {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		user.setValue(User.DEPT_ID, "123");
		query.addUpdateColumns(User.TABLE.getColumns());

		PreparedSql sql = query.getInsertPreparedSql(user);
		assertEquals(
			"INSERT INTO users (user_id,password,dept_id,update_date,age) VALUES (?,?,?,?,?)",
			sql.getSql());
		assertEquals(5, sql.getValues().size());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals("admin", stmt.getBoundValue(1));
		assertEquals("test", stmt.getBoundValue(2));
		assertEquals("123", stmt.getBoundValue(3));
		assertNull(stmt.getBoundValue(4));
		assertNull(stmt.getBoundValue(5));
	}

	@Test
	// An unset (non-required) column binds as a NULL BindValue, not an empty string.
	public void testGetInsertPreparedSql_UnsetColumn_BindsNullValue() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		query.addUpdateColumn(User.USER_ID);
		query.addUpdateColumn(User.DEPT_ID);

		PreparedSql sql = query.getInsertPreparedSql(user);
		assertEquals("INSERT INTO users (user_id,dept_id) VALUES (?,?)", sql.getSql());
		assertEquals(2, sql.getValues().size());
		assertEquals("admin", sql.getValues().get(0).getValue());
		assertTrue(sql.getValues().get(1).isNull());
	}

	@Test
	// BR-2: value-selection logic (autoGenerateId) is unchanged - the generated id is
	// both bound and written back onto data, same as the literal path.
	public void testGetInsertPreparedSql_AutoGenerateId_SetsGeneratedIdOnData() {
		QueryImpl<FileData> q = new QueryImpl<FileData>();
		FileData data = new FileData();
		data.setValue(FileData.FILE_NAME, "a.txt");
		q.addUpdateColumns(FileData.TABLE.getColumns());

		PreparedSql sql = q.getInsertPreparedSql(data);
		// update_date is auto-timestamp: its token is the literal "current_timestamp",
		// not a placeholder (BR-3) - 5 placeholders, not 6.
		assertEquals(
			"INSERT INTO file (file_id,file_name,size,content_type,data,update_date)"
			+ " VALUES (?,?,?,?,?,current_timestamp)",
			sql.getSql());
		assertEquals(5, sql.getValues().size());
		assertNotNull(data.getValue(FileData.FILE_ID));
		assertEquals(data.getValue(FileData.FILE_ID), sql.getValues().get(0).getValue());
	}

	@Test
	// The INSERT column list (business logic unchanged, BR-2) is identical text
	// between the literal and bind-path builders.
	public void testGetInsertSQL_And_GetInsertPreparedSql_ColumnListMatches() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		query.addUpdateColumns(User.TABLE.getColumns());
		String literal = query.getInsertSQL(user);

		QueryImpl<User> q2 = new QueryImpl<User>();
		q2.addUpdateColumns(User.TABLE.getColumns());
		PreparedSql prepared = q2.getInsertPreparedSql(user);

		String literalColumns = literal.substring(literal.indexOf('('), literal.indexOf(')') + 1);
		String preparedColumns = prepared.getSql().substring(
			prepared.getSql().indexOf('('), prepared.getSql().indexOf(')') + 1);
		assertEquals(literalColumns, preparedColumns);
	}

	@Test
	// AC-3b (unbound condition 1, failure mode 1): SET values and the WHERE-bound
	// primary key value must not shift - setValues and bindWhereValues are combined
	// in exactly one place (BR-7).
	public void testGetUpdatePreparedSql_AC3b_SetAndWhereValueOrder() throws Exception {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		user.setValue(User.DEPT_ID, "dev");
		query.addUpdateColumn(User.USER_ID);
		query.addUpdateColumn(User.PASSWORD);
		query.addUpdateColumn(User.DEPT_ID);

		PreparedSql sql = query.getUpdatePreparedSql(user);
		assertEquals("UPDATE users SET password=?,dept_id=? WHERE users.user_id=?", sql.getSql());
		assertEquals(3, sql.getValues().size());
		assertEquals("test", sql.getValues().get(0).getValue());
		assertEquals("dev", sql.getValues().get(1).getValue());
		assertEquals("admin", sql.getValues().get(2).getValue());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals("test", stmt.getBoundValue(1));
		assertEquals("dev", stmt.getBoundValue(2));
		assertEquals("admin", stmt.getBoundValue(3));
	}

	@Test
	// AC-7: OBJECT + STRING 2-column UPDATE (plus an auto-timestamp column) -
	// getBlobIndex() returns the OBJECT value's absolute bind position, compared
	// against a position hand-derived from column order (not getBindIndexOf, which
	// would be a tautology after the iteration-2 fix - security-requirements.md R-28).
	public void testGetUpdatePreparedSql_AC7_ObjectAndAutoTimestamp_BlobIndex() throws Exception {
		QueryImpl<FileData> q = new QueryImpl<FileData>();
		FileData data = new FileData();
		data.setValue(FileData.FILE_ID, "f1");
		data.setValue(FileData.FILE_NAME, "a.bin");
		q.addUpdateColumn(FileData.FILE_NAME);
		q.addUpdateColumn(FileData.DATA);
		q.addUpdateColumn(FileData.UPDATE_DATE);
		q.addUpdateColumn(FileData.FILE_ID);

		PreparedSql sql = q.getUpdatePreparedSql(data);
		assertEquals(
			"UPDATE file SET file_name=?,data=?,update_date=current_timestamp WHERE file.file_id=?",
			sql.getSql());
		assertEquals(3, sql.getValues().size());
		assertEquals("a.bin", sql.getValues().get(0).getValue());
		assertTrue(sql.getValues().get(1).isNull());
		assertEquals(DataType.OBJECT, sql.getValues().get(1).getType());
		assertEquals("f1", sql.getValues().get(2).getValue());

		// hand-derived: SET clause has 2 entries (file_name, data), so data is bind
		// position 2 - independent of getBindIndexOf.
		assertEquals(2, q.getBlobIndex());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals("a.bin", stmt.getBoundValue(1));
		assertNull(stmt.getBoundValue(2));
		assertEquals("f1", stmt.getBoundValue(3));
	}

	@Test
	// BR-5: the SET clause is unqualified (no table prefix); the primary-key WHERE
	// predicate is still table-qualified (BR-4, unchanged from the literal path).
	public void testGetUpdatePreparedSql_SetUnqualified_WhereQualified() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		user.setValue(User.PASSWORD, "test");
		query.addUpdateColumn(User.USER_ID);
		query.addUpdateColumn(User.PASSWORD);

		PreparedSql sql = query.getUpdatePreparedSql(user);
		assertEquals("UPDATE users SET password=? WHERE users.user_id=?", sql.getSql());
	}

	@Test
	// No column matches an update branch (isUpdate/autoTimestamp/OBJECT) - the SET
	// clause is empty, but the WHERE-bound primary key predicate still binds
	// correctly (mirrors the literal getUpdateSQL()'s behavior for the same input).
	public void testGetUpdatePreparedSql_NoUpdatedColumns_EmptySetClause() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		query.addUpdateColumn(User.USER_ID);
		query.addUpdateColumn(User.DEPT_ID);

		PreparedSql sql = query.getUpdatePreparedSql(user);
		assertEquals("UPDATE users SET  WHERE users.user_id=?", sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("admin", sql.getValues().get(0).getValue());
	}

	// --- getDeletePreparedSql(T) / getDeleteAllPreparedSql(Table) (U3 write-path,
	// business-logic-model.md sec 4) ---

	@Test
	// BR-13: exception throwing (table name cannot be resolved) is unchanged.
	public void testGetDeletePreparedSql_NoTable_Throws() {
		try {
			query.getDeletePreparedSql(new User());
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	public void testGetDeletePreparedSql_BindsPrimaryKeyPredicate() throws Exception {
		User user = new User();
		user.setValue(User.USER_ID, "admin");
		query.addUpdateColumn(User.USER_ID);

		PreparedSql sql = query.getDeletePreparedSql(user);
		assertEquals("DELETE FROM users WHERE users.user_id=?", sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("admin", sql.getValues().get(0).getValue());

		MockPreparedStatement stmt = bindOnMock(sql);
		assertEquals("admin", stmt.getBoundValue(1));
	}

	@Test
	public void testGetDeleteAllPreparedSql_NoWhere_EmptyValues() {
		PreparedSql sql = query.getDeleteAllPreparedSql(User.TABLE);
		assertEquals("DELETE FROM users", sql.getSql());
		assertEquals(0, sql.getValues().size());
	}

	@Test
	public void testGetDeleteAllPreparedSql_WithExplicitWhere_BindsValue() {
		query.addWhere("and", BUILDER.value(User.USER_ID, Condition.EQUAL, "admin"));
		PreparedSql sql = query.getDeleteAllPreparedSql(User.TABLE);
		assertEquals("DELETE FROM users WHERE users.user_id=?", sql.getSql());
		assertEquals(1, sql.getValues().size());
		assertEquals("admin", sql.getValues().get(0).getValue());
	}

	@Test
	// Failure mode 3 detection (business-rules.md): getDeleteSQL() and
	// getDeletePreparedSql() must report the same predicate count.
	public void testGetDeleteSQL_And_GetDeletePreparedSql_PredicateCountMatches() {
		User user = new User();
		user.setValue(User.USER_ID, "admin");

		QueryImpl<User> q1 = new QueryImpl<User>();
		q1.addUpdateColumn(User.USER_ID);
		String literal = q1.getDeleteSQL(user);

		QueryImpl<User> q2 = new QueryImpl<User>();
		q2.addUpdateColumn(User.USER_ID);
		PreparedSql prepared = q2.getDeletePreparedSql(user);

		int literalCount = countConnectors(literal) + 1;
		int preparedCount = countConnectors(prepared.getSql()) + 1;
		assertEquals(literalCount, preparedCount);
	}

	private static int countConnectors(String s) {
		int count = 0;
		int idx = 0;
		while ((idx = s.indexOf(" and ", idx)) >= 0) {
			count++;
			idx += 5;
		}
		idx = 0;
		while ((idx = s.indexOf(" or ", idx)) >= 0) {
			count++;
			idx += 4;
		}
		return count;
	}
}
