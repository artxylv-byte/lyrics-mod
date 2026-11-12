package com.artlyrics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Timing corrections for single songs, set with the "lyrics earlier / later" keys and remembered between games
 * (in {@code config/artlyrics/song-timing.json}). Useful when a song's lyrics are a little off, or were timed by
 * estimate.
 */
public final class TimingOffsets {
	private static final Logger LOGGER = LoggerFactory.getLogger("Artlyrics");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_SONGS = 2000;
	public static final int LIMIT_MS = 60_000;

	private final @Nullable Path path;
	private final Map<String, Integer> offsets = new LinkedHashMap<>();

	public TimingOffsets(@Nullable Path path) {
		this.path = path;
		load();
	}

	/** Milliseconds to add for this song; positive shows lyrics earlier. */
	public synchronized int get(@Nullable String songKey) {
		return songKey == null ? 0 : offsets.getOrDefault(songKey, 0);
	}

	/** Adds {@code deltaMs} to the song's correction and saves; returns the new value. */
	public synchronized int adjust(String songKey, int deltaMs) {
		int value = Math.max(-LIMIT_MS, Math.min(LIMIT_MS, get(songKey) + deltaMs));
		set(songKey, value);
		return value;
	}

	public synchronized void set(String songKey, int value) {
		offsets.remove(songKey);
		if (value != 0) {
			offsets.put(songKey, value);
			while (offsets.size() > MAX_SONGS) {
				offsets.remove(offsets.keySet().iterator().next());
			}
		}
		save();
	}

	private void load() {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Map<String, Integer> read = GSON.fromJson(r, new TypeToken<Map<String, Integer>>() { }.getType());
			if (read != null) {
				read.forEach((k, v) -> {
					if (k != null && v != null && v != 0) {
						offsets.put(k, Math.max(-LIMIT_MS, Math.min(LIMIT_MS, v)));
					}
				});
			}
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("[Artlyrics] Couldn't read {}", path, e);
		}
	}

	private void save() {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(offsets, w);
			}
		} catch (IOException e) {
			LOGGER.warn("[Artlyrics] Couldn't save {}", path, e);
		}
	}
}
