/*
 * Copyright (c) 2011 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import java.util.ArrayList;
import java.util.Collection;

import org.tamacat.dao.Dao;
import org.tamacat.dao.PreparedSql;
import org.tamacat.dao.Query;
import org.tamacat.dao.Search;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.orm.ORMappingSupport;

public class OracleDao<T extends ORMappingSupport<T>> extends Dao<T> {

	public OracleDao() {}

    @Override
    public Search createSearch() {
        return new OracleSearch();
    }

	/**
	 * Composes a dialect-suffixed {@code PreparedSql} from {@code base}, preserving
	 * its provenance (checked / unchecked). Duplicated from {@link MySQLDao} rather
	 * than shared via {@code Dao} (business-logic-model.md sec 2.3, business-rules.md
	 * BR-4/BR-5).
	 */
	private static PreparedSql compose(PreparedSql base, String sql) {
		return base.hasUnboundPlaceholders()
			? PreparedSql.ofLiteral(sql)
			: PreparedSql.of(sql, base.getValues());
	}

	private static final String FOR_UPDATE = "for update";

	/**
	 * Wraps {@code base} in a rownum-restricting inline view according to
	 * {@code (start, max)} (business-logic-model.md sec 4.2/4.3). Boundary values are
	 * int-literal text, not bound (BR-6/Q2=C) - rownum requires no bind values at all.
	 *
	 * <p>If {@code base} ends with {@code "for update"} it is stripped from the core
	 * text before wrapping and re-appended exactly once at the outermost level
	 * (BR-21) - appending it without stripping would leave it duplicated: once inside
	 * the innermost inline view (unstripped) and once outside (re-added), producing
	 * SQL that cannot execute.
	 */
	private static String wrapRownum(String base, int start, int max) {
		boolean forUpdate = base.toLowerCase().endsWith(FOR_UPDATE);
		String core = forUpdate
			? base.substring(0, base.length() - FOR_UPDATE.length()).trim()
			: base;
		int offset = start > 0 ? start - 1 : 0;
		StringBuilder q = new StringBuilder();
		if (start > 0) {
			q.append("select * from ( select row_.*, rownum rownum_ from ( ")
				.append(core)
				.append(" ) row_ ) where rownum_ > ").append(offset);
			if (max > 0) {
				q.append(" and rownum_ <= ").append(offset + max);
			}
		} else if (max > 0) {
			q.append("select * from ( ").append(core).append(" ) where rownum <= ").append(max);
		} else {
			return base; // W-4: no wrapping - for update (if any) stays at its original position.
		}
		if (forUpdate) {
			q.append(" ").append(FOR_UPDATE);
		}
		return q.toString();
	}

	@Override
	public Collection<T> searchList(Query<T> query, int start, int max) {
		return searchListForOracle(query, start, max);
	}

	/**
	 * TODO: bugfix
	 */
	public Collection<T> searchListForOracle(Query<T> query, int start, int max) {
		Collection<Column> columns = query.getSelectColumns();
		PreparedSql base = query.getSelectPreparedSql();
		String text = wrapRownum(base.getSql(), start, max);
		PreparedSql sql = compose(base, text);

		try {
			return executeQuery(sql, rs -> {
				ArrayList<T> list = new ArrayList<>();
				// BR-13: no row-skipping here - rownum has already skipped in the SQL.
				// Skipping again would double-skip (2 x (start-1) rows).
				int add = 0;
				while (rs.next()) {
					list.add(mapping(columns, rs));
					add++;
					if (max > 0 && add >= max)
						break;
				}
				return list;
			});
		} catch (DaoException e) {
			// BR-16 (U2): handleException always throws; this call never returns.
			handleException(e.getCause() != null ? e.getCause() : e);
			return null;
		}
	}
}
