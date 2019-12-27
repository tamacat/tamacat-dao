package org.tamacat.dao.util;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;

import org.tamacat.dao.meta.Column;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.orm.MapBasedORMappingBean;
import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.util.ClassUtils;
import org.tamacat.util.CollectionUtils;
import org.tamacat.util.DateUtils;
import org.tamacat.util.StringUtils;

public class JSONUtils {

	static final Log LOG = LogFactory.getLog(JSONUtils.class);
	
	public static String toString(MapBasedORMappingBean<?> bean, Column... columns) {
		return json(bean, columns).build().toString();
	}
	
	public static String toString(Collection<? extends MapBasedORMappingBean<?>> list, Column... columns) {
		return json(list, columns).build().toString();
	}
	
	public static JsonObjectBuilder json(MapBasedORMappingBean<?> bean, Column... columns) {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		for (Column col : columns) {
			String value = bean.val(col);
			if (value == null) value = "";
			builder.add(col.getColumnName(), value);
		}
		return builder;
	}

	/**
	 * Not included empty value.
	 * @since 1.4
	 * @param bean
	 * @param columns
	 */
	public static JsonObjectBuilder toJson(MapBasedORMappingBean<?> bean, Column... columns) {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		for (Column col : columns) {
			String value = bean.val(col);
			if (value != null && value.length()>0) {
				if (col.getType()==DataType.NUMERIC) {
					builder.add(col.getColumnName(), StringUtils.parse(value, 0L));
				} else if (col.getType()==DataType.FLOAT) {
					builder.add(col.getColumnName(), StringUtils.parse(value, 0d));
				} else if (col.getType()==DataType.TIME) {
					String format = col.getFormat();					
					if (StringUtils.isNotEmpty(format)) {
						Date d = DateUtils.parse(value, format);
						if (d != null) {
							builder.add(col.getColumnName(), d.getTime()); //DateUtils.getTime(d, format));
						} else {
							builder.add(col.getColumnName(), value);
						}
					} else {
						if (value.indexOf('.')>0) {
							builder.add(col.getColumnName(), DateUtils.parse(value, "yyyy-MM-dd HH:mm:ss.SSS").getTime());
						} else {
							builder.add(col.getColumnName(), DateUtils.parse(value, "yyyy-MM-dd HH:mm:ss").getTime());
						}
					}
				} else if (col.getType()==DataType.DATE) {
					builder.add(col.getColumnName(), DateUtils.parse(value, "yyyy-MM-dd").getTime());
				} else {
					builder.add(col.getColumnName(), value);
				}
			}
		}
		return builder;
	}
	
	public static JsonArrayBuilder json(Collection<? extends MapBasedORMappingBean<?>> list, Column... columns) {
		JsonArrayBuilder builder = Json.createArrayBuilder();
		for (MapBasedORMappingBean<?> bean : list) {
			builder.add(json(bean, columns));
		}
		return builder;
	}
	
	public static <T extends MapBasedORMappingBean<?>> T parse(T bean, JsonParser parser, Column... columns) {
		Map<String, Column> colmaps = CollectionUtils.newLinkedHashMap();
		for (Column col : columns) {
			colmaps.put(col.getColumnName(), col);
		}
		Column col = null;
		while (parser.hasNext()) {
			Event event = parser.next();
			switch (event) {
				case KEY_NAME:
					String key = parser.getString();
					if (StringUtils.isNotEmpty(key)) {
						col = colmaps.get(key);
					}
					break;
				case VALUE_STRING:
					if (col != null) {
						bean.val(col, parser.getString());
						col = null;
					}
					break;
				case VALUE_TRUE:
					if (col != null) {
						bean.val(col, true);
						col = null;
					}
					break;
				case VALUE_FALSE:
					if (col != null) {
						bean.val(col, false);
						col = null;
					}
					break;
				case VALUE_NUMBER:
					if (col != null) {
						long value = parser.getLong();
						if (col.getType() == DataType.TIME || col.getType() == DataType.DATE) {
							bean.val(col, new Date(value));
						} else {
							bean.val(col, value);
						}
						col = null;
					}
					break;
				case VALUE_NULL:
					if (col != null) {
						bean.val(col, "");
						col = null;
					}
					break;
				case START_ARRAY:
				case END_ARRAY:
				case START_OBJECT:
				case END_OBJECT:
					break;
				default:
					LOG.warn("JSON parser unknown event: "+event);
					col = null;
					break;
			}
		}
		return bean;
	}
	
	public static <T extends MapBasedORMappingBean<?>> Collection<T> parseArray(JsonParser parser, Class<T> type, Column... columns) {
		Collection<T> list = CollectionUtils.newArrayList();
		Map<String, Column> colmaps = CollectionUtils.newLinkedHashMap();
		for (Column col : columns) {
			colmaps.put(col.getColumnName(), col);
		}
		T data = null;
		Column col = null;
		while (parser.hasNext()) {
			Event event = parser.next();
			switch (event) {
				case KEY_NAME:
					String key = parser.getString();
					if (StringUtils.isNotEmpty(key)) {
						col = colmaps.get(key);
					}
					break;
				case VALUE_STRING:
					if (col != null) {
						String value = parser.getString();
						data.val(col, value);
						LOG.trace("    \""+col.getColumnName()+"\":\""+value+"\"");
						col = null;
					}
					break;
				case VALUE_TRUE:
					if (col != null) {
						data.val(col, true);
						LOG.trace("    \""+col.getColumnName()+"\":true");
						col = null;
					}
					break;
				case VALUE_FALSE:
					if (col != null) {
						data.val(col, false);
						LOG.trace("    \""+col.getColumnName()+"\":false");
						col = null;
					}
					break;
				case VALUE_NUMBER:
					if (col != null) {
						long value = parser.getLong();
						if (col.getType() == DataType.TIME || col.getType() == DataType.DATE) {
							data.val(col, new Date(value));
						} else {
							data.val(col, value);
						}
						LOG.trace("    \""+col.getColumnName()+"\":\""+value+"\"");
						col = null;
					}
					break;
				case VALUE_NULL:
					if (col != null) {
						data.val(col, "");
						LOG.trace("    \""+col.getColumnName()+"\":\"\"");
						col = null;
					}
					break;
				case START_ARRAY:
					LOG.trace("[");
					break;
				case END_ARRAY:
					LOG.trace("]");
					break;
				case START_OBJECT:
					data = ClassUtils.newInstance(type);
					LOG.trace("  {");
					break;
				case END_OBJECT:
					list.add(data);
					data = null;
					LOG.trace("  }");
					break;
				default:
					LOG.warn("JSON parser unknown event: "+event);
					col = null;
					break;
			}
		}
		return list;
	}
}
