/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tamacat.dao.exception.InvalidParameterException;

public class IdentifierRulesTest {

	@Test
	// BR-14 / IR-3: null is passed through without throwing.
	public void testValidate_Null_PassesThrough() {
		assertNull(IdentifierRules.validate(null));
	}

	@Test
	// ID-1: single quote.
	public void testValidate_SingleQuote_Throws() {
		try {
			IdentifierRules.validate("col'name");
			fail();
		} catch (InvalidParameterException e) {
			assertEquals("Unsafe identifier: [col'name]", e.getMessage());
		}
	}

	@Test
	// ID-2: double quote.
	public void testValidate_DoubleQuote_Throws() {
		try {
			IdentifierRules.validate("col\"name");
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-3: semicolon.
	public void testValidate_Semicolon_Throws() {
		try {
			IdentifierRules.validate("col;DROP TABLE users");
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-4: line comment.
	public void testValidate_LineComment_Throws() {
		try {
			IdentifierRules.validate("col--comment");
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-5: block comment open.
	public void testValidate_BlockCommentOpen_Throws() {
		try {
			IdentifierRules.validate("col/*comment*/");
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// ID-5: block comment close alone also rejected.
	public void testValidate_BlockCommentClose_Throws() {
		try {
			IdentifierRules.validate("col*/name");
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}

	@Test
	// BR-11: legitimate compound expressions with no dangerous literal pass through unchanged.
	public void testValidate_LegitimateExpressions_PassThrough() {
		assertEquals("RAND()", IdentifierRules.validate("RAND()"));
		assertEquals("COUNT(*)", IdentifierRules.validate("COUNT(*)"));
		assertEquals("t1.col1, t2.col2", IdentifierRules.validate("t1.col1, t2.col2"));
	}

	@Test
	// BR-11 / R-6: an expression with a string literal (containing ') is rejected -
	// documented narrowing of the existing flexibility.
	public void testValidate_ExpressionWithStringLiteral_Throws() {
		try {
			IdentifierRules.validate("TO_CHAR(d,'YYYY')");
			fail();
		} catch (InvalidParameterException e) {
			// expected
		}
	}
}
