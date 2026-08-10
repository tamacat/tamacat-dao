/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.Condition;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.Columns;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.meta.DefaultTable;

public class ValueRulesTest {

	Column stringColumn;
	Column numericColumn;
	Column dateColumn;
	Column timeColumn;
	Column objectColumn;
	Column functionColumn;
	Column notNullColumn;

	@Before
	public void setUp() {
		stringColumn = Columns.create("name").type(DataType.STRING);
		numericColumn = Columns.create("id").type(DataType.NUMERIC);
		dateColumn = Columns.create("date").type(DataType.DATE);
		timeColumn = Columns.create("time").type(DataType.TIME);
		objectColumn = Columns.create("data").type(DataType.OBJECT);
		functionColumn = Columns.create("fn").type(DataType.FUNCTION);
		notNullColumn = Columns.create("name").type(DataType.STRING).notNull(true);

		DefaultTable table = new DefaultTable("test1");
		table.registerColumn(stringColumn, numericColumn, dateColumn, timeColumn,
				objectColumn, functionColumn);
	}

	@Test
	public void testIsNumeric() {
		assertTrue(ValueRules.isNumeric("0"));
		assertTrue(ValueRules.isNumeric("1234567890.1234567890"));
		assertTrue(ValueRules.isNumeric("-1234567890"));
		assertFalse(ValueRules.isNumeric(""));
		assertFalse(ValueRules.isNumeric(null));
		assertFalse(ValueRules.isNumeric("abc"));
		assertFalse(ValueRules.isNumeric("-"));
	}

	@Test
	public void testValidatePassesForNumeric() {
		ValueRules.validate(numericColumn, "123");
	}

	@Test
	public void testValidatePassesForEmpty() {
		ValueRules.validate(numericColumn, "");
		ValueRules.validate(numericColumn, null);
	}

	@Test
	public void testValidateThrowsForNonNumeric() {
		try {
			ValueRules.validate(numericColumn, "abc");
			fail();
		} catch (InvalidParameterException e) {
			assertEquals("value is not numeric.", e.getMessage());
		}
	}

	@Test
	public void testValidateIgnoresNonNumericColumns() {
		// STRING column: no exception even for "not numeric" text.
		ValueRules.validate(stringColumn, "abc");
	}

	@Test
	public void testIsRequiredButEmpty() {
		assertTrue(ValueRules.isRequiredButEmpty(notNullColumn, ""));
		assertTrue(ValueRules.isRequiredButEmpty(notNullColumn, null));
		assertFalse(ValueRules.isRequiredButEmpty(notNullColumn, "x"));
		assertFalse(ValueRules.isRequiredButEmpty(stringColumn, ""));
	}

	@Test
	public void testIsNullValueForNumeric() {
		assertTrue(ValueRules.isNullValue(numericColumn, ""));
		assertTrue(ValueRules.isNullValue(numericColumn, null));
		assertFalse(ValueRules.isNullValue(numericColumn, "1"));
	}

	@Test
	public void testIsNullValueForDateTime() {
		assertTrue(ValueRules.isNullValue(dateColumn, ""));
		assertTrue(ValueRules.isNullValue(dateColumn, "NULL"));
		assertTrue(ValueRules.isNullValue(dateColumn, "null"));
		assertFalse(ValueRules.isNullValue(dateColumn, "2015-01-01"));
	}

	@Test
	public void testIsNullValueForObjectAlwaysFalse() {
		assertFalse(ValueRules.isNullValue(objectColumn, null));
		assertFalse(ValueRules.isNullValue(objectColumn, ""));
	}

	@Test
	public void testIsNullValueForStringOnlyNull() {
		assertTrue(ValueRules.isNullValue(stringColumn, null));
		assertFalse(ValueRules.isNullValue(stringColumn, ""));
	}

	@Test
	public void testIsSqlFunction() {
		assertTrue(ValueRules.isSqlFunction(dateColumn, "current_timestamp"));
		assertTrue(ValueRules.isSqlFunction(timeColumn, "CURRENT_TIMESTAMP"));
		assertFalse(ValueRules.isSqlFunction(dateColumn, "2015-01-01"));
		assertFalse(ValueRules.isSqlFunction(stringColumn, "current_timestamp"));
		assertFalse(ValueRules.isSqlFunction(dateColumn, null));
	}

	@Test
	public void testEscapeLikeNoWildcard() {
		LikeEscape le = ValueRules.escapeLike(Condition.LIKE_PART, stringColumn, "tama");
		assertFalse(le.hasEscape());
		assertEquals("%tama%", le.getBoundValue());
	}

	@Test
	public void testEscapeLikeChoosesFirstUnusedCandidate() {
		LikeEscape le = ValueRules.escapeLike(Condition.LIKE_PART, stringColumn, "ta_ma");
		assertTrue(le.hasEscape());
		assertEquals('$', le.getEscapeChar());
		assertEquals("%ta$_ma%", le.getBoundValue());
	}

	@Test
	public void testEscapeLikeSkipsCandidatePresentInValue() {
		LikeEscape le = ValueRules.escapeLike(Condition.LIKE_PART, stringColumn, "$ta_ma");
		assertEquals('#', le.getEscapeChar());
		assertEquals("%$ta#_ma%", le.getBoundValue());
	}

	@Test
	public void testEscapeLikeNullBecomesEmpty() {
		LikeEscape le = ValueRules.escapeLike(Condition.LIKE_PART, stringColumn, null);
		assertFalse(le.hasEscape());
		assertEquals("%%", le.getBoundValue());
	}
}
