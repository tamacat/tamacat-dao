/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.BindValue;
import org.tamacat.dao.Condition;
import org.tamacat.dao.Param;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.Columns;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.meta.DefaultTable;

public class BindSqlBuilderTest {

	Column stringColumn;
	Column numericColumn;
	Column dateColumn;
	Column objectColumn;
	Column functionColumn;
	BindSqlBuilder builder;

	@Before
	public void setUp() {
		stringColumn = Columns.create("name").type(DataType.STRING);
		numericColumn = Columns.create("id").type(DataType.NUMERIC);
		dateColumn = Columns.create("date").type(DataType.DATE);
		objectColumn = Columns.create("data").type(DataType.OBJECT);
		functionColumn = Columns.create("fn").type(DataType.FUNCTION);

		DefaultTable table = new DefaultTable("test1");
		table.registerColumn(stringColumn, numericColumn, dateColumn, objectColumn, functionColumn);

		builder = new BindSqlBuilder();
	}

	@Test
	public void testSingleValueBindsPlaceholder() {
		Param p = builder.value(stringColumn, Condition.EQUAL, "tama");
		assertEquals("test1.name=?", p.getSql());
		assertEquals(1, p.size());
		assertEquals("tama", p.getValues().get(0).getValue());
	}

	@Test
	public void testQuoteIsNotDoubledOnBindPath() { // AC-6 / BR-18
		Param p = builder.value(stringColumn, Condition.EQUAL, "O'Brien");
		assertEquals("test1.name=?", p.getSql());
		assertEquals("O'Brien", p.getValues().get(0).getValue()); // NOT O''Brien
		// contrast: the literal path still doubles the quote
		assertEquals("test1.name='O''Brien'",
			new SQLParser().value(stringColumn, Condition.EQUAL, "O'Brien"));
	}

	@Test
	public void testSingleValueNullBecomesEmptyString() {
		// mirrors SQLParser's BR-5: null on the single-value path becomes ''.
		Param p = builder.value(stringColumn, Condition.EQUAL, (String) null);
		assertEquals("test1.name=?", p.getSql());
		assertFalse(p.getValues().get(0).isNull());
		assertEquals("", p.getValues().get(0).getValue());
	}

	@Test
	public void testLikeEscapesAndBindsWrappedValue() {
		Param p = builder.value(stringColumn, Condition.LIKE_PART, "ta_ma");
		assertEquals("test1.name like ? escape '$'", p.getSql());
		assertEquals(1, p.size());
		assertEquals("%ta$_ma%", p.getValues().get(0).getValue());
	}

	@Test
	public void testLikeWithoutWildcardHasNoEscapeClause() {
		Param p = builder.value(stringColumn, Condition.LIKE_PART, "tama");
		assertEquals("test1.name like ?", p.getSql());
		assertEquals("%tama%", p.getValues().get(0).getValue());
	}

	@Test
	public void testInWithMultipleValues() {
		Param p = builder.value(stringColumn, Condition.IN, "abc", "def", "xyz");
		assertEquals("test1.name in (?,?,?)", p.getSql());
		assertEquals(3, p.size());
		assertEquals("abc", p.getValues().get(0).getValue());
		assertEquals("xyz", p.getValues().get(2).getValue());
	}

	@Test
	public void testInWithNullBindsSqlNull() {
		// mirrors BR-6: multi-value path does NOT substitute null -> ''.
		Param p = builder.value(stringColumn, Condition.IN, (String) null);
		assertEquals("test1.name in (?)", p.getSql());
		assertTrue(p.getValues().get(0).isNull());
	}

	@Test
	public void testBetweenWithTwoValues() {
		Param p = builder.value(numericColumn, Condition.BETWEEN, "100", "200");
		assertEquals("test1.id between ? and ?", p.getSql());
		assertEquals(2, p.size());
		assertEquals("100", p.getValues().get(0).getValue());
		assertEquals("200", p.getValues().get(1).getValue());
	}

	@Test
	public void testObjectColumnBindsNullPlaceholder() {
		Param p = builder.value(objectColumn, Condition.EQUAL, "ignored");
		assertEquals("test1.data=?", p.getSql());
		assertEquals(1, p.size());
		assertTrue(p.getValues().get(0).isNull());
		assertEquals(DataType.OBJECT, p.getValues().get(0).getType());
	}

	@Test
	public void testFunctionColumnEmitsLiteralTextNoBind() {
		Param p = builder.value(functionColumn, Condition.EQUAL, "now()");
		assertEquals("test1.fn=now()", p.getSql());
		assertEquals(0, p.size());
	}

	@Test
	public void testCurrentTimestampEmitsLiteralTextNoBind() {
		Param p = builder.value(dateColumn, Condition.EQUAL, "current_timestamp");
		assertEquals("test1.date=current_timestamp", p.getSql());
		assertEquals(0, p.size());
	}

	@Test
	public void testNumericValidationRejectsNonNumeric() {
		try {
			builder.value(numericColumn, Condition.EQUAL, "abc");
			fail();
		} catch (InvalidParameterException e) {
			assertEquals("value is not numeric.", e.getMessage());
		}
	}

	@Test
	public void testPlaceholderForInsertValues() {
		Param p = builder.placeholder(stringColumn, "tama");
		assertEquals("?", p.getSql());
		assertEquals(1, p.size());
		assertEquals("tama", p.getValues().get(0).getValue());
	}

	@Test
	public void testPlaceholderDoesNotSubstituteNull() {
		// unlike value(...), placeholder(...) has no VALUE1 replaceHolder to apply BR-5 to.
		Param p = builder.placeholder(stringColumn, null);
		assertEquals("?", p.getSql());
		assertTrue(p.getValues().get(0).isNull());
	}

	@Test
	public void testSqlFunctionEmitsLiteralNoBind() {
		Param p = builder.sqlFunction(dateColumn, "sysdate");
		assertEquals("sysdate", p.getSql());
		assertTrue(p.getValues().isEmpty());
	}

	@Test
	public void testSqlFunctionRejectsPlaceholderInFunctionText() {
		try {
			builder.sqlFunction(dateColumn, "?");
			fail();
		} catch (InvalidParameterException e) {
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void testKnownDefectBetweenWithThreeValuesThrows() {
		// preserved defect: VALUES has only 2 elements (business-logic-model.md sec 4.3, #1).
		try {
			builder.value(numericColumn, Condition.BETWEEN, "1", "2", "3");
			fail();
		} catch (ArrayIndexOutOfBoundsException e) {
			// expected - preserved current behavior
		}
	}

	@Test
	public void testKnownDefectBetweenWithOneValueLeavesUnreplacedTemplate() {
		// preserved defect: #{value2} remains unreplaced in the text.
		Param p = builder.value(stringColumn, Condition.BETWEEN, "a");
		assertTrue(p.getSql().indexOf("#{value2}") >= 0);
	}

	@Test
	public void testKnownDefectIsNullWithValueThrowsNpe() {
		// preserved defect: IS_NULL has no replaceHolder template.
		try {
			builder.value(stringColumn, Condition.IS_NULL, "x");
			fail();
		} catch (NullPointerException e) {
			// expected - preserved current behavior
		}
	}
}
