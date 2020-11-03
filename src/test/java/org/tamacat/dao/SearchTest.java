/*
 * Copyright 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Search.ValueConvertFilter;
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
}
