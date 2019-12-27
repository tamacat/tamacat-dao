package org.tamacat.dao.event;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.impl.NoneDaoTransactionHandler;

public class NoneDaoTransactionHandlerTest {
	NoneDaoTransactionHandler handler;
	
	@Before
	public void setUp() throws Exception {
		handler = new NoneDaoTransactionHandler();
	}
	
	@Test
	public void testHandleAfterCommit() {
		handler.handleAfterCommit(null);
	}

	@Test
	public void testHandleAfterRollback() {
		handler.handleAfterRollback(null);
	}

	@Test
	public void testHandleBeforeCommit() {
		handler.handleBeforeCommit(null);
	}

	@Test
	public void testHandleBeforeRollback() {
		handler.handleBeforeRollback(null);
	}

	@Test
	public void testHandleException() {
		RuntimeException cause = new RuntimeException("TEST ERROR");
		handler.handleException(null, cause);
	}

	@Test
	public void testHandleTransantionEnd() {
		handler.handleTransantionEnd(null);
	}

	@Test
	public void testHandleTransantionStart() {
		handler.handleTransantionStart(null);
	}

	@Test
	public void testHandleRelease() {
		handler.handleRelease(null);
	}

}
