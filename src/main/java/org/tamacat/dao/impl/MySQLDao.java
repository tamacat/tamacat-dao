/*
 * Copyright (c) 2007 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.tamacat.dao.BindValue;
import org.tamacat.dao.Dao;
import org.tamacat.dao.PreparedSql;
import org.tamacat.dao.Query;
import org.tamacat.dao.Search;
import org.tamacat.dao.exception.DaoException;
import org.tamacat.dao.meta.Column;
import org.tamacat.dao.orm.ORMappingSupport;
import org.tamacat.sql.SQLParser;

public class MySQLDao<T extends ORMappingSupport<T>> extends Dao<T> {

	public MySQLDao() {
		parser = new SQLParser(new MySQLSearch.MySQLValueConvertFilter());
	}

	@Override
	public Search createSearch() {
		return new MySQLSearch();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Query<T> createQuery() {
		return new QueryImpl(new MySQLSearch.MySQLValueConvertFilter());
	}

	/**
	 * Composes a dialect-suffixed {@code PreparedSql} from {@code base}, preserving
	 * its provenance (checked / unchecked). {@code sql} is {@code base.getSql()}
	 * with the MySQL suffix (and/or {@code SQL_CALC_FOUND_ROWS} prefix substitution)
	 * applied - it carries the same number of {@code ?} placeholders as {@code base}
	 * (business-logic-model.md sec 2.2, business-rules.md BR-4/BR-5).
	 */
	private static PreparedSql compose(PreparedSql base, String sql) {
		return base.hasUnboundPlaceholders()
			? PreparedSql.ofLiteral(sql)
			: PreparedSql.of(sql, base.getValues());
	}

	@Override
	public Collection<T> searchList(Query<T> query, int start, int max) {
		Collection<Column> columns = query.getSelectColumns();
		PreparedSql base = query.getSelectPreparedSql();

		String text = base.getSql();
		boolean paged = start > 0 && max > 0;
		if (paged) {
			if (useHitCount) {
				text = text.replaceFirst("SELECT ", "SELECT SQL_CALC_FOUND_ROWS ");
			}
			// BR-6 / Q2=C: the LIMIT boundary values are int-literal text, not bound -
			// MySQL's LIMIT clause requires integer parameters, incompatible with
			// ADR-007's setString-only binding (business-logic-model.md sec 8 D-1).
			text = text + " limit " + (start - 1) + "," + max;
		}
		PreparedSql sql = compose(base, text);

		try {
			Collection<T> list = executeQuery(sql, rs -> {
				ArrayList<T> l = new ArrayList<>();
				int add = 0;
				while (rs.next()) {
					l.add(mapping(columns, rs));
					add++;
					if (max > 0 && add >= max)
						break;
				}
				return l;
			});
			setHitCount(list.size());
			if (useHitCount && paged) {
				// BR-8/BR-9: run as a second statement with zero values - FOUND_ROWS()
				// reads session-level state, not statement-level, so it need not be the
				// same PreparedStatement as the body query.
				long hit = executeQuery(
					PreparedSql.of("SELECT FOUND_ROWS()", Collections.<BindValue>emptyList()),
					rs -> rs.next() ? rs.getLong(1) : 0L);
				setHitCount(hit);
			}
			return list;
		} catch (DaoException e) {
			// BR-16 (U2): handleException always throws; this call never returns.
			handleException(e.getCause() != null ? e.getCause() : e);
			return null;
		}
	}
}
