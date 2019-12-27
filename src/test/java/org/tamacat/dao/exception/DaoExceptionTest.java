package org.tamacat.dao.exception;

import static org.junit.Assert.*;

import org.junit.Test;
import org.tamacat.dao.exception.DaoException;

public class DaoExceptionTest {

	@Test
	public void testDaoExceptionString() {
		DaoException e = new DaoException("TEST ERROR");
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals(null, e.getCause());
	}

	@Test
	public void testDaoExceptionThrowable() {
		Exception cause = new RuntimeException("TEST ERROR");
		DaoException e = new DaoException(cause);
		assertEquals("java.lang.RuntimeException: TEST ERROR", e.getMessage());
		assertSame(cause, e.getCause());
	}

	@Test
	public void testDaoExceptionStringThrowable() {
		Exception cause = new RuntimeException("CAUSE ERROR");
		DaoException e = new DaoException("TEST ERROR", cause);
		assertEquals("TEST ERROR", e.getMessage());
		assertEquals("CAUSE ERROR", e.getCause().getMessage());
	}

}
