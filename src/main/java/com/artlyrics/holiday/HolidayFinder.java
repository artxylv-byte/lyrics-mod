package com.artlyrics.holiday;

import com.artlyrics.config.Palette;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Picks the holiday whose theme to show for a region on a given day. */
public final class HolidayFinder {
	/** When holiday themes are shown. */
	public enum Mode {
		/** Never. */
		OFF,
		/** In the days around each holiday (e.g. all of December for Christmas). */
		SEASONS,
		/** Always: the nearest holiday, before or after today. */
		NEAREST;

		public String translationKey() {
			return "artlyrics.holidays." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * The holiday shown now. {@code daysAway} is negative before it, 0 on the day and positive after it.
	 */
	public record Active(Region region, Holiday holiday, LocalDate date, long daysAway) {
		public HolidayTheme theme() {
			return holiday.theme();
		}

		public Palette palette() {
			return holiday.theme().palette(region);
		}

		public int[] decorationColors() {
			return holiday.theme().decorationColors(region);
		}
	}

	private HolidayFinder() {
	}

	public static @Nullable Active find(Region region, LocalDate today, Mode mode) {
		if (region == null || mode == Mode.OFF) {
			return null;
		}
		Active best = null;
		long bestDistance = Long.MAX_VALUE;
		for (Holiday h : region.holidays()) {
			for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
				LocalDate date;
				try {
					date = h.dateIn(year);
				} catch (RuntimeException e) {
					continue; // a date rule that can't answer for this year
				}
				long away = ChronoUnit.DAYS.between(date, today); // negative: still to come
				if (mode == Mode.SEASONS && (away < -h.daysBefore() || away > h.daysAfter())) {
					continue;
				}
				// The closest wins, with small holidays counting as three times further away (so Seollal beats
				// Valentine's Day a few days before both); between equally close ones, the one still to come.
				long distance = Math.abs(away) * (h.theme().minor() ? 6 : 2) + (away > 0 ? 1 : 0);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new Active(region, h, date, away);
				}
			}
		}
		return best;
	}

	/** A theme tried out in the settings, shown as if its holiday were today. */
	public static Active preview(Region region, HolidayTheme theme) {
		for (Holiday h : region.holidays()) {
			if (h.theme() == theme) {
				return new Active(region, h, LocalDate.now(), 0);
			}
		}
		return new Active(region, new Holiday(theme.label(), theme, y -> LocalDate.now()), LocalDate.now(), 0);
	}
}
