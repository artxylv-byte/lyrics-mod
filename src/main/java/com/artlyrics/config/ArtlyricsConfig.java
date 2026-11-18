package com.artlyrics.config;

import com.artlyrics.holiday.HolidayFinder;
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
		TYPEWRITER("typewriter"),
		ROUNDED("rounded"),
		BRUSH("brush"),
		RETRO("retro"),
		ARCADE("arcade"),
		POSTER("poster"),
		CLASSIC("classic"),
		ELEGANT("elegant"),
		NOTES("notes");

		/** Name of the bundled font files under {@code assets/artlyrics/font}, or null for Minecraft fonts. */
		public final String bundled;

		TextFont(String bundled) {
			this.bundled = bundled;
		}

		public String translationKey() {
			return "artlyrics.font." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** How the song card looks. */
	public enum CardStyle {
		/** A slim bar: small cover, title and the lyric line. */
		COMPACT,
		/** A bigger player with a large cover, a record and moving bars. */
		PLAYER;

		public String translationKey() {
			return "artlyrics.card_style." + name().toLowerCase(Locale.ROOT);
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
	/** Show each line complete as soon as it starts, instead of word by word. */
	public boolean wholeLine = false;
	/** Show the next line faintly under the current one. */
	public boolean nextLinePreview = false;
	/** How visible the next-line preview is. */
	public float previewOpacity = 0.4f;

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
	public CardStyle cardStyle = CardStyle.COMPACT;
	/** Player style: a record slides out from behind the cover and spins while the song plays. */
	public boolean cardVinyl = true;
	/** Player style: bars that dance along with the sung words. */
	public boolean cardVisualizer = true;
	public boolean cardArtist = true;
	public boolean cardProgress = true;
	public boolean cardNextLine = true;
	/** Show the song's cover picture (from the player, the covers folder or online). */
	public boolean cardCover = true;
	/** A bigger cover tile. */
	public boolean cardBigCover = false;
	/** Play animated covers (GIFs); off shows their first frame. */
	public boolean coverAnimate = true;
	/** Look covers up online (iTunes search) when the player doesn't share one. */
	public boolean coverOnline = true;

	// --- Music ----------------------------------------------------------------------------------------------
	/** When several players are active, prefer the one whose name contains this ("" = any). */
	public String preferredPlayer = "spotify";
	/** Play / pause, previous and next in the menu (and their keys); with Spotify connected also like and playlists. */
	public boolean mediaControls = false;
	/** Client ID of the player's own Spotify app (from developer.spotify.com), for liking and playlists. */
	public String spotifyClientId = "";

	// --- Lyrics sources -------------------------------------------------------------------------------------
	/** Lyrics files in config/artlyrics/lyrics. */
	public boolean sourceFolder = true;
	public boolean sourceLrclib = true;
	public boolean sourceNetease = true;
	/** When a song only has lyrics without timestamps, spread them over the song instead of showing nothing. */
	public boolean estimateUnsynced = true;

	// --- Menu -------------------------------------------------------------------------------------------------
	/** Colors of this settings menu and the song card. */
	public enum MenuTheme {
		DARK, LIGHT, MIDNIGHT, GLASS, OCEAN, FOREST, SAKURA;

		public String translationKey() {
			return "artlyrics.menu_theme." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Color of the glow, outline or shadow around the lyrics. */
	public enum EffectColor {
		/** A lighter (glow) or darker (outline, shadow) version of each word's color. */
		AUTO, WHITE, BLACK, ACCENT;

		public String translationKey() {
			return "artlyrics.effect_color." + name().toLowerCase(Locale.ROOT);
		}
	}

	public MenuTheme menuTheme = MenuTheme.DARK;
	public EffectColor effectColor = EffectColor.AUTO;

	// --- Sky ------------------------------------------------------------------------------------------------
	public com.artlyrics.sky.SkyTheme sky = com.artlyrics.sky.SkyTheme.NONE;
	/** Show the sky only at night (it fades in at dusk). */
	public boolean skyNightOnly = false;
	public float skyIntensity = 0.9f;
	/** How fast the sky moves (0 = still). */
	public float skySpeed = 1.0f;
	public boolean skyStars = true;

	// --- Updates ------------------------------------------------------------------------------------------
	public enum UpdateMode {
		/** Download new versions by itself; they're used from the next start. */
		AUTOMATIC,
		/** Only say when there's a new version. */
		NOTIFY,
		OFF;

		public String translationKey() {
			return "artlyrics.updates." + name().toLowerCase(Locale.ROOT);
		}
	}

	public UpdateMode updates = UpdateMode.AUTOMATIC;
	/** GitHub repository ("owner/name") whose downloads folder has the mod's jars. */
	public String updateRepository = "artxylv-byte/lyrics-mod";
	/** Branches to look in; the newest version found in any of them is used. */
	public java.util.List<String> updateBranches = new java.util.ArrayList<>(
			java.util.List.of("main", "claude/minecraft-sky-lyric-mods-p1mjm2"));

	// --- Holidays -----------------------------------------------------------------------------------------
	public HolidayFinder.Mode holidays = HolidayFinder.Mode.SEASONS;
	/** Region code ("LV", "US", ...) or "auto" for the computer's region. */
	public String holidayRegion = "auto";
	/** Holiday colors for lyrics, the card and this menu. */
	public boolean holidayColors = true;
	/** Holiday decorations around the lyrics and on the card. */
	public boolean holidayDecorations = true;
	/** A holiday theme to try out now ("" = none). */
	public String holidayPreview = "";
	/** The holiday shown now; worked out by the mod, not saved. */
	public transient HolidayFinder.Active activeHoliday;

	private transient Path path;

	/** The palette in use, resolving {@link LyricStyle#CUSTOM} to the user's colors. */
	public Palette palette() {
		if (activeHoliday != null && holidayColors) {
			return activeHoliday.palette();
		}
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
		if (cardStyle == null) cardStyle = d.cardStyle;
		if (preferredPlayer == null) preferredPlayer = d.preferredPlayer;
		if (spotifyClientId == null) spotifyClientId = "";
		if (holidays == null) holidays = d.holidays;
		if (updates == null) updates = d.updates;
		if (menuTheme == null) menuTheme = d.menuTheme;
		if (sky == null) sky = d.sky;
		skyIntensity = clamp(skyIntensity, 0.2f, 1f, d.skyIntensity);
		skySpeed = clamp(skySpeed, 0f, 2f, d.skySpeed);
		if (effectColor == null) effectColor = d.effectColor;
		if (updateRepository == null || !updateRepository.matches("[\\w.-]+/[\\w.-]+")) updateRepository = d.updateRepository;
		if (updateBranches == null || updateBranches.isEmpty()) updateBranches = d.updateBranches;
		updateBranches.removeIf(b -> b == null || b.isBlank());
		if (holidayRegion == null || holidayRegion.isBlank()) holidayRegion = d.holidayRegion;
		if (holidayPreview == null) holidayPreview = "";

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
		previewOpacity = clamp(previewOpacity, 0.15f, 0.8f, d.previewOpacity);
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
