package com.artlyrics.holiday;

import java.time.LocalDate;

/**
 * One holiday of a region: its name, its look, how to find its date in a given year, and how many days before
 * and after it its theme is shown in "holiday seasons" mode.
 */
public record Holiday(String name, HolidayTheme theme, DateRule rule, int daysBefore, int daysAfter) {
	/** Finds the holiday's date in a year. */
	@FunctionalInterface
	public interface DateRule {
		LocalDate in(int year);
	}

	public Holiday(String name, HolidayTheme theme, DateRule rule) {
		this(name, theme, rule, defaultBefore(theme), defaultAfter(theme));
	}

	/** The same holiday with a different season around it. */
	public Holiday season(int before, int after) {
		return new Holiday(name, theme, rule, before, after);
	}

	public LocalDate dateIn(int year) {
		return rule.in(year);
	}

	static int defaultBefore(HolidayTheme t) {
		return switch (t) {
			case CHRISTMAS -> 24;
			case LUNAR_NEW_YEAR, HALLOWEEN -> 10;
			case EASTER, MIDSUMMER, AUTUMN, MOON -> 7;
			case VALENTINE, LANTERNS, CARNIVAL, DAY_OF_THE_DEAD -> 5;
			case NEW_YEAR, STARS, DIWALI -> 4;
			case NATIONAL, ST_PATRICK, BLOSSOM, DRAGON_BOAT -> 3;
			case HOLI, RED_AND_WHITE, BONFIRE -> 2;
		};
	}

	static int defaultAfter(HolidayTheme t) {
		return switch (t) {
			case LUNAR_NEW_YEAR -> 4;
			case MOON -> 2;
			case RED_AND_WHITE -> 6;
			case NEW_YEAR, EASTER, MIDSUMMER, AUTUMN, LANTERNS, DAY_OF_THE_DEAD, DIWALI, CHRISTMAS -> 1;
			default -> 0;
		};
	}
}
