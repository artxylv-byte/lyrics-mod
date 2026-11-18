package com.artlyrics.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.artlyrics.holiday.HolidayTheme.*;

/** A country or region, with its flag colors and the holidays it celebrates. */
public enum Region {
	// Europe
	LATVIA("LV", "Latvia", 0x9E3039, 0xF4F4F4, 0x9E3039),
	LITHUANIA("LT", "Lithuania", 0xFDB913, 0x00A35A, 0xC1272D),
	ESTONIA("EE", "Estonia", 0x2F8AE0, 0x4A4A4A, 0xF4F4F4),
	FINLAND("FI", "Finland", 0xF4F4F4, 0x2F6FD0, 0xF4F4F4),
	SWEDEN("SE", "Sweden", 0x2F86D0, 0xFECC00, 0x2F86D0),
	NORWAY("NO", "Norway", 0xD0283E, 0xF4F4F4, 0x2D4FA8),
	DENMARK("DK", "Denmark", 0xD0283E, 0xF4F4F4, 0xD0283E),
	GERMANY("DE", "Germany", 0x4A4A4A, 0xE02020, 0xFFCE00),
	AUSTRIA("AT", "Austria", 0xED2939, 0xF4F4F4, 0xED2939),
	SWITZERLAND("CH", "Switzerland", 0xDA291C, 0xF4F4F4, 0xDA291C),
	NETHERLANDS("NL", "Netherlands", 0xC8303C, 0xF4F4F4, 0x2F5DB0),
	BELGIUM("BE", "Belgium", 0x4A4A4A, 0xFDDA24, 0xEF3340),
	FRANCE("FR", "France", 0x2F6FD0, 0xF4F4F4, 0xEF4135),
	SPAIN("ES", "Spain", 0xC8303C, 0xF1BF00, 0xC8303C),
	PORTUGAL("PT", "Portugal", 0x1F9A3A, 0xFFCE00, 0xE02020),
	ITALY("IT", "Italy", 0x1FA055, 0xF4F4F4, 0xCE2B37),
	UNITED_KINGDOM("GB", "United Kingdom", 0x2F4FA8, 0xF4F4F4, 0xC8102E),
	IRELAND("IE", "Ireland", 0x169B62, 0xF4F4F4, 0xFF883E),
	POLAND("PL", "Poland", 0xF4F4F4, 0xDC143C),
	CZECHIA("CZ", "Czechia", 0xF4F4F4, 0xD7141A, 0x2F5DB0),
	SLOVAKIA("SK", "Slovakia", 0xF4F4F4, 0x2F6FD0, 0xEE1C25),
	HUNGARY("HU", "Hungary", 0xCE2939, 0xF4F4F4, 0x4F9A5A),
	ROMANIA("RO", "Romania", 0x2F5DB0, 0xFCD116, 0xCE1126),
	BULGARIA("BG", "Bulgaria", 0xF4F4F4, 0x00966E, 0xD62612),
	GREECE("GR", "Greece", 0x2F7AD0, 0xF4F4F4, 0x2F7AD0),
	CROATIA("HR", "Croatia", 0xE02020, 0xF4F4F4, 0x2F3FB0),
	SERBIA("RS", "Serbia", 0xC6363C, 0x2F5DA0, 0xF4F4F4),
	UKRAINE("UA", "Ukraine", 0x2F7AE0, 0xFFD700),
	TURKEY("TR", "Türkiye", 0xE30A17, 0xF4F4F4),
	// Americas
	UNITED_STATES("US", "United States", 0xC8283E, 0xF4F4F4, 0x3C5BB0),
	CANADA("CA", "Canada", 0xD80621, 0xF4F4F4, 0xD80621),
	MEXICO("MX", "Mexico", 0x1F8A57, 0xF4F4F4, 0xCE1126),
	BRAZIL("BR", "Brazil", 0x1FAA4B, 0xFFDF00, 0x2F5DB0),
	// Asia and Oceania
	SOUTH_KOREA("KR", "South Korea", 0xF4F4F4, 0xCD2E3A, 0x2F6FD0),
	JAPAN("JP", "Japan", 0xF4F4F4, 0xD0203D, 0xF4F4F4),
	CHINA("CN", "China", 0xEE1C25, 0xFFDE00),
	TAIWAN("TW", "Taiwan", 0xFE2020, 0x2F3FB0, 0xF4F4F4),
	INDIA("IN", "India", 0xFF9933, 0xF4F4F4, 0x1F9A1F),
	AUSTRALIA("AU", "Australia", 0x2F4FB0, 0xF4F4F4, 0xE02020);

