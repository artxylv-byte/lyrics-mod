package com.artlyrics.nowplaying;

import com.artlyrics.lyrics.TrackQuery;

import java.util.Objects;

/**
 * A snapshot of what a media player reported.
 *
 * @param player         short player name, e.g. "Spotify"
 * @param durationMs     track length, 0 if unknown
 * @param positionMs     playback position at the moment of sampling
 * @param sampledAtNanos {@link System#nanoTime()} when the position was sampled
 */
public record NowPlaying(String player, String title, String artist, String album,
						 long durationMs, long positionMs, boolean playing, long sampledAtNanos) {
	public NowPlaying {
		player = Objects.requireNonNullElse(player, "");
		title = Objects.requireNonNullElse(title, "").strip();
		artist = Objects.requireNonNullElse(artist, "").strip();
		album = Objects.requireNonNullElse(album, "").strip();
	}

	/** Position extrapolated to {@code nowNanos}. */
	public long positionAt(long nowNanos) {
		long pos = positionMs;
		if (playing) {
			pos += (nowNanos - sampledAtNanos) / 1_000_000L;
		}
		if (durationMs > 0) {
			pos = Math.min(pos, durationMs);
		}
		return Math.max(0, pos);
	}

	public boolean sameTrackAs(NowPlaying other) {
		return other != null
				&& title.equalsIgnoreCase(other.title)
				&& artist.equalsIgnoreCase(other.artist)
				&& player.equals(other.player);
	}

	public TrackQuery toQuery() {
		return new TrackQuery(title, artist, album, durationMs);
	}

	public String displayName() {
		return artist.isBlank() ? title : artist + " – " + title;
	}
}
