/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.event;

public interface DaoEvent {
	
	Class<?> getCallerDao();
	
	void setQuery(String query);
	
	String getQuery();
	
	void setResult(int result);
	
	int getResult();
}
