/*
 * Copyright (c) 2008, TamaCat.org
 * All rights reserved.
 */
package org.tamacat.dao.orm;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.orm.MapBasedORMappingBean;
import org.tamacat.dao.test.Data;
import org.tamacat.dao.test.User;

public class MapBasedORMappingBeanTest {

	User bean;
	
	@Before
	public void setUp() throws Exception {
		bean = new User();
	}

	@After
	public void tearDown() throws Exception {
		bean.clear();
	}

	@Test
	public void testValColumnString() {
		bean.val(User.USER_ID, "admin");
		assertEquals("admin", bean.val(User.USER_ID));
	}
	
	@Test
	public void testSetValueAndGetValue() {
		bean.setValue(User.USER_ID, "admin");
		assertEquals("admin", bean.getValue(User.USER_ID));
	}

	@Test
	public void testPutStringObject() {
		bean.put("users.user_id", "admin");
		assertEquals("admin", bean.getValue(User.USER_ID));
		assertEquals("admin", bean.get("users.user_id"));
	}

	@Test
	public void testGetObject() {
		bean.val(User.USER_ID, "admin");
		assertEquals("admin", bean.get("users.user_id"));
		assertEquals(null, bean.get("user_id"));

		bean.defaultTableName = "users";
		assertEquals("admin", bean.get("user_id"));
		assertEquals("admin", bean.get("users.user_id"));
	}
	
	@Test
	public void testGetTime() {
		Date date = new Date();
		bean.val(User.UPDATE_DATE, date);
		assertEquals(date, bean.get("users.update_date"));
		assertEquals(null, bean.get("update_date"));

		bean.defaultTableName = "users";
		assertEquals(date, bean.get("users.update_date"));
		assertEquals(date, bean.get("update_date"));
		
		assertEquals(date, bean.get("update_date:date"));
	}
	
	@Test
	public void testMapping() {
		assertEquals("admin", bean.mapping(User.USER_ID, "admin").get("users.user_id"));
		assertEquals("admin", bean.mapping(User.USER_ID, "admin").val(User.USER_ID));
		
		assertEquals("123", bean.mapping(Data.NUM1, 123).get("data.num1"));
		assertEquals("12345678900000", bean.mapping(Data.NUM1, 12345678900000L).get("data.num1"));
		assertEquals("", bean.mapping(Data.NUM1, "").get("data.num1"));
		assertEquals("", bean.mapping(Data.NUM1, null).get("data.num1"));
		
		assertEquals("123456.789", bean.mapping(Data.NUM2, 123456.789d).get("data.num2"));
		assertEquals("1.2345678901234567E8", bean.mapping(Data.NUM2, 123456789.0123456789d).get("data.num2"));
		assertEquals("1.23456789E16", bean.mapping(Data.NUM2, 1.23456789E16).get("data.num2"));
		
		assertEquals("1234567890.000001234567890", bean.mapping(Data.NUM2, new BigDecimal("1234567890.000001234567890")).get("data.num2"));
		
		assertEquals("123", bean.mapping("data.num1", 123).get("data.num1"));
		assertEquals("0", bean.mapping("data.num1", 0).get("data.num1"));
		assertEquals("12345678900000", bean.mapping("data.num1", 12345678900000L).get("data.num1"));
		assertEquals("123.123", bean.mapping("data.num1", 123.123f).get("data.num1"));
		assertEquals("1.2345678901234567E8", bean.mapping("data.num1", 123456789.0123456789d).get("data.num1"));
		assertEquals("", bean.mapping("data.num1", "").get("data.num1"));
		assertEquals("", bean.mapping("data.num1", null).get("data.num1"));
	}

	@Test
	public void testIsUpdate() {
		bean.setValue(User.USER_ID, "admin");
		assertEquals(true, bean.isUpdate(User.USER_ID));
		assertEquals(false, bean.isUpdate(User.PASSWORD));
	}

	@Test
	public void testParse() {
		assertEquals("users.user_id", MapBasedORMappingBean.parse(User.USER_ID));
	}
	
 	@Test
 	public void testGetSize() {
 		assertEquals("100 byte",  bean.getSize("100"));
 		assertEquals("1000 byte", bean.getSize("1000"));
 		assertEquals("9.8 KB",    bean.getSize("10000"));
		assertEquals("97.7 KB",   bean.getSize("100000"));
		assertEquals("976.6 KB",  bean.getSize("1000000"));
		assertEquals("9.5 MB",    bean.getSize("10000000"));
		assertEquals("95.4 MB",   bean.getSize("100000000"));
		assertEquals("953.7 MB",  bean.getSize("1000000000"));
		assertEquals("9.3 GB",    bean.getSize("10000000000"));
 	}
 	
 	@Test
 	public void testFloatParse() {
 		Data data = new Data();
 		data.val(Data.NUM1, 100);
 		data.val(Data.NUM2, 100.123456789d);
 		
 		assertEquals("100", data.val(Data.NUM1));
 		assertEquals("100.123456789", data.val(Data.NUM2));
 		
 		data.val(Data.NUM2, 1000000.00123456789d);
 		assertEquals("1000000.0012345678", data.val(Data.NUM2));
 		
 		data.val(Data.NUM2, new BigDecimal("1234567890.000000123456789"));
 		assertEquals("1234567890.000000123456789", data.val(Data.NUM2));
 	}
 	
 	@Test
 	public void testDate() {
 		long time = 1500000000000L;
 		Data data = new Data();
 		data.val(Data.UPDATE_DATE, new Date(time));
 		
 		Date date = data.date(Data.UPDATE_DATE);
 		assertEquals(time, date.getTime());
 	}
}
