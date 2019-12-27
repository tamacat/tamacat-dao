/*
 * Copyright (c) 2008 TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.test;

import org.tamacat.dao.meta.DefaultColumn;
import org.tamacat.dao.meta.DefaultTable;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.orm.MapBasedORMappingBean;

public class UserStat extends MapBasedORMappingBean<UserStat> {

	private static final long serialVersionUID = 1L;

	public static final DefaultTable TABLE = new DefaultTable("user_stat");
	public static final DefaultColumn USER_ID = new DefaultColumn("user_id");
	public static final DefaultColumn TOTAL_SCORE = new DefaultColumn("total_score");

	static {
		USER_ID.type(DataType.STRING).setPrimaryKey(true);
		TOTAL_SCORE.type(DataType.FUNCTION).setFunctionName("sum(score)");
		TABLE.registerColumn(USER_ID, TOTAL_SCORE);
	}
}
