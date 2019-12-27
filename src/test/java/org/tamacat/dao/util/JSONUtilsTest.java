package org.tamacat.dao.util;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

import javax.json.Json;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.test.User;

public class JSONUtilsTest {

	@Before
	public void setUp() throws Exception {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testToString() {
		User bean = new User();
		bean.val(User.USER_ID, "guest");		
		String json = JSONUtils.toString(bean, User.TABLE.columns());
		assertEquals("{\"user_id\":\"guest\",\"password\":\"\",\"dept_id\":\"\",\"update_date\":\"\",\"age\":\"\"}", json);
	}

	@Test
	public void testToStringArray() {
		ArrayList<User> list = new ArrayList<>();
		User bean = new User();
		bean.val(User.USER_ID, "guest");
		list.add(bean);
		
		String json = JSONUtils.toString(list, User.TABLE.columns());
		assertEquals("[{\"user_id\":\"guest\",\"password\":\"\",\"dept_id\":\"\",\"update_date\":\"\",\"age\":\"\"}]", json);
	}
	
	@Test
	/**
	 * Date -> long
	 */
	public void testParse() {
		String json = "{\"user_id\":\"guest\",\"password\":\"PASSWORD\",\"dept_id\":\"TEST DEPT\",\"update_date\":1483233825000,\"age\":\"20\"}";
		User bean = new User();
		//User.UPDATE_DATE.format(null);
		JSONUtils.parse(bean, Json.createParser(new StringReader(json)), User.TABLE.columns());
		String result = "{\"user_id\":\"guest\",\"password\":\"PASSWORD\",\"dept_id\":\"TEST DEPT\",\"update_date\":1483233825000,\"age\":20}";
		assertEquals(result, bean.toJson(User.TABLE.columns()).build().toString());
		assertEquals(bean.val(User.USER_ID), "guest");
		assertEquals(bean.val(User.PASSWORD), "PASSWORD");
		assertEquals(bean.val(User.DEPT_ID), "TEST DEPT");
		assertEquals(bean.val(User.AGE), "20");
		assertEquals(bean.date(User.UPDATE_DATE), new Date(1483233825000L));
	}
	
	@Test
	/**
	 * Date format "yyyy-MM-dd HH:mm:ss.SSS"
	 */
	public void testParse_TIME_msec() {
		String json = "{\"user_id\":\"guest\",\"password\":\"PASSWORD\",\"dept_id\":\"TEST DEPT\",\"update_date\":1483233825678,\"age\":20}";
		User bean = new User();
		//User.UPDATE_DATE.format("yyyy-MM-dd HH:mm:ss.SSS");
		//System.out.println(bean.TABLE.find("update_date").getFormat());
		JSONUtils.parse(bean, Json.createParser(new StringReader(json)), User.TABLE.columns());
		
		//{"user_id":"guest","password":"PASSWORD","dept_id":"TEST DEPT","update_date":1483201425000,"age":20}
		assertEquals(json, bean.toJson(User.TABLE.columns()).build().toString());

		assertEquals(bean.val(User.USER_ID), "guest");
		assertEquals(bean.val(User.PASSWORD), "PASSWORD");
		assertEquals(bean.val(User.DEPT_ID), "TEST DEPT");
		assertEquals(bean.val(User.AGE), "20");
	}
	
	@Test
	/**
	 * Date format "yyyy-MM-dd HH:mm:ss.S"
	 */
	public void testParse_TIME_msec2() {
		String json = "{\"user_id\":\"guest\",\"password\":\"PASSWORD\",\"dept_id\":\"TEST DEPT\",\"update_date\":1483233825000,\"age\":20}";
		User bean = new User();
		//User.UPDATE_DATE.format("yyyy-MM-dd HH:mm:ss.S");
		JSONUtils.parse(bean, Json.createParser(new StringReader(json)), User.TABLE.columns());
		//{"user_id":"guest","password":"PASSWORD","dept_id":"TEST DEPT","update_date":1483201425000,"age":20}
		assertEquals(json, bean.toJson(User.TABLE.columns()).build().toString());

		assertEquals(bean.val(User.USER_ID), "guest");
		assertEquals(bean.val(User.PASSWORD), "PASSWORD");
		assertEquals(bean.val(User.DEPT_ID), "TEST DEPT");
		assertEquals(bean.val(User.AGE), "20");
		//User.UPDATE_DATE.format(null);
	}
	
	@Test
	public void testParse_DATE() {
		User bean = new User();
		
		bean.val(User.UPDATE_DATE, new Date(1500000000000L));
		String json = bean.toJson(User.UPDATE_DATE).build().toString();
		assertEquals("{\"update_date\":1500000000000}", json);
		assertEquals("2017-07-14 02:40:00.000", bean.val(User.UPDATE_DATE));
		
		//{"update_date":1500000000000}
		
		User.parse(bean); //TODO rename
		
		//User.UPDATE_DATE.format("yyyy-MM-dd HH:mm:ss.SSS");
		assertEquals("{\"update_date\":1500000000000}", bean.toJson(User.UPDATE_DATE).build().toString());
		//{"update_date":"2017-07-14 11:40:00.000"}
		
		//User.UPDATE_DATE.format(null); //reset
	}
	
	@Test
	public void testParseArray() {
		String json = "[{\"user_id\":\"user01\"},{\"user_id\":\"user02\"}]";
		
		Collection<User> list = JSONUtils.parseArray(Json.createParser(new StringReader(json)), User.class, User.TABLE.columns());
		if (list.size() == 2) {
			Iterator<User> it = list.iterator();
			assertEquals("user01", it.next().val(User.USER_ID));
			assertEquals("user02", it.next().val(User.USER_ID));
		}
	}
}
