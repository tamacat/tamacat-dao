/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.dao;

/**
 * Counts the {@code ?} placeholders that lie outside of single-quoted text in a SQL
 * fragment. Shared by {@link Param} and {@link PreparedSql}.
 *
 * @since 2.0
 */
final class PlaceholderScanner {

	private PlaceholderScanner() {
	}

	/**
	 * Returns the number of {@code ?} characters outside of single-quoted text in
	 * {@code sql}. A {@code ''} inside quotes is treated as an escaped quote and does
	 * not close the quoted region.
	 * @param sql the SQL text to scan. Must not be {@code null}
	 * @return the placeholder count, or {@code -1} if the quoting is unterminated
	 *         (the scan could not determine the boundary)
	 */
	static int countPlaceholders(String sql) {
		int count = 0;
		boolean inQuote = false;
		for (int i = 0; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c == '\'') {
				if (inQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
					i++; // skip escaped '' as a single literal quote
					continue;
				}
				inQuote = !inQuote;
			} else if (c == '?' && !inQuote) {
				count++;
			}
		}
		return inQuote ? -1 : count;
	}
}
