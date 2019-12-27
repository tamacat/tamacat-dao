package org.tamacat.sql;

import static org.junit.Assert.*;

import org.junit.Test;

public class IllegalTransactionStateExceptionTest {

	@Test
	public void testIllegalTransactionStateExceptionStringThrowable() {
		IllegalTransactionStateException e = new IllegalTransactionStateException("TEST ERROR");
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals(null, e.getCause());
	}

	@Test
	public void testIllegalTransactionStateExceptionString() {
		Exception cause = new RuntimeException("TEST ERROR");
		IllegalTransactionStateException e = new IllegalTransactionStateException(cause);
		assertEquals("java.lang.RuntimeException: TEST ERROR", e.getMessage());
		assertSame(cause, e.getCause());
	}

	@Test
	public void testIllegalTransactionStateExceptionThrowable() {
		Exception cause = new RuntimeException("CAUSE ERROR");
		IllegalTransactionStateException e = new IllegalTransactionStateException("TEST ERROR", cause);
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals("CAUSE ERROR", e.getCause().getMessage());
	}

}
