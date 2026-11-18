package com.artlyrics.holiday;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Just enough astronomy for holidays that follow the moon: times of new and full moons and of the sun reaching a
 * given longitude (Jean Meeus, "Astronomical Algorithms", chapters 25 and 49). Good to a few minutes, which is
 * plenty for finding the day.
 */
final class Astronomy {
	private static final double SYNODIC_MONTH = 29.530588861;

	private Astronomy() {
	}

	/** Julian Day of the new moon (phase 0) or full moon (phase 0.5) number {@code k} (k = 0 near 6 Jan 2000). */
	static double moonPhase(double k, boolean full) {
		if (full) {
			k = Math.floor(k) + 0.5;
		}
		double t = k / 1236.85;
		double jde = 2451550.09766 + SYNODIC_MONTH * k + 0.00015437 * t * t - 0.000000150 * t * t * t
				+ 0.00000000073 * t * t * t * t;
		double e = 1 - 0.002516 * t - 0.0000074 * t * t;
		double m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t * t - 0.00000011 * t * t * t);
		double mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t - 0.000000058 * t * t * t * t);
		double f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t + 0.000000011 * t * t * t * t);
		double om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t * t + 0.00000215 * t * t * t);
		double c;
		if (!full) {
			c = -0.40720 * Math.sin(mp) + 0.17241 * e * Math.sin(m) + 0.01608 * Math.sin(2 * mp) + 0.01039 * Math.sin(2 * f)
					+ 0.00739 * e * Math.sin(mp - m) - 0.00514 * e * Math.sin(mp + m) + 0.00208 * e * e * Math.sin(2 * m);
		} else {
			c = -0.40614 * Math.sin(mp) + 0.17302 * e * Math.sin(m) + 0.01614 * Math.sin(2 * mp) + 0.01043 * Math.sin(2 * f)
					+ 0.00734 * e * Math.sin(mp - m) - 0.00515 * e * Math.sin(mp + m) + 0.00209 * e * e * Math.sin(2 * m);
		}
		c += -0.00111 * Math.sin(mp - 2 * f) - 0.00057 * Math.sin(mp + 2 * f) + 0.00056 * e * Math.sin(2 * mp + m)
				- 0.00042 * Math.sin(3 * mp) + 0.00042 * e * Math.sin(m + 2 * f) + 0.00038 * e * Math.sin(m - 2 * f)
				- 0.00024 * e * Math.sin(2 * mp - m) - 0.00017 * Math.sin(om) - 0.00007 * Math.sin(mp + 2 * m)
				+ 0.00004 * Math.sin(2 * mp - 2 * f) + 0.00004 * Math.sin(3 * m) + 0.00003 * Math.sin(mp + m - 2 * f)
				+ 0.00003 * Math.sin(2 * mp + 2 * f) - 0.00003 * Math.sin(mp + m + 2 * f) + 0.00003 * Math.sin(mp - m + 2 * f)
				- 0.00002 * Math.sin(mp - m - 2 * f) - 0.00002 * Math.sin(3 * mp + m) + 0.00002 * Math.sin(4 * mp);
		return jde + c;
	}

	/** The sun's apparent longitude in degrees at Julian Day {@code jd}. */
	static double sunLongitude(double jd) {
		double t = (jd - 2451545.0) / 36525.0;
		double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
		double m = rad(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
		double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m) + (0.019993 - 0.000101 * t) * Math.sin(2 * m)
				+ 0.000289 * Math.sin(3 * m);
		double om = rad(125.04 - 1934.136 * t);
		return norm(l0 + c - 0.00569 - 0.00478 * Math.sin(om));
	}

	/** Julian Day when the sun reaches {@code longitude} degrees, searching near {@code guessJd}. */
	static double sunAt(double longitude, double guessJd) {
		double jd = guessJd;
		for (int i = 0; i < 50; i++) {
			double diff = norm(longitude - sunLongitude(jd) + 180) - 180;
			jd += diff * 365.2422 / 360.0;
			if (Math.abs(diff) < 1e-6) {
				break;
			}
		}
		return jd;
	}

	/** Moon number {@code k} of the last new moon whose local date is on or before {@code date}. */
	static long newMoonOnOrBefore(LocalDate date, ZoneOffset zone) {
		double jd = julianDay(date);
		long k = (long) Math.floor((jd - 2451550.09766) / SYNODIC_MONTH);
		while (localDate(moonPhase(k, false), zone).isAfter(date)) {
			k--;
		}
		while (!localDate(moonPhase(k + 1, false), zone).isAfter(date)) {
			k++;
		}
		return k;
	}

	static double julianDay(LocalDate date) {
		return date.toEpochDay() + 2440587.5;
	}

	static LocalDate localDate(double jd, ZoneOffset zone) {
		long millis = Math.round((jd - 2440587.5) * 86_400_000.0);
		return Instant.ofEpochMilli(millis).atOffset(zone).toLocalDate();
	}

	static Instant instant(double jd) {
		return Instant.ofEpochMilli(Math.round((jd - 2440587.5) * 86_400_000.0));
	}

	private static double rad(double deg) {
		return Math.toRadians(norm(deg));
	}

	private static double norm(double deg) {
		double d = deg % 360.0;
		return d < 0 ? d + 360.0 : d;
	}
}
