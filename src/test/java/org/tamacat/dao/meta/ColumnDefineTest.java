package org.tamacat.dao.meta;

import static org.junit.Assert.*;

import org.junit.Test;
import org.tamacat.dao.meta.ColumnDefine;

public class ColumnDefineTest {

	@Test
	public void testHashCode() {
		ColumnDefine pk = new ColumnDefine("primary key");

		assertEquals(-869111968, pk.hashCode());
		assertEquals(-869111968, ColumnDefine.PRIMARY_KEY.hashCode());
		assertEquals(31, new ColumnDefine(null).hashCode());
	}

	@Test
	public void testGetDefineName() {
		ColumnDefine pk = new ColumnDefine("primary key");
		assertEquals("primary key", pk.getDefineName());
	}

	@Test
	public void testEqualsObject() {
		ColumnDefine pk = new ColumnDefine("primary key");
		ColumnDefine nn = new ColumnDefine("not null");

		assertEquals(true, ColumnDefine.PRIMARY_KEY.equals(pk));
		assertEquals(true, pk.equals(pk));
		assertEquals(true, new ColumnDefine(null).equals(new ColumnDefine(null)));

		assertEquals(false, pk.equals(nn));
		assertEquals(false, pk.equals(null));
		assertEquals(false, pk.equals(new ColumnDefine(null)));
		assertEquals(false, new ColumnDefine(null).equals(pk));
	}
}
