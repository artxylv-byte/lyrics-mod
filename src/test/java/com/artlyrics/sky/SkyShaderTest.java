package com.artlyrics.sky;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shader picks a sky by its number, so SkyTheme's order must match the shader's list. */
class SkyShaderTest {
	private static final Map<SkyTheme, String> FUNCTIONS = new LinkedHashMap<>();

	static {
		FUNCTIONS.put(SkyTheme.MILKY_WAY, "art_skyMilkyWay");
		FUNCTIONS.put(SkyTheme.RINGED_PLANET, "art_skyPlanet");
		FUNCTIONS.put(SkyTheme.SPIRAL_GALAXY, "art_skyGalaxy");
		FUNCTIONS.put(SkyTheme.AURORA, "art_skyAurora");
		FUNCTIONS.put(SkyTheme.ECLIPSE, "art_skyEclipse");
		FUNCTIONS.put(SkyTheme.BLACK_HOLE, "art_skyBlackHole");
		FUNCTIONS.put(SkyTheme.NEBULA, "art_skyNebula");
		FUNCTIONS.put(SkyTheme.TWIN_MOONS, "art_skyTwinMoons");
		FUNCTIONS.put(SkyTheme.MOON_HALO, "art_skyMoonHalo");
		FUNCTIONS.put(SkyTheme.METEOR_SHOWER, "art_skyMeteors");
		FUNCTIONS.put(SkyTheme.COMET, "art_skyComet");
		FUNCTIONS.put(SkyTheme.LIGHT_PILLARS, "art_skyPillars");
		FUNCTIONS.put(SkyTheme.STAR_RIFT, "art_skyRift");
		FUNCTIONS.put(SkyTheme.SKY_LANTERNS, "art_skyLanterns");
		FUNCTIONS.put(SkyTheme.SAKURA_DUSK, "art_skySakura");
		FUNCTIONS.put(SkyTheme.GOLDEN_HOUR, "art_skyGolden");
		FUNCTIONS.put(SkyTheme.SYNTH_SUNSET, "art_skySynth");
	}

	private static String shader() throws Exception {
		try (InputStream in = SkyShaderTest.class.getResourceAsStream("/assets/artlyrics/shaders/include/sky.glsl")) {
			assertNotNull(in, "sky.glsl is missing");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void everySkyHasItsNumberInTheShader() throws Exception {
		String src = shader();
		assertEquals(SkyTheme.values().length - 1, FUNCTIONS.size(), "add new skies to this test too");
		Matcher m = Pattern.compile("if \\(sky == (\\d+)\\) c = (art_sky\\w+)\\(").matcher(src);
		Map<Integer, String> byId = new LinkedHashMap<>();
		while (m.find()) {
			byId.put(Integer.parseInt(m.group(1)), m.group(2));
		}
		Matcher last = Pattern.compile("else c = (art_sky\\w+)\\(").matcher(src);
		assertTrue(last.find(), "the shader's list should end with a plain else");
		for (Map.Entry<SkyTheme, String> e : FUNCTIONS.entrySet()) {
			int id = e.getKey().ordinal();
			String fn = byId.containsKey(id) ? byId.get(id) : (id == SkyTheme.values().length - 1 ? last.group(1) : null);
			assertEquals(e.getValue(), fn, e.getKey() + " should be sky " + id + " in sky.glsl");
		}
	}

	@Test
	void everySkyHasAPreviewDirection() throws Exception {
		String src = shader();
		for (int id = 1; id < SkyTheme.values().length; id++) {
			assertTrue(src.contains("if (sky == " + id + ") return vec2("), "art_hero has no entry for sky " + id);
		}
	}
}
