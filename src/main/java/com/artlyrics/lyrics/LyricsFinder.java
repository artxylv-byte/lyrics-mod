package com.artlyrics.lyrics;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Asks each lyrics source in turn (the lyrics folder, LRCLIB, NetEase Cloud Music) and keeps the best answer:
 * synced lyrics from the first source that has them. When no source has timestamps but one has the text, the
 * lines are timed by estimate (if that's switched on), so the song still gets lyrics.
 */
public final class LyricsFinder {
	/** Which sources to use. */
	public record Settings(boolean folder, boolean lrclib, boolean netease, boolean estimateUnsynced) {
		public static final Settings ALL = new Settings(true, true, true, true);
	}

	private final @Nullable LocalLyrics folder;
	private final @Nullable LyricsSource lrclib;
	private final @Nullable LyricsSource netease;
	private final Supplier<Settings> settings;

	public LyricsFinder(@Nullable LocalLyrics folder, @Nullable LyricsSource lrclib, @Nullable LyricsSource netease,
						Supplier<Settings> settings) {
		this.folder = folder;
		this.lrclib = lrclib;
		this.netease = netease;
		this.settings = settings;
	}

	public @Nullable LocalLyrics folder() {
		return folder;
	}

	public LyricsResult find(TrackQuery track) throws InterruptedException {
		Settings s = settings.get();
		List<LyricsSource> sources = new ArrayList<>();
		if (s.folder() && folder != null) {
			sources.add(folder);
		}
		if (s.lrclib() && lrclib != null) {
			sources.add(lrclib);
		}
		if (s.netease() && netease != null) {
			sources.add(netease);
		}
		if (sources.isEmpty()) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "All lyrics sources are switched off");
		}

		LyricsResult unsynced = null;
		LyricsResult instrumental = null;
		LyricsResult error = null;
		for (LyricsSource source : sources) {
			LyricsResult r = source.lookup(track);
			switch (r.status()) {
				case FOUND -> {
					return r;
				}
				case INSTRUMENTAL -> {
					// Another source may still have lyrics if this one is wrong, but it's a strong hint.
					if (instrumental == null) {
						instrumental = r;
					}
				}
				case UNSYNCED_ONLY -> {
					if (unsynced == null && r.plainLyrics() != null) {
						unsynced = new LyricsResult(r.status(), null, source.name() + ": " + r.detail(), r.plainLyrics());
					}
				}
				case ERROR -> {
					if (error == null) {
						error = r;
					}
				}
				default -> {
				}
			}
		}
		if (instrumental != null) {
			return instrumental;
		}
		if (unsynced != null) {
			if (s.estimateUnsynced()) {
				Lyrics estimated = EstimatedTiming.build(unsynced.plainLyrics(), track.durationMs(), unsynced.detail() + " (timing estimated)");
				if (!estimated.isEmpty()) {
					return new LyricsResult(LyricsResult.Status.FOUND, estimated, estimated.source());
				}
			}
			return unsynced;
		}
		if (error != null) {
			return error;
		}
		return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No lyrics found");
	}
}
