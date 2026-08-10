package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Query;
import org.tamacat.dao.test.User;
import org.tamacat.sql.DBAccessManager;

/**
 * AC-9: Oracle's searchList - rownum paging, no unbound '?', both start and max
 * used (business-logic-model.md sec 4, business-rules.md BR-6/BR-11/BR-12/BR-13/BR-21).
 *
 * <p>{@code compose}/{@code wrapRownum} are private static, so the generated SQL is
 * observed indirectly via {@code getExecutedQuery()} - the text actually sent to
 * {@link DBAccessManager#executeQuery(org.tamacat.dao.PreparedSql, org.tamacat.sql.ResultSetHandler)}
 * after paging (design source of truth: business-logic-model.md sec 4.2/4.3 table).
 */
public class OracleDaoTest {

	OracleDao<User> dao;

	@Before
	public void setUp() throws Exception {
		dao = new OracleDao<>();
		dao.setDatabase("default");
		dao.setPrototype(User.class);
	}

	@After
	public void tearDown() throws Exception {
		if (dao != null)
			dao.close();
	}

	private String lastExecuted() {
		return dao.getExecutedQuery().get(dao.getExecutedQuery().size() - 1);
	}

	private static int countOccurrences(String text, String needle) {
		int count = 0;
		int idx = 0;
		while ((idx = text.indexOf(needle, idx)) >= 0) {
			count++;
			idx += needle.length();
		}
		return count;
	}

	@Test
	public void testCreateSearch() {
		assertTrue(dao.createSearch() instanceof OracleSearch);
	}

	// W-1: start>0 && max>0 -> 2-stage wrap, both bounds.
	@Test
	public void testW1_OffsetAndLimit_TwoStageWrap() {
		Query<User> query = dao.createQuery().select(User.USER_ID);
		dao.searchListForOracle(query, 3, 5);
		assertEquals(
			"select * from ( select row_.*, rownum rownum_ from ( SELECT users.user_id FROM users"
				+ " ) row_ ) where rownum_ > 2 and rownum_ <= 7",
			lastExecuted());
	}

	// W-2: start>0 && max<=0 -> 2-stage wrap, lower bound only.
	@Test
	public void testW2_OffsetOnly_TwoStageWrapNoUpperBound() {
		Query<User> query = dao.createQuery().select(User.USER_ID);
		dao.searchListForOracle(query, 3, 0);
		assertEquals(
			"select * from ( select row_.*, rownum rownum_ from ( SELECT users.user_id FROM users"
				+ " ) row_ ) where rownum_ > 2",
			lastExecuted());
	}

	// W-3: start<=0 && max>0 -> single-stage wrap.
	@Test
	public void testW3_LimitOnly_SingleStageWrap() {
		Query<User> query = dao.createQuery().select(User.USER_ID);
		dao.searchListForOracle(query, -1, 5);
		assertEquals(
			"select * from ( SELECT users.user_id FROM users ) where rownum <= 5",
			lastExecuted());
	}

	// W-4: neither -> no wrap, base SQL unchanged.
	@Test
	public void testW4_Neither_NoWrap() {
		Query<User> query = dao.createQuery().select(User.USER_ID);
		dao.searchListForOracle(query, 0, 0);
		assertEquals("SELECT users.user_id FROM users", lastExecuted());
	}

	// BR-21: "for update" is stripped from the core before wrapping and re-appended
	// exactly once at the outermost level - not left duplicated (inside the inline
	// view AND outside it, as the pre-fix code did).
	@Test
	public void testForUpdate_AppendedExactlyOnceAtOutermost() {
		Query<User> query = dao.createQuery().select(User.USER_ID).where("1=1 for update");
		dao.searchListForOracle(query, 3, 5);
		String sql = lastExecuted();
		assertEquals(1, countOccurrences(sql, "for update"));
		assertTrue("for update must be at the outermost end", sql.endsWith("for update"));
		assertEquals(
			"select * from ( select row_.*, rownum rownum_ from ( SELECT users.user_id FROM users"
				+ " WHERE 1=1 ) row_ ) where rownum_ > 2 and rownum_ <= 7 for update",
			sql);
	}

	// W-3 with "for update": the same strip/re-append rule applies (business-logic-model.md sec 4.3).
	@Test
	public void testForUpdate_W3_AppendedExactlyOnceAtOutermost() {
		Query<User> query = dao.createQuery().select(User.USER_ID).where("1=1 for update");
		dao.searchListForOracle(query, -1, 5);
		String sql = lastExecuted();
		assertEquals(1, countOccurrences(sql, "for update"));
		assertEquals(
			"select * from ( SELECT users.user_id FROM users WHERE 1=1 ) where rownum <= 5 for update",
			sql);
	}

	// No "for update" in the input -> none appears in the output.
	@Test
	public void testNoForUpdate_NotAppended() {
		Query<User> query = dao.createQuery().select(User.USER_ID);
		dao.searchListForOracle(query, 3, 5);
		assertEquals(0, countOccurrences(lastExecuted(), "for update"));
	}

	// AC-9 (delegation): searchList(Query,int,int) - the Dao override - must produce
	// the same rownum-wrapped SQL as searchListForOracle (BR-12).
	@Test
	public void testSearchList_DelegatesToSearchListForOracle() {
		Query<User> query1 = dao.createQuery().select(User.USER_ID);
		dao.searchListForOracle(query1, 3, 5);
		String viaSearchListForOracle = lastExecuted();

		Query<User> query2 = dao.createQuery().select(User.USER_ID);
		dao.searchList(query2, 3, 5);
		String viaSearchList = lastExecuted();

		assertEquals(viaSearchListForOracle, viaSearchList);
	}

	// AC-9: no unbound '?' - a WHERE predicate's placeholder is bound, and the
	// rownum boundary values are int-literal text (never '?'), so the composed
	// PreparedSql executes without DaoException.
	@Test
	public void testNoUnboundPlaceholders_BoundWherePredicateSurvivesPaging() {
		Query<User> query = dao.createQuery().select(User.USER_ID)
			.where(dao.prepare(User.USER_ID, Condition.EQUAL, "admin"));
		dao.searchListForOracle(query, 3, 5);
		String sql = lastExecuted();
		assertEquals(1, countOccurrences(sql, "?"));
		assertTrue(sql.indexOf("users.user_id=?") >= 0);
		assertTrue("rownum boundaries must be int literals, not '?'", sql.indexOf("rownum_ > 2") >= 0);
	}
}
