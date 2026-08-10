/*
 * Copyright (c) 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

/**
 * The result of {@code ValueRules.escapeLike(...)}: the value to bind for a LIKE
 * predicate, and whether/which escape character it needs.
 *
 * @since 2.0
 */
public final class LikeEscape {

	private final String boundValue;
	private final char escapeChar;
	private final boolean escaped;

	private LikeEscape(String boundValue, char escapeChar, boolean escaped) {
		this.boundValue = boundValue;
		this.escapeChar = escapeChar;
		this.escaped = escaped;
	}

	/**
	 * Creates a {@code LikeEscape} for a value that required escaping.
	 * @param boundValue the value to bind. Must not be {@code null}
	 * @param escapeChar the escape character used
	 * @return a new {@code LikeEscape} with {@link #hasEscape()} {@code true}
	 */
	public static LikeEscape of(String boundValue, char escapeChar) {
		return new LikeEscape(boundValue, escapeChar, true);
	}

	/**
	 * Creates a {@code LikeEscape} for a value that required no escaping.
	 * @param boundValue the value to bind. Must not be {@code null}
	 * @return a new {@code LikeEscape} with {@link #hasEscape()} {@code false}
	 */
	public static LikeEscape noEscape(String boundValue) {
		return new LikeEscape(boundValue, '\0', false);
	}

	public String getBoundValue() {
		return boundValue;
	}

	public char getEscapeChar() {
		return escapeChar;
	}

	public boolean hasEscape() {
		return escaped;
	}
}
