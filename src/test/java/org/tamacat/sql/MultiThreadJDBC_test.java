/*
 * Copyright (c) 2009, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import java.sql.ResultSet;
import java.util.Random;

public class MultiThreadJDBC_test extends Thread {
	
	public static void main(String[] args) {

		for (int i=0; i<5; i++) {
		    MultiThreadJDBC_test test = new MultiThreadJDBC_test();
		    test.start();
		}
	}

	public void run() {
		DBAccessManager dba = DBAccessManager.getInstance("default");
		try {
			ResultSet rs = dba.executeQuery("select 1 from dual");
			long time = new Random().nextInt(5000);
			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {
			}
			DBUtils.close(rs);
		} finally {
			if (dba != null) dba.release();
		}
	}
	
	public static void select() {
		
	}
}
