/*
 * Copyright 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Search.ValueConvertFilter;
import org.tamacat.dao.impl.MySQLCondition;
import org.tamacat.dao.impl.PostgreSQLCondition;
import org.tamacat.dao.meta.DefaultColumn;
import org.tamacat.dao.meta.DefaultTable;
import org.tamacat.dao.meta.DataType;

import junit.framework.TestCase;

public class SearchTest extends TestCase {

	DefaultTable table1;
	DefaultColumn column1;
	DefaultColumn column2;
	Search search;

	@Before
	protected void setUp() throws Exception {
		table1 = new DefaultTable("test1");
		column1 = new DefaultColumn();
		column1.columnName("name").type(DataType.STRING);

		column2 = new DefaultColumn();
		column2.columnName("id").type(DataType.NUMERIC);
		table1.registerColumn(column1, column2);
		search = new Search();
	}

	static class TestValueConvertFilter implements Search.ValueConvertFilter {
		public String convertValue(String value) {
			return value.replace("'", "''").replace("\\", "\\\\");
		}
	}

	@Test
	public void testRdbSearchConstructor() {
		ValueConvertFilter filter = new TestValueConvertFilter();
		search = new Search(filter);
		search.and(column1, Condition.LIKE_HEAD, "Tama\\Cat");
		assertEquals("test1.name like 'Tama\\\\Cat%'", search.getSearchString());
	}

	@Test
	public void testAnd() {
		search.and(column1, Condition.LIKE_HEAD, "TamaCat");
		assertEquals("test1.name like 'TamaCat%'", search.getSearchString());

		search.and(column2, Condition.EQUAL, "123");
		assertEquals("test1.name like 'TamaCat%' and test1.id=123", search.getSearchString());
	}

	@Test
	public void testIn() {
		Search search = new Search();
		search.and(column1, Condition.IN, "123");
		assertEquals("test1.name in ('123')", search.getSearchString());

		search = new Search();
		search.and(column1, Condition.IN, "123","456","789");
		assertEquals("test1.name in ('123','456','789')", search.getSearchString());
		
		search = new Search();
		search.and(column2, Condition.IN, "123");
		assertEquals("test1.id in (123)", search.getSearchString());
		
		search = new Search();
		search.and(column2, Condition.IN, "123","456","789");
		assertEquals("test1.id in (123,456,789)", search.getSearchString());
	}
	
	@Test
	public void testRegexp_MySQL() {
		search.and(column1, MySQLCondition.REGEXP, "^TamaCat$");
		assertEquals("test1.name regexp '^TamaCat$'", search.getSearchString());
	}
	
	@Test
	public void testRlike_MySQL() {
		search.and(column1, MySQLCondition.RLIKE, "^TamaCat$");
		assertEquals("test1.name rlike '^TamaCat$'", search.getSearchString());
	}
	
	@Test
	public void testRegexp_PostgreSQL() {
		search.and(column1, PostgreSQLCondition.REGEXP, "^TamaCat$");
		assertEquals("test1.name ~ '^TamaCat$'", search.getSearchString());
	}
	
	@Test
	public void testGetSearchString() {
		assertEquals("", search.getSearchString());

		search.and(column1, Condition.EQUAL, "TamaCat");
		assertEquals("test1.name='TamaCat'", search.getSearchString());
	}

	// for debug.
	static public void assertEquals(String expected, String actual) {
		// System.out.println(actual);
		assertEquals(null, expected, actual);
	}

	// --- getSearchParam() (U2 select-path, business-logic-model.md sec 2.4) ---

	@Test
	public void testGetSearchParam_Empty() {
		Param p = search.getSearchParam();
		assertEquals("", p.getSql());
		assertEquals(0, p.getValues().size());
	}

	@Test
	public void testGetSearchParam_SinglePredicate() {
		search.and(column1, Condition.EQUAL, "TamaCat");
		Param p = search.getSearchParam();
		assertEquals("test1.name=?", p.getSql());
		assertEquals(1, p.getValues().size());
		assertEquals("TamaCat", p.getValues().get(0).getValue());
	}

	@Test
	public void testGetSearchParam_TwoPredicatesAnd() {
		search.and(column1, Condition.EQUAL, "TamaCat");
		search.and(column2, Condition.EQUAL, "123");
		Param p = search.getSearchParam();
		assertEquals("test1.name=? and test1.id=?", p.getSql());
		assertEquals(2, p.getValues().size());
		assertEquals("TamaCat", p.getValues().get(0).getValue());
		assertEquals("123", p.getValues().get(1).getValue());
	}

	@Test
	public void testGetSearchParam_TwoPredicatesOrMixed() {
		search.and(column1, Condition.EQUAL, "TamaCat");
		search.or(column2, Condition.EQUAL, "123");
		Param p = search.getSearchParam();
		assertEquals("test1.name=? or test1.id=?", p.getSql());
		assertEquals(2, p.getValues().size());
	}

	@Test
	public void testGetSearchParam_LikeCondition() {
		search.and(column1, Condition.LIKE_HEAD, "TamaCat");
		Param p = search.getSearchParam();
		assertEquals(1, p.getValues().size());
		assertTrue(p.getSql().indexOf('?') >= 0);
		assertEquals("TamaCat%", p.getValues().get(0).getValue());
	}

	@Test
	public void testGetSearchParam_NestedSearch() {
		Search inner = new Search();
		inner.and(column1, Condition.EQUAL, "TamaCat");
		search.and(inner);
		Param p = search.getSearchParam();
		assertEquals("(test1.name=?)", p.getSql());
		assertEquals(1, p.getValues().size());
		assertEquals("TamaCat", p.getValues().get(0).getValue());
	}

	@Test
	public void testGetSearchParam_PredicateCountMatchesGetSearchString() {
		search.and(column1, Condition.EQUAL, "TamaCat");
		search.and(column2, Condition.EQUAL, "123");
		search.or(column1, Condition.LIKE_HEAD, "Tama");
		String literal = search.getSearchString();
		String bind = search.getSearchParam().getSql();
		int literalPredicates = countConnectors(literal) + 1;
		int bindPredicates = countConnectors(bind) + 1;
		assertEquals(literalPredicates, bindPredicates);
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
