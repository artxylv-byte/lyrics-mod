package com.artlyrics;

import com.artlyrics.config.ArtlyricsConfig;
import com.artlyrics.lyrics.LyricsManager;
import com.artlyrics.nowplaying.PlaybackTracker;
import net.minecraft.resources.Identifier;

/** Holds the mod's long-lived pieces. Created once by {@link ArtlyricsClient}. */
public final class Artlyrics {
	public static final String MOD_ID = "artlyrics";

	private static Artlyrics instance;

	private final ArtlyricsConfig config;
	private final PlaybackTracker tracker;
	private final LyricsManager lyrics;
	/** Bumped whenever settings change, so cached text layouts get rebuilt. */
	private int settingsRevision;

	Artlyrics(ArtlyricsConfig config, PlaybackTracker tracker, LyricsManager lyrics) {
		this.config = config;
		this.tracker = tracker;
		this.lyrics = lyrics;
		instance = this;
	}

	public static Artlyrics get() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public ArtlyricsConfig config() {
		return config;
	}

	public PlaybackTracker tracker() {
		return tracker;
	}

	public LyricsManager lyrics() {
		return lyrics;
	}

	public int settingsRevision() {
		return settingsRevision;
	}

	/** Applies changed settings right away. Call {@code config().save()} to persist them. */
	public void settingsChanged() {
		settingsRevision++;
		tracker.system().setPreferredPlayer(config.preferredPlayer);
	}
}
