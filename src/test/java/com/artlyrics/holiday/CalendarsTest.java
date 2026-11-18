package com.artlyrics.holiday;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarsTest {
	private static LocalDate d(int y, int m, int day) {
		return LocalDate.of(y, m, day);
	}

	@Test
	void easter() {
		assertEquals(d(2024, 3, 31), Calendars.easter(2024));
		assertEquals(d(2025, 4, 20), Calendars.easter(2025));
		assertEquals(d(2026, 4, 5), Calendars.easter(2026));
		assertEquals(d(2027, 3, 28), Calendars.easter(2027));
		assertEquals(d(2024, 5, 5), Calendars.orthodoxEaster(2024));
		assertEquals(d(2025, 4, 20), Calendars.orthodoxEaster(2025));
		assertEquals(d(2026, 4, 12), Calendars.orthodoxEaster(2026));
		assertEquals(d(2027, 5, 2), Calendars.orthodoxEaster(2027));
	}

	@Test
	void weekdayRules() {
		assertEquals(d(2026, 11, 26), Calendars.nthWeekday(2026, 11, DayOfWeek.THURSDAY, 4)); // US Thanksgiving
		assertEquals(d(2026, 10, 12), Calendars.nthWeekday(2026, 10, DayOfWeek.MONDAY, 2)); // Canadian Thanksgiving
		assertEquals(d(2026, 6, 19), Calendars.weekdayFrom(2026, MonthDay.of(6, 19), DayOfWeek.FRIDAY)); // Midsummer Eve
	}

	@Test
	void lunarNewYear() {
		assertEquals(d(2023, 1, 22), Calendars.lunar(2023, 1, 1, Calendars.CHINA));
		assertEquals(d(2024, 2, 10), Calendars.lunar(2024, 1, 1, Calendars.CHINA));
		assertEquals(d(2025, 1, 29), Calendars.lunar(2025, 1, 1, Calendars.CHINA));
		assertEquals(d(2026, 2, 17), Calendars.lunar(2026, 1, 1, Calendars.CHINA));
		assertEquals(d(2027, 2, 6), Calendars.lunar(2027, 1, 1, Calendars.CHINA));
		assertEquals(d(2028, 1, 26), Calendars.lunar(2028, 1, 1, Calendars.CHINA));
		assertEquals(d(2033, 1, 31), Calendars.lunar(2033, 1, 1, Calendars.CHINA));
		assertEquals(d(2034, 2, 19), Calendars.lunar(2034, 1, 1, Calendars.CHINA));
		assertEquals(d(2026, 2, 17), Calendars.lunar(2026, 1, 1, Calendars.KOREA)); // Seollal
	}

	@Test
	void chuseokAndOtherLunarDays() {
		assertEquals(d(2023, 9, 29), Calendars.lunar(2023, 8, 15, Calendars.KOREA));
		assertEquals(d(2024, 9, 17), Calendars.lunar(2024, 8, 15, Calendars.KOREA));
		assertEquals(d(2025, 10, 6), Calendars.lunar(2025, 8, 15, Calendars.KOREA));
		assertEquals(d(2026, 9, 25), Calendars.lunar(2026, 8, 15, Calendars.KOREA));
		assertEquals(d(2024, 9, 17), Calendars.lunar(2024, 8, 15, Calendars.CHINA)); // Mid-Autumn
		assertEquals(d(2024, 5, 15), Calendars.lunar(2024, 4, 8, Calendars.KOREA)); // Buddha's Birthday
		assertEquals(d(2025, 5, 5), Calendars.lunar(2025, 4, 8, Calendars.KOREA));
		assertEquals(d(2026, 5, 24), Calendars.lunar(2026, 4, 8, Calendars.KOREA));
		assertEquals(d(2024, 6, 10), Calendars.lunar(2024, 5, 5, Calendars.CHINA)); // Dragon Boat
		assertEquals(d(2025, 5, 31), Calendars.lunar(2025, 5, 5, Calendars.CHINA));
		assertEquals(d(2026, 6, 19), Calendars.lunar(2026, 5, 5, Calendars.CHINA));
	}

	@Test
	void indianFestivalsWithinADay() {
		assertNear(d(2024, 10, 31), Calendars.diwali(2024));
		assertNear(d(2025, 10, 20), Calendars.diwali(2025));
		assertNear(d(2026, 11, 8), Calendars.diwali(2026));
		assertNear(d(2027, 10, 29), Calendars.diwali(2027));
		assertNear(d(2024, 3, 25), Calendars.holi(2024));
		assertNear(d(2025, 3, 14), Calendars.holi(2025));
		assertNear(d(2026, 3, 4), Calendars.holi(2026));
		assertNear(d(2027, 3, 22), Calendars.holi(2027));
	}

	private static void assertNear(LocalDate expected, LocalDate actual) {
		long diff = Math.abs(expected.toEpochDay() - actual.toEpochDay());
		assertTrue(diff <= 1, "expected about " + expected + " but got " + actual);
	}
}
