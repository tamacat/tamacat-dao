/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

import java.io.InputStream;

import org.tamacat.dao.exception.InvalidParameterException;
import org.tamacat.dao.meta.DataType;

/**
 * A single bind value carried alongside a {@code ?} placeholder.
 *
 * <p>A {@code BindValue} always takes exactly one of three forms:
 * <ul>
 *   <li>text value ({@link #of(DataType, String)}) - a non-null string value</li>
 *   <li>stream value ({@link #ofStream(InputStream)}) - a binary ({@code DataType.OBJECT}) value</li>
 *   <li>NULL value ({@link #ofNull(DataType)}) - to be bound as SQL NULL</li>
 * </ul>
 *
 * <p>Instances are immutable (fields are final and never reassigned), except that the
 * {@link InputStream} held by a stream value is itself mutable and can only be read once.
 * A {@code BindValue} that wraps a stream must not be bound more than once.
 *
 * @since 2.0
 */
public final class BindValue {

	private final DataType type;
	private final String value;
	private final InputStream stream;
	private final boolean nullValue;

	private BindValue(DataType type, String value, InputStream stream, boolean nullValue) {
		this.type = type;
		this.value = value;
		this.stream = stream;
		this.nullValue = nullValue;
	}

	/**
	 * Creates a text bind value.
	 * @param type the value's type. Must not be {@code null}
	 * @param value the value's string representation. Must not be {@code null} -
	 *        use {@link #ofNull(DataType)} for SQL NULL
	 * @return a new text {@code BindValue}
	 * @throws InvalidParameterException if {@code type} or {@code value} is {@code null}
	 */
	public static BindValue of(DataType type, String value) {
		if (type == null) {
			throw new InvalidParameterException("type is required.");
		}
		if (value == null) {
			throw new InvalidParameterException("value is required.");
		}
		return new BindValue(type, value, null, false);
	}

	/**
	 * Creates a stream (BLOB) bind value. The type is fixed to {@code DataType.OBJECT}.
	 * @param in the binary content. Must not be {@code null}
	 * @return a new stream {@code BindValue}
	 * @throws InvalidParameterException if {@code in} is {@code null}
	 */
	public static BindValue ofStream(InputStream in) {
		if (in == null) {
			throw new InvalidParameterException("in is required.");
		}
		return new BindValue(DataType.OBJECT, null, in, false);
	}

	/**
	 * Creates a SQL NULL bind value.
	 * @param type the value's type. Must not be {@code null}
	 * @return a new NULL {@code BindValue}
	 * @throws InvalidParameterException if {@code type} is {@code null}
	 */
	public static BindValue ofNull(DataType type) {
		if (type == null) {
			throw new InvalidParameterException("type is required.");
		}
		return new BindValue(type, null, null, true);
	}

	public DataType getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public InputStream getStream() {
		return stream;
	}

	public boolean isNull() {
		return nullValue;
	}
}
