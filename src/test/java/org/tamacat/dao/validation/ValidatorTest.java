package org.tamacat.dao.validation;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.exception.InvalidValueLengthException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.test.Data;
import org.tamacat.util.StringUtils;

public class ValidatorTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testValidate() {
		Data data = new Data();
		data.val(Data.NAME.maxLength(15), "1234567890");		
		try {
			data.val(Data.NAME.maxLength(5), "1234567890");
			fail();
		} catch (InvalidValueLengthException e) {
			assertTrue(e.getColumn().equals(Data.NAME));
		}
		
		try {
			Data.NAME.set(new DataValidator());
			data.val(Data.NAME, "");
			fail();
		} catch (InvalidParameterException e) {
			assertEquals("name is empty.", e.getMessage());
		}
	}
	
	static class DataValidator implements Validator {
		
		@Override
		public Validator validate(Column column, Object value) {
			if (column.equals(Data.NAME)) {
				validateName(value);
			}
//			String value = data.val(col);
//			if (value != null && value.length() > col.getMaxLength()) {
//				throw new InvalidValueLengthException(
//					"The column ["+column.getColumnName() +"] exceeds the maximum length of character. (" + column.getMaxLength() + ")");
//			}
//			if (column.isNotNull() && data.isEmpty(column)) {
//				throw new InvalidParameterException(column.getColumnName() +" is null.");
//			}
			return this;
		}
		
		void validateName(Object value) {
			if (StringUtils.isEmpty(value)) {
				throw new InvalidParameterException("name is empty.");
			}
		}
	}
}