	private final String code;
	private final String displayName;
	private final int[] flag;
	private List<Holiday> holidays;

	Region(String code, String displayName, int... flag) {
		this.code = code;
		this.displayName = displayName;
		this.flag = flag;
	}

	/** ISO country code, e.g. "LV". */
	public String code() {
		return code;
	}

	public String displayName() {
		return displayName;
	}

	/** Flag colors, left to right or top to bottom. */
	public int[] flag() {
		return flag.clone();
	}

	/** The region for an ISO country code ("lv", "LV" or a language tag like "lv_lv"), or null. */
	public static Region forCode(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		String c = code.strip();
		int sep = Math.max(c.lastIndexOf('_'), c.lastIndexOf('-'));
		if (sep >= 0) {
			c = c.substring(sep + 1);
		}
		c = c.toUpperCase(Locale.ROOT);
		if (c.equals("UK")) {
			c = "GB";
		}
		for (Region r : values()) {
			if (r.code.equals(c)) {
				return r;
			}
		}
		return null;
	}

	public synchronized List<Holiday> holidays() {
		if (holidays == null) {
			holidays = List.copyOf(build());
		}
		return holidays;
	}

	// ---- holiday lists ----

	private static Holiday on(String name, HolidayTheme theme, int month, int day) {
		return new Holiday(name, theme, y -> LocalDate.of(y, month, day));
	}

	private static Holiday rule(String name, HolidayTheme theme, Holiday.DateRule rule) {
		return new Holiday(name, theme, rule);
	}

	private static Holiday newYear() {
		return on("New Year", NEW_YEAR, 1, 1);
	}

	private static Holiday christmas() {
		return on("Christmas", CHRISTMAS, 12, 25);
	}

	private static Holiday valentines() {
		return on("Valentine's Day", VALENTINE, 2, 14);
	}

	private static Holiday easter() {
		return rule("Easter", EASTER, Calendars::easter);
	}

	private static Holiday orthodoxEaster() {
		return rule("Easter", EASTER, Calendars::orthodoxEaster);
	}

	private static Holiday carnival(String name) {
		return rule(name, CARNIVAL, y -> Calendars.easter(y).minusDays(47));
	}

	private static Holiday midsummer(String name, int month, int day) {
		return on(name, MIDSUMMER, month, day);
	}

	private static Holiday midsummerEve(String name) {
		return rule(name, MIDSUMMER, y -> Calendars.weekdayFrom(y, MonthDay.of(6, 19), DayOfWeek.FRIDAY));
	}

	private static Holiday flagDay(String name, int month, int day) {
		return on(name, NATIONAL, month, day);
	}

	private static Holiday lunar(String name, HolidayTheme theme, int month, int day, java.time.ZoneOffset zone) {
		return rule(name, theme, y -> Calendars.lunar(y, month, day, zone));
	}

