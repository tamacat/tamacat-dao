/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import org.tamacat.dao.event.DaoEvent;
import org.tamacat.dao.event.DaoExecuteHandler;

public class NoneDaoExecuteHandler implements DaoExecuteHandler {

	@Override
	public void handleAfterExecuteQuery(DaoEvent event) {
	}

	@Override
	public int handleAfterExecuteUpdate(DaoEvent event) {
		if (event == null) return 0;
		else return event.getResult();
	}

	@Override
	public void handleBeforeExecuteQuery(DaoEvent event) {
	}

	@Override
	public void handleBeforeExecuteUpdate(DaoEvent event) {
	}
}
