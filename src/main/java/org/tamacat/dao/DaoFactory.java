/*
 * Copyright (c) 2009, tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import org.tamacat.di.DI;
import org.tamacat.di.DIContainer;
import org.tamacat.util.ClassUtils;

public class DaoFactory {
	
	public static <T>T getDao(Class<T> type) {
		return ClassUtils.newInstance(type);
	}
	
	public static <T extends DaoAdapter<?>>T create(Class<T> type) {
		return ClassUtils.newInstance(type);
	}
	
	public static <T extends DaoAdapter<?>>T create(Class<T> type, DaoAdapter<?> parent) {
		T dao = ClassUtils.newInstance(type);
		if (dao != null && parent != null) {
			dao.setDatabase(parent.getDatabase());
		}
		return dao;
	}
	
	/**
	 * Get the Dao object from orm.xml
	 * @param id
	 */
	@SuppressWarnings("unchecked")
	public static <T>T getDao(String id) {
		DIContainer di = DI.configure("orm.xml");
		return (T) di.getBean(id);
	}
}
