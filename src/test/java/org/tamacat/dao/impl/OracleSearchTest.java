package org.tamacat.dao.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import org.tamacat.dao.impl.OracleSearch;

public class OracleSearchTest {

	@Test
	public void testOracleSearch() {
		new OracleSearch();
		OracleSearch.OracleValueConvertFilter filter = new OracleSearch.OracleValueConvertFilter();
		
		assertEquals("te''st", filter.convertValue("te'st"));
	}
}