	private List<Holiday> build() {
		List<Holiday> h = new ArrayList<>();
		h.add(newYear());
		switch (this) {
			case LATVIA -> {
				h.addAll(List.of(valentines(), easter(), flagDay("Restoration of Independence Day", 5, 4),
						midsummer("Jāņi (Midsummer)", 6, 23), flagDay("Proclamation Day of Latvia", 11, 18).season(7, 0), christmas()));
			}
			case LITHUANIA -> h.addAll(List.of(flagDay("Day of Restoration of the State", 2, 16), carnival("Užgavėnės"),
					flagDay("Independence Restoration Day", 3, 11), easter(), midsummer("Joninės (Midsummer)", 6, 24),
					flagDay("Statehood Day", 7, 6), valentines(), christmas()));
			case ESTONIA -> h.addAll(List.of(valentines(), flagDay("Independence Day", 2, 24), easter(),
					midsummer("Jaanipäev (Midsummer)", 6, 23), christmas()));
			case FINLAND -> h.addAll(List.of(valentines(), easter(), on("Vappu", CARNIVAL, 5, 1),
					midsummerEve("Juhannus (Midsummer)"), flagDay("Independence Day", 12, 6), christmas()));
			case SWEDEN -> h.addAll(List.of(valentines(), easter(), flagDay("National Day", 6, 6),
					midsummerEve("Midsommar"), on("Lucia", CHRISTMAS, 12, 13).season(3, 0), christmas()));
			case NORWAY -> h.addAll(List.of(valentines(), easter(), flagDay("Constitution Day", 5, 17),
					midsummer("Sankthans (Midsummer)", 6, 23), christmas()));
			case DENMARK -> h.addAll(List.of(valentines(), rule("Fastelavn", CARNIVAL, y -> Calendars.easter(y).minusDays(49)),
					easter(), flagDay("Constitution Day", 6, 5), midsummer("Sankt Hans (Midsummer)", 6, 23), christmas()));
			case GERMANY -> h.addAll(List.of(valentines(), rule("Karneval", CARNIVAL, y -> Calendars.easter(y).minusDays(48)),
					easter(), flagDay("German Unity Day", 10, 3), christmas()));
			case AUSTRIA -> h.addAll(List.of(valentines(), carnival("Fasching"), easter(), flagDay("National Day", 10, 26), christmas()));
			case SWITZERLAND -> h.addAll(List.of(valentines(), carnival("Fasnacht"), easter(), flagDay("Swiss National Day", 8, 1), christmas()));
			case NETHERLANDS -> h.addAll(List.of(valentines(), carnival("Carnaval"), easter(),
					new Holiday("King's Day", NATIONAL, y -> {
						LocalDate d = LocalDate.of(y, 4, 27);
						return d.getDayOfWeek() == DayOfWeek.SUNDAY ? d.minusDays(1) : d;
					}), on("Sinterklaas", CHRISTMAS, 12, 5).season(4, 0), christmas()));
			case BELGIUM -> h.addAll(List.of(valentines(), carnival("Carnival"), easter(), flagDay("National Day", 7, 21), christmas()));
			case FRANCE -> h.addAll(List.of(valentines(), carnival("Mardi Gras"), easter(), flagDay("Bastille Day", 7, 14), christmas()));
			case SPAIN -> h.addAll(List.of(on("Three Kings' Day", CHRISTMAS, 1, 6).season(4, 0), valentines(), carnival("Carnaval"),
					easter(), midsummer("San Juan", 6, 23), flagDay("National Day", 10, 12), christmas()));
			case PORTUGAL -> h.addAll(List.of(valentines(), carnival("Carnaval"), easter(), flagDay("Portugal Day", 6, 10),
					midsummer("Santos Populares", 6, 13), christmas()));
			case ITALY -> h.addAll(List.of(valentines(), carnival("Carnevale"), easter(), flagDay("Republic Day", 6, 2), christmas()));
			case UNITED_KINGDOM -> h.addAll(List.of(valentines(), easter(), on("Halloween", HALLOWEEN, 10, 31),
					on("Bonfire Night", BONFIRE, 11, 5), christmas()));
			case IRELAND -> h.addAll(List.of(valentines(), on("St Patrick's Day", ST_PATRICK, 3, 17), easter(),
					on("Halloween", HALLOWEEN, 10, 31), christmas()));
			case POLAND -> h.addAll(List.of(valentines(), easter(), flagDay("Constitution Day", 5, 3),
					flagDay("Independence Day", 11, 11), christmas()));
			case CZECHIA -> h.addAll(List.of(valentines(), easter(), flagDay("Independence Day", 10, 28), christmas()));
			case SLOVAKIA -> h.addAll(List.of(valentines(), easter(), flagDay("National Uprising Day", 8, 29), christmas()));
			case HUNGARY -> h.addAll(List.of(valentines(), flagDay("National Day", 3, 15), easter(),
					flagDay("St Stephen's Day", 8, 20), flagDay("Republic Day", 10, 23), christmas()));
			case ROMANIA -> h.addAll(List.of(valentines(), on("Mărțișor", RED_AND_WHITE, 3, 1), orthodoxEaster(),
					flagDay("Great Union Day", 12, 1), christmas()));
			case BULGARIA -> h.addAll(List.of(valentines(), on("Baba Marta", RED_AND_WHITE, 3, 1), flagDay("Liberation Day", 3, 3),
					orthodoxEaster(), christmas()));
			case GREECE -> h.addAll(List.of(valentines(), rule("Apokries", CARNIVAL, y -> Calendars.orthodoxEaster(y).minusDays(49)),
					flagDay("Independence Day", 3, 25), orthodoxEaster(), flagDay("Ohi Day", 10, 28), christmas()));
			case CROATIA -> h.addAll(List.of(valentines(), carnival("Fašnik"), easter(), flagDay("Statehood Day", 5, 30), christmas()));
			case SERBIA -> h.addAll(List.of(on("Christmas", CHRISTMAS, 1, 7).season(6, 1), flagDay("Statehood Day", 2, 15),
					valentines(), orthodoxEaster()));
			case UKRAINE -> h.addAll(List.of(valentines(), orthodoxEaster(),
					rule("Vyshyvanka Day", NATIONAL, y -> Calendars.nthWeekday(y, 5, DayOfWeek.THURSDAY, 3)),
					midsummer("Ivana Kupala", 6, 24), flagDay("Constitution Day", 6, 28),
					flagDay("Independence Day", 8, 24).season(4, 0), christmas()));
			case TURKEY -> h.add(flagDay("Republic Day", 10, 29));
			case UNITED_STATES -> h.addAll(List.of(valentines(), on("St Patrick's Day", ST_PATRICK, 3, 17), easter(),
					flagDay("Independence Day", 7, 4), on("Halloween", HALLOWEEN, 10, 31),
					rule("Thanksgiving", AUTUMN, y -> Calendars.nthWeekday(y, 11, DayOfWeek.THURSDAY, 4)), christmas()));
			case CANADA -> h.addAll(List.of(valentines(), easter(), flagDay("Canada Day", 7, 1),
					rule("Thanksgiving", AUTUMN, y -> Calendars.nthWeekday(y, 10, DayOfWeek.MONDAY, 2)),
					on("Halloween", HALLOWEEN, 10, 31), christmas()));
			case MEXICO -> h.addAll(List.of(valentines(), easter(), flagDay("Independence Day", 9, 16),
					on("Día de Muertos", DAY_OF_THE_DEAD, 11, 1), christmas()));
			case BRAZIL -> h.addAll(List.of(carnival("Carnaval"), easter(), midsummer("Festa Junina", 6, 24),
					flagDay("Independence Day", 9, 7), christmas()));
			case SOUTH_KOREA -> h.addAll(List.of(lunar("Seollal", LUNAR_NEW_YEAR, 1, 1, Calendars.KOREA), valentines(),
					flagDay("Independence Movement Day", 3, 1), lunar("Buddha's Birthday", LANTERNS, 4, 8, Calendars.KOREA),
					on("Children's Day", BLOSSOM, 5, 5), flagDay("Liberation Day", 8, 15),
					lunar("Chuseok", MOON, 8, 15, Calendars.KOREA), flagDay("National Foundation Day", 10, 3),
					flagDay("Hangul Day", 10, 9), christmas()));
			case JAPAN -> {
				h.set(0, newYear().season(4, 6)); // Shōgatsu lasts the first week
				h.addAll(List.of(valentines(), on("Hinamatsuri", BLOSSOM, 3, 3), on("Children's Day", BLOSSOM, 5, 5),
						on("Tanabata", STARS, 7, 7), on("Obon", LANTERNS, 8, 15), on("Halloween", HALLOWEEN, 10, 31), christmas()));
			}
			case CHINA -> h.addAll(List.of(lunar("Spring Festival", LUNAR_NEW_YEAR, 1, 1, Calendars.CHINA),
					lunar("Lantern Festival", LANTERNS, 1, 15, Calendars.CHINA), lunar("Dragon Boat Festival", DRAGON_BOAT, 5, 5, Calendars.CHINA),
					lunar("Qixi", VALENTINE, 7, 7, Calendars.CHINA), lunar("Mid-Autumn Festival", MOON, 8, 15, Calendars.CHINA),
					flagDay("National Day", 10, 1).season(3, 6)));
			case TAIWAN -> h.addAll(List.of(lunar("Lunar New Year", LUNAR_NEW_YEAR, 1, 1, Calendars.CHINA),
					lunar("Lantern Festival", LANTERNS, 1, 15, Calendars.CHINA), lunar("Dragon Boat Festival", DRAGON_BOAT, 5, 5, Calendars.CHINA),
					lunar("Mid-Autumn Festival", MOON, 8, 15, Calendars.CHINA), flagDay("National Day", 10, 10)));
			case INDIA -> h.addAll(List.of(flagDay("Republic Day", 1, 26), rule("Holi", HOLI, Calendars::holi),
					flagDay("Independence Day", 8, 15), rule("Diwali", DIWALI, Calendars::diwali), christmas()));
			case AUSTRALIA -> h.addAll(List.of(flagDay("Australia Day", 1, 26), valentines(), easter(), christmas()));
		}
		return h;
	}
}
