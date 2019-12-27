/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransactionStateManagerTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testCommit1() throws Exception {
		transactionCase1(null);
	}
	
	@Test
	public void testCommit2() throws Exception {
		transactionCase2(null);
	}
	
	@Test
	public void testRollback1() {
		try {
			transactionCase1(new RuntimeException());
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof RuntimeException);
		}
	}

	@Test
	public void testRollback2() {
		try {
			transactionCase2(new RuntimeException());
			fail();
		} catch (Exception e) {
			assertTrue(e instanceof RuntimeException);
		}
	}
	
	@Test
	public void testForceRollback() {
		try {
			transactionCase3(new RuntimeException());
			fail();
		} catch (Exception e) {
			//e.printStackTrace();
			assertTrue(e instanceof RuntimeException);
		}
	}
	
	void transactionCase1(Exception error) throws Exception {
		assertFalse(TransactionStateManager.getInstance().isTransactionStarted());
		TransactionStateManager.getInstance().begin();
		try {
			assertTrue(TransactionStateManager.getInstance().isTransactionStarted());
			
			TransactionStateManager.getInstance().executed();
			
			if (error != null) throw error;
			TransactionStateManager.getInstance().commit();
		} catch (Exception e) {
			TransactionStateManager.getInstance().rollback();
			throw e;
		} finally {
			TransactionStateManager.getInstance().end();
			assertFalse(TransactionStateManager.getInstance().isTransactionStarted());
		}
	}
	
	void transactionCase2(Exception error) throws Exception {
		TransactionStateManager tran = new TransactionStateManager();
		assertFalse(tran.isTransactionStarted());
		tran.begin();
		try {
			assertTrue(tran.isTransactionStarted());
			tran.executed();
			
			if (error != null) throw error;
			tran.commit();
		} catch (Exception e) {
			tran.rollback();
			throw e;
		} finally {
			tran.end();
			assertFalse(tran.isTransactionStarted());
		}
	}
	
	void transactionCase3(Exception error) throws Exception {
		TransactionStateManager tran = new TransactionStateManager();
		assertFalse(tran.isTransactionStarted());
		tran.begin();
		try {
			tran.executed();
			assertTrue(tran.isTransactionStarted());
			//if (error != null) throw error;
			//tran.commit();
		} catch (Exception e) {
			tran.rollback();
			throw e;
		} finally {
			if (tran.isNotCommited()) {
				throw new IllegalStateException("Transaction is not commit or rollback. Force execute rollback.");
			}
			tran.end();
			assertFalse(tran.isTransactionStarted());
		}
	}
}
