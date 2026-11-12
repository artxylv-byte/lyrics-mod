package com.artlyrics.lyrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMatchTest {
	@Test
	void ignoresAccentsCaseAndPunctuation() {
		assertEquals("prata vetra", TextMatch.normalize("Prāta Vētra!"));
		assertEquals("rock and roll", TextMatch.normalize("Rock & Roll"));
		assertEquals("привет мир", TextMatch.normalize("Привет, мир"));
		assertTrue(TextMatch.sameTitle("Ziemeļblāzma (Remastered 2011)", "ziemelblazma"));
		assertTrue(TextMatch.sameTitle("Glow (feat. Someone)", "Glow"));
		assertFalse(TextMatch.sameTitle("Glow", "Glow Part 2"));
	}

	@Test
	void similarTitlesAllowShortExtras() {
		assertTrue(TextMatch.similarTitle("Paper Lanterns", "Paper Lanterns Radio"));
		assertFalse(TextMatch.similarTitle("Go", "Go Home Tonight"));
	}

	@Test
	void artistsMatchByAnyCredit() {
		assertTrue(TextMatch.artistMatches("Ādams un Ieva", "Adams"));
		assertTrue(TextMatch.artistMatches("North Lights feat. Mira", "Mira"));
		assertTrue(TextMatch.artistMatches("The Lanterns", "lanterns"));
		assertTrue(TextMatch.artistMatches("Sky Band - Topic", "Sky Band"));
		assertFalse(TextMatch.artistMatches("Sky Band", "Sea Band"));
	}
}
