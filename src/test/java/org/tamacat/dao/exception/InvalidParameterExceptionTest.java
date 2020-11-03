package org.tamacat.dao.exception;

import static org.junit.Assert.*;

import org.junit.Test;

public class InvalidParameterExceptionTest {

	@Test
	public void testRdbInvalidParameterException() {
		InvalidParameterException e = new InvalidParameterException();
		assertEquals(null, e.getMessage());
		assertEquals(null, e.getCause());
	}

	@Test
	public void testRdbInvalidParameterExceptionString() {
		InvalidParameterException e = new InvalidParameterException("TEST ERROR");
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals(null, e.getCause());
	}

	@Test
	public void testRdbInvalidParameterExceptionThrowable() {
		Exception cause = new RuntimeException("TEST ERROR");
		InvalidParameterException e = new InvalidParameterException(cause);
		assertEquals("java.lang.RuntimeException: TEST ERROR", e.getMessage());
		assertSame(cause, e.getCause());
	}

	@Test
	public void testRdbInvalidParameterExceptionStringThrowable() {
		Exception cause = new RuntimeException("CAUSE ERROR");
		InvalidParameterException e = new InvalidParameterException("TEST ERROR", cause);
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals("CAUSE ERROR", e.getCause().getMessage());
	}
}
