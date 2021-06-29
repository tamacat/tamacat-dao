/*
 * Copyright 2021 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.tx;

import java.util.LinkedHashSet;

import org.tamacat.sql.DBAccessManager;

public class Transaction {

	final LinkedHashSet<String> names = new LinkedHashSet<>();
	final LinkedHashSet<DBAccessManager> dbm = new LinkedHashSet<>();

	public static final Transaction create(String... names) {
		return new Transaction(names);
	}
	
	private Transaction(String... names) {
		for (String name : names) {
			this.names.add(name);
		}
	}
	
	public void begin() {
		for (String name : names) {
			DBAccessManager db = DBAccessManager.getInstance(name);
			dbm.add(db);
		}
	}

	public void commit() {
		for (DBAccessManager db : dbm) {
			db.commit();
		}
	}

	public void rollback() {
		for (DBAccessManager db : dbm) {
			db.rollback();
		}
	}

	public void release() {
		for (DBAccessManager db : dbm) {
			db.release();
		}
	}
}
