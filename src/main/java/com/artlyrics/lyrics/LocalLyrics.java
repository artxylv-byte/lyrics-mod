package com.artlyrics.lyrics;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lyrics files the player puts in {@code config/artlyrics/lyrics}: {@code .lrc} files (with timestamps) or
 * {@code .txt} files (plain text, timed automatically). A file matches a song by its name, "Artist - Title.lrc" or
 * "Title.lrc", or by the {@code [ar:]} and {@code [ti:]} tags inside an LRC file. This is how songs no website has
 * lyrics for can still get them.
 */
public final class LocalLyrics implements LyricsSource {
	private static final Pattern TAG = Pattern.compile("^\\[(ar|ti):\\s*(.*?)\\s*]\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
	private static final long MAX_FILE_BYTES = 512 * 1024;

	private final Path folder;

	public LocalLyrics(Path folder) {
		this.folder = folder;
	}

	public Path folder() {
		return folder;
	}

	@Override
	public String name() {
		return "Lyrics folder";
	}

	/** Creates the folder with a short how-to, if it isn't there yet. */
	public void createFolder() {
		try {
			Files.createDirectories(folder);
			Path help = folder.resolve("README.txt");
			if (!Files.exists(help)) {
				Files.writeString(help, """
						Put lyrics for songs here and Artlyrics will use them before searching online.

						Name each file after the song:
						  Artist - Title.lrc   lyrics with timestamps, like [00:12.50]First line
						  Artist - Title.txt   plain lyrics; Artlyrics spreads them over the song by itself
						  Title.lrc            also works (matches any artist)

						Capital letters, accents and things like "(Remastered)" or "feat. ..." don't matter.
						LRC files can also say which song they are with [ar:Artist] and [ti:Title] lines.
						Press "Search again" in the Artlyrics menu (Music page) after adding a file.
						""", StandardCharsets.UTF_8);
			}
		} catch (IOException ignored) {
			// not important: the folder is only read when it exists
		}
	}

	@Override
	public LyricsResult lookup(TrackQuery track) throws InterruptedException {
		if (!track.isUsable() || !Files.isDirectory(folder)) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No lyrics file");
		}
		Path best = null;
		int bestScore = 0;
		try (Stream<Path> files = Files.walk(folder, 3)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				if (Thread.currentThread().isInterrupted()) {
					throw new InterruptedException();
				}
				String name = file.getFileName().toString();
				String lower = name.toLowerCase(Locale.ROOT);
				if (!Files.isRegularFile(file) || !(lower.endsWith(".lrc") || lower.endsWith(".txt")) || lower.equals("readme.txt")) {
					continue;
				}
				int score = matchName(name.substring(0, name.length() - 4), track);
				if (score == 0 && lower.endsWith(".lrc")) {
					score = matchTags(file, track);
				}
				if (score > bestScore) {
					bestScore = score;
					best = file;
				}
			}
		} catch (IOException e) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "Couldn't read the lyrics folder");
		}
		if (best == null) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, "No lyrics file");
		}
		return read(best, track.durationMs());
	}

	/** How well a file name fits the song: 3 = artist and title, 2 = title only (no artist in the name), 0 = no. */
	static int matchName(String baseName, TrackQuery track) {
		List<TrackQuery> candidates = new java.util.ArrayList<>();
		candidates.add(track);
		candidates.addAll(track.fallbacks());
		int best = 0;
		for (TrackQuery q : candidates) {
			int dash = baseName.indexOf(" - ");
			if (dash > 0) {
				String left = baseName.substring(0, dash);
				String right = baseName.substring(dash + 3);
				if (TextMatch.sameTitle(right, q.title()) && TextMatch.artistMatches(left, q.artist())) {
					return 3;
				}
				if (TextMatch.sameTitle(left, q.title()) && TextMatch.artistMatches(right, q.artist())) {
					return 3; // "Title - Artist"
				}
			}
			if (TextMatch.sameTitle(baseName, q.title())) {
				best = Math.max(best, 2);
			}
		}
		return best;
	}

	private static int matchTags(Path file, TrackQuery track) {
		String text = readText(file, 8192);
		if (text == null) {
			return 0;
		}
		String ar = "";
		String ti = "";
		Matcher m = TAG.matcher(text);
		while (m.find()) {
			if (m.group(1).equalsIgnoreCase("ar")) {
				ar = m.group(2);
			} else {
				ti = m.group(2);
			}
		}
		if (ti.isEmpty() || !TextMatch.sameTitle(ti, track.title())) {
			return 0;
		}
		return ar.isEmpty() ? 2 : TextMatch.artistMatches(ar, track.artist()) ? 3 : 0;
	}

	private static LyricsResult read(Path file, long durationMs) {
		String text = readText(file, MAX_FILE_BYTES);
		String name = file.getFileName().toString();
		if (text == null || text.isBlank()) {
			return LyricsResult.of(LyricsResult.Status.NOT_FOUND, name);
		}
		String source = "Lyrics folder: " + name;
		if (name.toLowerCase(Locale.ROOT).endsWith(".lrc")) {
			Lyrics lyrics = LrcParser.parse(text, durationMs, source);
			if (lyrics.lines().stream().anyMatch(l -> !l.isBlank())) {
				return LyricsResult.found(lyrics);
			}
			text = text.replaceAll("(?m)^\\[[a-zA-Z]+:[^]]*]\\s*$", ""); // an .lrc without timestamps: drop the tags
		}
		// A file the player chose to add: show it even without timestamps.
		return LyricsResult.found(EstimatedTiming.build(text, durationMs, source + " (timing estimated)"));
	}

	private static String readText(Path file, long limit) {
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				return null;
			}
			byte[] bytes = Files.readAllBytes(file);
			String s;
			if (bytes.length > limit) {
				s = new String(bytes, 0, (int) limit, StandardCharsets.UTF_8); // just the start, for the tags
			} else {
				try {
					s = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
				} catch (CharacterCodingException e) {
					// Not UTF-8: probably an older file saved in a Windows code page (Baltic covers Latvian).
					String cs = java.nio.charset.Charset.isSupported("windows-1257") ? "windows-1257" : "ISO-8859-1";
					s = new String(bytes, java.nio.charset.Charset.forName(cs));
				}
			}
			return s.startsWith("\uFEFF") ? s.substring(1) : s; // byte order mark
		} catch (IOException e) {
			return null;
		}
	}
}
