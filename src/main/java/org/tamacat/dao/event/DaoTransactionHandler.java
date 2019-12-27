/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.event;

public interface DaoTransactionHandler {

	void handleTransantionStart(DaoEvent event);
	void handleTransantionEnd(DaoEvent event);
	
	void handleBeforeCommit(DaoEvent event);
	void handleAfterCommit(DaoEvent event);
	
	void handleBeforeRollback(DaoEvent event);
	void handleAfterRollback(DaoEvent event);
	
	void handleException(DaoEvent event, Throwable cause);
	
	void handleRelease(DaoEvent event);
}
