package org.tamacat.dao.test;

import org.tamacat.dao.Condition;
import org.tamacat.dao.DaoFactory;
import org.tamacat.dao.Search;
import org.tamacat.dao.Sort;

public class User_test {

	public static void main(String[] args) {
		UserDao dao = DaoFactory.create(UserDao.class);
		try {
			Search search = dao.createSearch();
			search.and(User.AGE, Condition.EQUAL, "';select * from dual --'");
			Sort sort = dao.createSort();
			dao.searchList(search, sort);
		} finally {
			dao.release();
		}
	}
}
