/*
 * Copyright 2026 tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

import static org.junit.Assert.*;

import org.junit.Test;

public class LikeEscapeTest {

	@Test
	public void testOf() {
		LikeEscape le = LikeEscape.of("%ta$_ma%", '$');
		assertEquals("%ta$_ma%", le.getBoundValue());
		assertEquals('$', le.getEscapeChar());
		assertTrue(le.hasEscape());
	}

	@Test
	public void testNoEscape() {
		LikeEscape le = LikeEscape.noEscape("%tama%");
		assertEquals("%tama%", le.getBoundValue());
		assertFalse(le.hasEscape());
	}

	@Test
	public void testBoundValueNeverNull() {
		LikeEscape le = LikeEscape.noEscape("%%");
		assertNotNull(le.getBoundValue());
	}

	@Test
	public void testEscapeCharIsOneOfCandidates() {
		char[] candidates = { '$', '#', '~', '!', '^' };
		LikeEscape le = LikeEscape.of("%$_%", '#');
		boolean found = false;
		for (char c : candidates) {
			if (c == le.getEscapeChar()) {
				found = true;
			}
		}
		assertTrue(found);
	}
}
