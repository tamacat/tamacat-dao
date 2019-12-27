/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.event;

public interface DaoExecuteHandler {

	void handleBeforeExecuteQuery(DaoEvent event);
	void handleAfterExecuteQuery(DaoEvent event);
	
	void handleBeforeExecuteUpdate(DaoEvent event);
	int handleAfterExecuteUpdate(DaoEvent event);
}
