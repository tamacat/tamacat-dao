package org.tamacat.dao.test;

import org.tamacat.dao.Condition;
import org.tamacat.dao.DaoAdapter;
import org.tamacat.dao.Query;

public class FileDataDao extends DaoAdapter<FileData> {
    
	public FileData search(FileData data) {
        Query<FileData> query = createQuery()
            .select(FileData.TABLE.getColumns())
            .where(param(FileData.FILE_ID, Condition.EQUAL,
            			data.getValue(FileData.FILE_ID)));
        return super.search(query);
    }

    @Override
    protected String getInsertSQL(FileData data) {
        Query<FileData> query = createQuery()
        	.addUpdateColumns(FileData.TABLE.getColumns());
        return query.getInsertSQL(data);
    }

    @Override
    protected String getUpdateSQL(FileData data) {
        Query<FileData> query = createQuery()
        	.addUpdateColumn(FileData.UPDATE_DATE)
        	.addUpdateColumn(FileData.FILE_NAME)
        	.where(
        		param(FileData.FILE_ID, Condition.EQUAL,
        				data.getValue(FileData.FILE_ID))
        	);
        return query.getUpdateSQL(data);
    }

    @Override
    protected String getDeleteSQL(FileData data) {
        Query<FileData> query = createQuery()
        	.addUpdateColumn(FileData.FILE_ID);
        return query.getDeleteSQL(data);
    }
}
