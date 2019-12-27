package org.tamacat.pool;

import static org.junit.Assert.*;

import org.junit.Test;

public class ObjectActivateExceptionTest {

	@Test
	public void testObjectActivateException() {
		ObjectActivateException e = new ObjectActivateException();
		assertEquals(null, e.getMessage());
		assertEquals(null, e.getCause());
	}

	@Test
	public void testObjectActivateExceptionString() {
		ObjectActivateException e = new ObjectActivateException("TEST ERROR");
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals(null, e.getCause());
	}

	@Test
	public void testObjectActivateExceptionThrowable() {
		Exception cause = new RuntimeException("TEST ERROR");
		ObjectActivateException e = new ObjectActivateException(cause);
		assertEquals("java.lang.RuntimeException: TEST ERROR", e.getMessage());
		assertSame(cause, e.getCause());
	}

	@Test
	public void testObjectActivateExceptionStringThrowable() {
		Exception cause = new RuntimeException("CAUSE ERROR");
		ObjectActivateException e = new ObjectActivateException("TEST ERROR", cause);
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals("CAUSE ERROR", e.getCause().getMessage());
	}

}
