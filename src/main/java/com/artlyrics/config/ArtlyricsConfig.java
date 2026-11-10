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
		/** Words appear in 3D far out in the sky; blocks in front hide them. */
		SKY,
		/** Words float in front of you wherever you look and are never hidden (good for PvP). */
		FRONT;

		public String translationKey() {
			return "artlyrics.mode." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum Animation {
		/** Each word fades and rises in as it is sung. */
		WORD_BY_WORD,
		/** The whole line shows at once and words light up as they are sung. */
		KARAOKE,
		/** Only the word being sung is shown, big. */
		ONE_WORD;

		public String translationKey() {
			return "artlyrics.animation." + name().toLowerCase(Locale.ROOT);
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

	public boolean enabled = true;
	public DisplayMode displayMode = DisplayMode.SKY;
	public Animation animation = Animation.WORD_BY_WORD;
	public LyricStyle style = LyricStyle.LAVENDER;
	public TextFont font = TextFont.DEFAULT;
	/** Show the current line in a bar at the top of the screen. */
	public boolean topBar = true;
	/** Bend lines into a gentle arc. */
	public boolean curved = true;
	public boolean shadow = true;
	/** Text size multiplier. */
	public float size = 1.0f;
	/** Degrees above the horizon for sky lyrics. */
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
			displayMode = DisplayMode.SKY;
		}
		if (animation == null) {
			animation = Animation.WORD_BY_WORD;
		}
		if (style == null) {
			style = LyricStyle.LAVENDER;
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
