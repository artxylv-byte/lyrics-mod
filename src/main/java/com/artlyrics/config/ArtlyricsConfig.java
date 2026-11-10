package com.artlyrics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** User settings, stored as JSON in {@code config/artlyrics.json}. */
public final class ArtlyricsConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum DisplayMode {
		/** Lines are written onto the sky around you at a fixed height; terrain can cover them. */
		SKYWRITING,
		/** Lines hover near the crosshair, trail your view and are drawn over everything. */
		HOVER;

		public String translationKey() {
			return "artlyrics.mode." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Animation {
		/** Each word blooms open (grows and brightens) as it is sung. */
		BLOOM,
		/** Words are typed out letter by letter while they are sung. */
		TYPEWRITER,
		/** The whole line is shown faintly and a sweep of color follows the singing. */
		SWEEP,
		/** Only the word being sung is shown, large, in the middle. */
		SPOTLIGHT;

		public String translationKey() {
			return "artlyrics.animation." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum TextEffect {
		SHADOW,
		/** A dark outline around every letter, like glowing signs. */
		OUTLINE,
		NONE;

		public String translationKey() {
			return "artlyrics.effect." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum TextFont {
		DEFAULT,
		/** Minecraft's smoother "uniform" (Unifont) font. */
		SMOOTH;

		public String translationKey() {
			return "artlyrics.font." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Which corner of the screen the song card sits in. */
	public enum CardCorner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT;

		public String translationKey() {
			return "artlyrics.corner." + name().toLowerCase(Locale.ROOT);
		}
	}

	public boolean enabled = true;
	public DisplayMode displayMode = DisplayMode.SKYWRITING;
	public Animation animation = Animation.BLOOM;
	public LyricStyle style = LyricStyle.AMETHYST;
	public TextFont font = TextFont.DEFAULT;
	public TextEffect textEffect = TextEffect.SHADOW;
	/** Show a small card with the song, its progress and the current line. */
	public boolean songCard = true;
	public CardCorner cardCorner = CardCorner.TOP_LEFT;
	/** Bend lines into an arc. */
	public boolean arc = false;
	/** Text size multiplier. */
	public float size = 1.0f;
	/** Degrees above the horizon for skywriting. */
	public float skyHeight = 18f;
	/** Shifts lyrics in time; positive shows them earlier. */
	public int offsetMs = 0;
	/** When several players are active, prefer the one whose name contains this. */
	public String preferredPlayer = "spotify";

	private transient Path path;

	public static ArtlyricsConfig load(Path path) {
		ArtlyricsConfig config = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, ArtlyricsConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[Artlyrics] Couldn't read {}, using defaults", path, e);
			}
		}
		if (config == null) {
			config = new ArtlyricsConfig();
		}
		config.path = path;
		config.sanitize();
		config.save();
		return config;
	}

	private void sanitize() {
		if (displayMode == null) {
			displayMode = DisplayMode.SKYWRITING;
		}
		if (animation == null) {
			animation = Animation.BLOOM;
		}
		if (style == null) {
			style = LyricStyle.AMETHYST;
		}
		if (textEffect == null) {
			textEffect = TextEffect.SHADOW;
		}
		if (cardCorner == null) {
			cardCorner = CardCorner.TOP_LEFT;
		}
		if (font == null) {
			font = TextFont.DEFAULT;
		}
		if (preferredPlayer == null) {
			preferredPlayer = "spotify";
		}
		size = clamp(Float.isFinite(size) ? size : 1f, 0.5f, 2.5f);
		skyHeight = clamp(Float.isFinite(skyHeight) ? skyHeight : 18f, 5f, 60f);
		offsetMs = Math.max(-5000, Math.min(5000, offsetMs));
	}

	public void save() {
		if (path == null) {
			return;
		}
		sanitize();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("[Artlyrics] Couldn't save {}", path, e);
		}
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}
