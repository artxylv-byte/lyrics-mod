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
import java.nio.file.AtomicMoveNotSupportedException;
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
		/** Words softly fade in. */
		FADE,
		/** Letters float up into place one after another. */
		RISE,
		/** Each word opens up bright and settles into the palette. */
		BLOOM,
		/** Letters are typed out in time with the singing. */
		TYPEWRITER,
		/** The whole line shows faintly and color sweeps along it. */
		SWEEP,
		/** Only the word being sung, large and centered. */
		SPOTLIGHT;

		public String translationKey() {
			return "artlyrics.animation." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** How a finished line leaves. */
	public enum ExitStyle {
		FLOAT, FADE, SHRINK;

		public String translationKey() {
			return "artlyrics.exit." + name().toLowerCase(Locale.ROOT);
		}
	}

	public enum TextEffect {
		/** A soft halo in the text's own color. */
		GLOW,
		SHADOW,
		/** A dark outline around every letter, like glowing signs. */
		OUTLINE,
		NONE;

		public String translationKey() {
			return "artlyrics.effect." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Fonts. Everything but {@link #MINECRAFT} and {@link #PIXEL} is a bundled, smooth TrueType font. */
	public enum TextFont {
		MINECRAFT(null),
		/** Minecraft's own Unifont-based "uniform" font. */
		PIXEL(null),
		SOFT("soft"),
		BOLD("bold"),
		SCRIPT("script"),
		FANCY("fancy"),
		HANDWRITTEN("handwritten"),
		DECO("deco"),
		BUBBLY("bubbly"),
		TYPEWRITER("typewriter");

		/** Name of the bundled font files under {@code assets/artlyrics/font}, or null for Minecraft fonts. */
		public final String bundled;

		TextFont(String bundled) {
			this.bundled = bundled;
		}

		public String translationKey() {
			return "artlyrics.font." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Where the song card sits. */
	public enum CardCorner {
		TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public String translationKey() {
			return "artlyrics.corner." + name().toLowerCase(Locale.ROOT);
		}
	}

	// --- Lyrics -------------------------------------------------------------------------------------------
	public boolean enabled = true;
	public DisplayMode displayMode = DisplayMode.SKYWRITING;
	public Animation animation = Animation.FADE;
	/** Multiplies how fast animations play (0.5 = slow and dreamy, 2 = snappy). */
	public float animationSpeed = 1.0f;
	public ExitStyle exitStyle = ExitStyle.FLOAT;
	/** How long a line stays after its last word, in seconds (it always leaves when the next line starts). */
	public float lingerSeconds = 2.5f;
	/** Shifts lyrics in time; positive shows them earlier. */
	public int offsetMs = 0;

	// --- Look -----------------------------------------------------------------------------------------------
	public TextFont font = TextFont.SOFT;
	public LyricStyle style = LyricStyle.AMETHYST;
	public int customFrom = LyricStyle.CUSTOM.from;
	public int customTo = LyricStyle.CUSTOM.to;
	public int customAccent = LyricStyle.CUSTOM.accent;
	public TextEffect textEffect = TextEffect.GLOW;
	public float glowStrength = 0.6f;
	public boolean bold = false;
	public boolean italic = false;
	public boolean uppercase = false;
	public float opacity = 1.0f;
	/** Text size multiplier. */
	public float size = 1.0f;
	/** Longest row before a line wraps, in font pixels. */
	public int lineWidth = 200;

	// --- Placement ----------------------------------------------------------------------------------------
	/** Degrees above the horizon for skywriting. */
	public float skyHeight = 18f;
	/** Blocks away for skywriting; 0 picks a distance from the render distance. */
	public int skyDistance = 0;
	/** Place consecutive lines at slightly different spots. */
	public boolean scatter = true;
	/** Degrees per second that skywriting rises while shown. */
	public float driftSpeed = 0.25f;
	public boolean arc = false;
	public float arcAmount = 0.5f;
	public float hoverDistance = 4.0f;
	/** Degrees above the crosshair for hover mode. */
	public float hoverRaise = 9f;
	/** How quickly hovering lines catch up with your view. */
	public float followSpeed = 7f;

	// --- Song card ----------------------------------------------------------------------------------------
	public boolean songCard = true;
	public CardCorner cardCorner = CardCorner.TOP_LEFT;
	public float cardScale = 1.0f;
	public float cardOpacity = 0.75f;
	public boolean cardArtist = true;
	public boolean cardProgress = true;
	public boolean cardNextLine = true;

	// --- Music ----------------------------------------------------------------------------------------------
	/** When several players are active, prefer the one whose name contains this ("" = any). */
	public String preferredPlayer = "spotify";

	private transient Path path;

	/** The palette in use, resolving {@link LyricStyle#CUSTOM} to the user's colors. */
	public Palette palette() {
		if (style == LyricStyle.CUSTOM) {
			return new Palette(customFrom & 0xFFFFFF, customTo & 0xFFFFFF, customAccent & 0xFFFFFF, false);
		}
		return style.palette();
	}

	/** Applies a look preset. */
	public void apply(Look look) {
		font = look.font;
		style = look.style;
		textEffect = look.effect;
		animation = look.animation;
	}

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

	/** Puts every value back in range and replaces anything unknown (e.g. from older versions) with defaults. */
	public void sanitize() {
		ArtlyricsConfig d = new ArtlyricsConfig();
		if (displayMode == null) displayMode = d.displayMode;
		if (animation == null) animation = d.animation;
		if (exitStyle == null) exitStyle = d.exitStyle;
		if (font == null) font = d.font;
		if (style == null) style = d.style;
		if (textEffect == null) textEffect = d.textEffect;
		if (cardCorner == null) cardCorner = d.cardCorner;
		if (preferredPlayer == null) preferredPlayer = d.preferredPlayer;

		animationSpeed = clamp(animationSpeed, 0.5f, 2f, d.animationSpeed);
		lingerSeconds = clamp(lingerSeconds, 0.5f, 6f, d.lingerSeconds);
		offsetMs = Math.max(-5000, Math.min(5000, offsetMs));
		glowStrength = clamp(glowStrength, 0f, 1f, d.glowStrength);
		opacity = clamp(opacity, 0.2f, 1f, d.opacity);
		size = clamp(size, 0.5f, 2.5f, d.size);
		lineWidth = Math.max(100, Math.min(320, lineWidth));
		skyHeight = clamp(skyHeight, 5f, 60f, d.skyHeight);
		skyDistance = skyDistance <= 0 ? 0 : Math.max(16, Math.min(128, skyDistance));
		driftSpeed = clamp(driftSpeed, 0f, 1f, d.driftSpeed);
		arcAmount = clamp(arcAmount, 0f, 1f, d.arcAmount);
		hoverDistance = clamp(hoverDistance, 2f, 10f, d.hoverDistance);
		hoverRaise = clamp(hoverRaise, -15f, 30f, d.hoverRaise);
		followSpeed = clamp(followSpeed, 1f, 20f, d.followSpeed);
		cardScale = clamp(cardScale, 0.6f, 1.6f, d.cardScale);
		cardOpacity = clamp(cardOpacity, 0.2f, 1f, d.cardOpacity);
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
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOGGER.warn("[Artlyrics] Couldn't save {}", path, e);
		}
	}

	private static float clamp(float v, float min, float max, float fallback) {
		if (!Float.isFinite(v)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, v));
	}
}
