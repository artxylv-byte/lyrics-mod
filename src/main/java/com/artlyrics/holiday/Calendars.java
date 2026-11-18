package com.artlyrics.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/** Dates of movable holidays. */
public final class Calendars {
	public static final ZoneOffset CHINA = ZoneOffset.ofHours(8);
	public static final ZoneOffset KOREA = ZoneOffset.ofHours(9);
	public static final ZoneOffset INDIA = ZoneOffset.ofHoursMinutes(5, 30);

	private Calendars() {
	}

	/** Western (Gregorian) Easter Sunday. */
	public static LocalDate easter(int year) {
		int a = year % 19;
		int b = year / 100;
		int c = year % 100;
		int d = b / 4;
		int e = b % 4;
		int f = (b + 8) / 25;
		int g = (b - f + 1) / 3;
		int h = (19 * a + b - d - g + 15) % 30;
		int i = c / 4;
		int k = c % 4;
		int l = (32 + 2 * e + 2 * i - h - k) % 7;
		int m = (a + 11 * h + 22 * l) / 451;
		int month = (h + l - 7 * m + 114) / 31;
		int day = (h + l - 7 * m + 114) % 31 + 1;
		return LocalDate.of(year, month, day);
	}

	/** Orthodox Easter Sunday (Julian reckoning), as a Gregorian date. Valid 1900–2099. */
	public static LocalDate orthodoxEaster(int year) {
		int a = year % 4;
		int b = year % 7;
		int c = year % 19;
		int d = (19 * c + 15) % 30;
		int e = (2 * a + 4 * b - d + 34) % 7;
		int month = (d + e + 114) / 31;
		int day = (d + e + 114) % 31 + 1;
		return LocalDate.of(year, month, day).plusDays(13);
	}

	/** The {@code n}th (1-based) given weekday of a month, e.g. the 4th Thursday of November. */
	public static LocalDate nthWeekday(int year, int month, DayOfWeek day, int n) {
		return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day));
	}

	/** The first given weekday on or after a date, e.g. the Friday between 19 and 25 June. */
	public static LocalDate weekdayFrom(int year, MonthDay from, DayOfWeek day) {
		return from.atYear(year).with(TemporalAdjusters.nextOrSame(day));
	}

	/**
	 * A date of the Chinese / Korean lunisolar calendar in the given Gregorian year: {@code month} (1–12, not a
	 * leap month) and {@code day}. Months start on the day of a new moon; the 11th month is the one with the winter
	 * solstice, and months are counted from there. When a year has 13 months, the first month without a principal
	 * solar term (a multiple of 30° of the sun's longitude) is the leap month and repeats the previous number.
	 */
	public static LocalDate lunar(int year, int month, int day, ZoneOffset zone) {
		long first = month11(year - 1, zone);
		long last = month11(year, zone);
		int months = (int) (last - first);
		int leap = -1;
		if (months == 13) {
			for (int i = 1; i < 13; i++) {
				if (!hasPrincipalTerm(first + i, zone)) {
					leap = i;
					break;
				}
			}
		}
		int number = 11;
		for (int i = 1; i < months; i++) {
			if (i == leap) {
				continue;
			}
			number = number % 12 + 1;
			if (number == month) {
				return Astronomy.localDate(Astronomy.moonPhase(first + i, false), zone).plusDays(day - 1);
			}
		}
		throw new IllegalStateException("No lunar month " + month + " in " + year);
	}

	/** Number of the new moon that starts the 11th lunar month (the one with the December solstice) of a year. */
	private static long month11(int year, ZoneOffset zone) {
		double solstice = Astronomy.sunAt(270, Astronomy.julianDay(LocalDate.of(year, 12, 21)));
		return Astronomy.newMoonOnOrBefore(Astronomy.localDate(solstice, zone), zone);
	}

	/** Whether the lunar month starting with new moon {@code k} contains a principal solar term. */
	private static boolean hasPrincipalTerm(long k, ZoneOffset zone) {
		double start = midnight(Astronomy.localDate(Astronomy.moonPhase(k, false), zone), zone);
		double end = midnight(Astronomy.localDate(Astronomy.moonPhase(k + 1, false), zone), zone);
		int a = (int) Math.floor(Astronomy.sunLongitude(start) / 30);
		int b = (int) Math.floor(Astronomy.sunLongitude(end) / 30);
		return a != b;
	}

	private static double midnight(LocalDate date, ZoneOffset zone) {
		return Astronomy.julianDay(date) - zone.getTotalSeconds() / 86400.0;
	}

	/** Diwali (Lakshmi Puja): the evening of the new moon of Kartika, the first new moon from mid-October. */
	public static LocalDate diwali(int year) {
		long k = Astronomy.newMoonOnOrBefore(LocalDate.of(year, 10, 13), INDIA) + 1;
		double nm = Astronomy.moonPhase(k, false);
		// The new-moon day (amavasya) is observed on the evening before the exact moment, so step back ~19 hours.
		return Astronomy.localDate(nm - 19.0 / 24.0, INDIA);
	}

	/** Holi: the full moon of Phalguna, the lunar month that starts while the sun is in sidereal Aquarius. */
	public static LocalDate holi(int year) {
		long k = Astronomy.newMoonOnOrBefore(LocalDate.of(year, 2, 12), INDIA) + 1;
		double fm = Astronomy.moonPhase(k, true);
		return Astronomy.localDate(fm, INDIA);
	}
}
