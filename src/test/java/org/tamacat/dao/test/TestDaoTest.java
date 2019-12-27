package org.tamacat.dao.test;

import static org.junit.Assert.*;

import org.junit.Test;
import org.tamacat.dao.DaoFactory;
import org.tamacat.dao.Query;

public class TestDaoTest {

	@Test
	public void test() {
		TestDao dao = DaoFactory.create(TestDao.class);
		assertNotNull(dao);
		Query<User> query = dao.createQuery().select(User.TABLE.columns());
		dao.search(query);
		assertEquals(
			"SELECT users.user_id,users.password,users.dept_id,users.update_date,users.age FROM users",
			dao.getExecutedQuery().get(0)
		);
		dao.release();
	}

}
