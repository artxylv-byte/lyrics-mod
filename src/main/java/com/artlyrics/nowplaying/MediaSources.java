package com.artlyrics.nowplaying;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

/** Picks the right {@link MediaSource} for the operating system. */
public final class MediaSources {
	private MediaSources() {
	}

	public static MediaSource forThisComputer(Path dataDir) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return new WindowsMediaSource(dataDir);
		}
		if (os.contains("mac") || os.contains("darwin")) {
			return new MacMediaSource();
		}
		if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) {
			return new LinuxMediaSource();
		}
		return new Unsupported(os);
	}

	private static final class Unsupported implements MediaSource {
		private final String os;

		Unsupported(String os) {
			this.os = os;
		}

		@Override
		public void start() {
		}

		@Override
		public @Nullable NowPlaying current() {
			return null;
		}

		@Override
		public String problem() {
			return "Music detection isn't supported on " + os;
		}

		@Override
		public void close() {
		}
	}
}
