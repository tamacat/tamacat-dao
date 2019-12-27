package org.tamacat.dao.test;

import org.tamacat.dao.meta.DefaultColumn;
import org.tamacat.dao.meta.DefaultTable;
import org.tamacat.dao.meta.DataType;
import org.tamacat.dao.orm.MapBasedORMappingBean;

public class FileData extends MapBasedORMappingBean<FileData> {

	private static final long serialVersionUID = 1L;

    public static final DefaultTable TABLE = new DefaultTable("file");
    public static final DefaultColumn FILE_ID = new DefaultColumn();
    public static final DefaultColumn FILE_NAME = new DefaultColumn();
    public static final DefaultColumn SIZE = new DefaultColumn();
    public static final DefaultColumn CONTENT_TYPE = new DefaultColumn();
    public static final DefaultColumn DATA = new DefaultColumn();
    public static final DefaultColumn UPDATE_DATE = new DefaultColumn();
    
    static {
        FILE_ID.type(DataType.STRING).columnName("file_id").primaryKey(true).autoGenerateId(true);
        FILE_NAME.type(DataType.STRING).columnName("file_name");
        SIZE.type(DataType.NUMERIC).columnName("size");
        CONTENT_TYPE.type(DataType.STRING).columnName("content_type");
        DATA.type(DataType.OBJECT).columnName("data");
        UPDATE_DATE.type(DataType.DATE).columnName("update_date").autoTimestamp(true);
        TABLE.registerColumn(FILE_ID, FILE_NAME, SIZE, CONTENT_TYPE, DATA, UPDATE_DATE);
    }
}
