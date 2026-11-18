package com.artlyrics.holiday;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HolidayFinderTest {
	private static HolidayFinder.Active find(Region r, int y, int m, int d, HolidayFinder.Mode mode) {
		return HolidayFinder.find(r, LocalDate.of(y, m, d), mode);
	}

	private static String name(Region r, int y, int m, int d) {
		HolidayFinder.Active a = find(r, y, m, d, HolidayFinder.Mode.SEASONS);
		return a == null ? null : a.holiday().name();
	}

	@Test
	void latvia() {
		HolidayFinder.Active today = find(Region.LATVIA, 2026, 11, 18, HolidayFinder.Mode.SEASONS);
		assertNotNull(today);
		assertEquals("Proclamation Day of Latvia", today.holiday().name());
		assertEquals(0, today.daysAway());
		assertEquals(HolidayTheme.NATIONAL, today.theme());
		assertEquals(0x9E3039, today.palette().colorAt(0f, 0)); // carmine, white, carmine
		assertEquals(0xF4F4F4, today.palette().colorAt(0.5f, 0));
		assertEquals("Christmas", name(Region.LATVIA, 2026, 12, 10));
		assertEquals("New Year", name(Region.LATVIA, 2026, 12, 30));
		assertEquals("Jāņi (Midsummer)", name(Region.LATVIA, 2026, 6, 20));
		assertNull(name(Region.LATVIA, 2026, 7, 20));
		// "Always nearest" always has one: in July the nearest is Midsummer.
		assertEquals("Jāņi (Midsummer)", find(Region.LATVIA, 2026, 7, 10, HolidayFinder.Mode.NEAREST).holiday().name());
		assertNull(find(Region.LATVIA, 2026, 11, 18, HolidayFinder.Mode.OFF));
	}

	@Test
	void otherRegions() {
		assertEquals("Independence Day", name(Region.UKRAINE, 2026, 8, 22));
		assertEquals("Easter", name(Region.UKRAINE, 2026, 4, 10)); // Orthodox Easter, 12 April 2026
		assertEquals("Chuseok", name(Region.SOUTH_KOREA, 2026, 9, 22));
		assertEquals("Seollal", name(Region.SOUTH_KOREA, 2026, 2, 12));
		assertEquals("Thanksgiving", name(Region.UNITED_STATES, 2026, 11, 22));
		assertEquals("Halloween", name(Region.UNITED_STATES, 2026, 10, 28));
		assertEquals("Independence Day", name(Region.UNITED_STATES, 2026, 7, 3));
		assertEquals("Midsommar", name(Region.SWEDEN, 2026, 6, 17));
		assertEquals("Diwali", name(Region.INDIA, 2026, 11, 6));
		assertEquals("Día de Muertos", name(Region.MEXICO, 2026, 10, 30));
	}

	@Test
	void everyRegionHasDatesForYears() {
		for (Region r : Region.values()) {
			for (Holiday h : r.holidays()) {
				for (int y = 2024; y <= 2040; y++) {
					assertNotNull(h.dateIn(y), r + " " + h.name() + " " + y);
				}
			}
			assertNotNull(HolidayFinder.find(r, LocalDate.of(2026, 3, 1), HolidayFinder.Mode.NEAREST), r.toString());
		}
	}

	@Test
	void regionCodes() {
		assertEquals(Region.LATVIA, Region.forCode("lv_lv"));
		assertEquals(Region.LATVIA, Region.forCode("LV"));
		assertEquals(Region.UNITED_KINGDOM, Region.forCode("en_GB"));
		assertEquals(Region.UNITED_KINGDOM, Region.forCode("UK"));
		assertEquals(Region.SOUTH_KOREA, Region.forCode("ko-KR"));
		assertNull(Region.forCode("xx"));
		assertNull(Region.forCode(""));
	}
}
