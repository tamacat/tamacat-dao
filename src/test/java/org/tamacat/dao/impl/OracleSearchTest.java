package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import org.tamacat.dao.Search;

public class OracleSearchTest {

	@Test
	public void testOracleSearch() {
		new OracleSearch();
		OracleSearch.OracleValueConvertFilter filter = new OracleSearch.OracleValueConvertFilter();

		assertEquals("te''st", filter.convertValue("te'st"));
	}

	// AC-10: null must not throw NullPointerException, and must return null -
	// the same contract as Search.DefaultValueConvertFilter and
	// MySQLSearch.MySQLValueConvertFilter (business-rules.md BR-16).

	@Test
	public void testConvertValue_Null_ReturnsNull() {
		OracleSearch.OracleValueConvertFilter filter = new OracleSearch.OracleValueConvertFilter();
		assertNull(filter.convertValue(null));
	}

	@Test
	public void testConvertValue_Null_MatchesDefaultValueConvertFilter() {
		Search.DefaultValueConvertFilter defaultFilter = new Search.DefaultValueConvertFilter();
		OracleSearch.OracleValueConvertFilter oracleFilter = new OracleSearch.OracleValueConvertFilter();
		assertEquals(defaultFilter.convertValue(null), oracleFilter.convertValue(null));
	}

	@Test
	public void testConvertValue_Null_MatchesMySQLValueConvertFilter() {
		MySQLSearch.MySQLValueConvertFilter mysqlFilter = new MySQLSearch.MySQLValueConvertFilter();
		OracleSearch.OracleValueConvertFilter oracleFilter = new OracleSearch.OracleValueConvertFilter();
		assertEquals(mysqlFilter.convertValue(null), oracleFilter.convertValue(null));
	}
}
