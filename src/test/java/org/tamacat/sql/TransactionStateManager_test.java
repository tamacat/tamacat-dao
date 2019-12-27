package org.tamacat.sql;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionStateManager_test {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		TransactionStateManager.getInstance().begin();
		try {
			assertTrue(TransactionStateManager.getInstance().isTransactionStarted());
			
			TransactionStateManager.getInstance().executed();
			//throw new RuntimeException("");
			TransactionStateManager.getInstance().commit();
		} catch (Exception e) {
			TransactionStateManager.getInstance().rollback();
			throw e;
		} finally {
			TransactionStateManager.getInstance().end();
			assertFalse(TransactionStateManager.getInstance().isTransactionStarted());
		}	
	}

}
