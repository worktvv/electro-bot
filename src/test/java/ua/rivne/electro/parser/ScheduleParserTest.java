package ua.rivne.electro.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ua.rivne.electro.model.DailySchedule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScheduleParser Tests")
class ScheduleParserTest {

    private ScheduleParser parser;

    @BeforeEach
    void setUp() {
        parser = new ScheduleParser();
    }

    @Nested
    @DisplayName("parseHours() tests")
    class ParseHoursTests {

        @Test
        @DisplayName("Should parse standard format with spaces")
        void shouldParseStandardFormat() {
            List<String> hours = parser.parseHours("13:00 - 17:00");
            assertEquals(1, hours.size());
            assertEquals("13:00 - 17:00", hours.get(0));
        }

        @Test
        @DisplayName("Should parse format without spaces")
        void shouldParseFormatWithoutSpaces() {
            List<String> hours = parser.parseHours("08:00-12:00");
            assertEquals(1, hours.size());
            assertEquals("08:00 - 12:00", hours.get(0));
        }

        @Test
        @DisplayName("Should parse comma separated hours")
        void shouldParseCommaSeparated() {
            List<String> hours = parser.parseHours("08:00 - 12:00, 20:00 - 23:59");
            assertEquals(2, hours.size());
            assertEquals("08:00 - 12:00", hours.get(0));
            assertEquals("20:00 - 23:59", hours.get(1));
        }

        @Test
        @DisplayName("Should parse newline separated hours")
        void shouldParseNewlineSeparated() {
            List<String> hours = parser.parseHours("08:00 - 12:00\n20:00 - 23:59");
            assertEquals(2, hours.size());
            assertEquals("08:00 - 12:00", hours.get(0));
            assertEquals("20:00 - 23:59", hours.get(1));
        }

        @Test
        @DisplayName("Should parse hours without separator (concatenated)")
        void shouldParseConcatenatedHours() {
            List<String> hours = parser.parseHours("08:00 - 12:0020:00 - 23:59");
            assertEquals(2, hours.size());
            assertEquals("08:00 - 12:00", hours.get(0));
            assertEquals("20:00 - 23:59", hours.get(1));
        }

        @Test
        @DisplayName("Should return empty list for null input")
        void shouldReturnEmptyForNull() {
            List<String> hours = parser.parseHours(null);
            assertTrue(hours.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for empty string")
        void shouldReturnEmptyForEmptyString() {
            List<String> hours = parser.parseHours("");
            assertTrue(hours.isEmpty());
        }

        @Test
        @DisplayName("Should parse midnight hours")
        void shouldParseMidnightHours() {
            List<String> hours = parser.parseHours("00:00 - 04:00");
            assertEquals(1, hours.size());
            assertEquals("00:00 - 04:00", hours.get(0));
        }

        @Test
        @DisplayName("Should handle multiple time ranges")
        void shouldHandleMultipleRanges() {
            List<String> hours = parser.parseHours("06:00 - 10:00\n14:00 - 18:00\n22:00 - 23:59");
            assertEquals(3, hours.size());
        }
    }

    @Nested
    @DisplayName("normalizeTimeRange() tests")
    class NormalizeTimeRangeTests {

        @Test
        @DisplayName("Should normalize format without spaces")
        void shouldNormalizeWithoutSpaces() {
            String result = parser.normalizeTimeRange("13:00-17:00");
            assertEquals("13:00 - 17:00", result);
        }

        @Test
        @DisplayName("Should keep already normalized format")
        void shouldKeepNormalizedFormat() {
            String result = parser.normalizeTimeRange("13:00 - 17:00");
            assertEquals("13:00 - 17:00", result);
        }

        @Test
        @DisplayName("Should normalize format with space before dash only")
        void shouldNormalizeSpaceBeforeDash() {
            String result = parser.normalizeTimeRange("13:00 -17:00");
            assertEquals("13:00 - 17:00", result);
        }

        @Test
        @DisplayName("Should normalize format with space after dash only")
        void shouldNormalizeSpaceAfterDash() {
            String result = parser.normalizeTimeRange("13:00- 17:00");
            assertEquals("13:00 - 17:00", result);
        }

        @Test
        @DisplayName("Should return null for invalid format")
        void shouldReturnNullForInvalidFormat() {
            assertNull(parser.normalizeTimeRange("invalid"));
            assertNull(parser.normalizeTimeRange("13:00"));
            assertNull(parser.normalizeTimeRange(""));
        }

        @Test
        @DisplayName("Should handle single digit hours")
        void shouldHandleSingleDigitHours() {
            String result = parser.normalizeTimeRange("8:00-12:00");
            assertEquals("8:00 - 12:00", result);
        }
    }

    @Nested
    @DisplayName("Cache state tests")
    class CacheStateTests {

        @Test
        @DisplayName("Should have no cached data initially")
        void shouldHaveNoCachedDataInitially() {
            assertFalse(parser.hasCachedData());
        }

        @Test
        @DisplayName("Should return empty list when no cached data")
        void shouldReturnEmptyListWhenNoCachedData() {
            List<DailySchedule> schedules = parser.fetchSchedules();
            assertTrue(schedules.isEmpty());
        }

        @Test
        @DisplayName("Should return null for last cache update initially")
        void shouldReturnNullForLastCacheUpdateInitially() {
            assertNull(parser.getLastCacheUpdate());
        }

        @Test
        @DisplayName("Should not be source unavailable initially")
        void shouldNotBeSourceUnavailableInitially() {
            assertFalse(parser.isSourceUnavailable());
        }

        @Test
        @DisplayName("Should return empty schedule for today when no cached data")
        void shouldReturnEmptyScheduleForTodayWhenNoData() {
            // Parser has no cached data, so getTodaySchedule returns empty schedule
            ScheduleParser freshParser = new ScheduleParser();
            DailySchedule schedule = freshParser.getTodaySchedule();
            assertNotNull(schedule);
            assertFalse(schedule.hasData());
        }

        @Test
        @DisplayName("Should return empty schedule for tomorrow when no cached data")
        void shouldReturnEmptyScheduleForTomorrowWhenNoData() {
            // Parser has no cached data, so getTomorrowSchedule returns empty schedule
            ScheduleParser freshParser = new ScheduleParser();
            DailySchedule schedule = freshParser.getTomorrowSchedule();
            assertNotNull(schedule);
            assertFalse(schedule.hasData());
        }
    }

    @Nested
    @DisplayName("getScheduleForDate() tests")
    class GetScheduleForDateTests {

        @Test
        @DisplayName("Should return empty schedule for far future date")
        void shouldReturnEmptyScheduleForFarFutureDate() {
            // Use a date far in the future that won't be in any cache
            LocalDate farFuture = LocalDate.of(2099, 12, 31);
            DailySchedule schedule = parser.getScheduleForDate(farFuture);
            assertNotNull(schedule);
            assertEquals("31.12.2099", schedule.getDate());
            assertFalse(schedule.hasData());
        }

        @Test
        @DisplayName("Should return schedule with correct date format")
        void shouldReturnScheduleWithCorrectDateFormat() {
            LocalDate date = LocalDate.of(2026, 1, 5);
            DailySchedule schedule = parser.getScheduleForDate(date);
            assertEquals("05.01.2026", schedule.getDate());
        }
    }

    @Nested
    @DisplayName("Edge cases for parseHours()")
    class ParseHoursEdgeCases {

        @Test
        @DisplayName("Should handle whitespace only input")
        void shouldHandleWhitespaceOnlyInput() {
            List<String> hours = parser.parseHours("   ");
            assertTrue(hours.isEmpty());
        }

        @Test
        @DisplayName("Should handle mixed separators")
        void shouldHandleMixedSeparators() {
            List<String> hours = parser.parseHours("08:00 - 12:00, 14:00 - 18:00\n20:00 - 23:59");
            assertEquals(3, hours.size());
        }

        @Test
        @DisplayName("Should handle hours with extra whitespace")
        void shouldHandleHoursWithExtraWhitespace() {
            List<String> hours = parser.parseHours("  08:00 - 12:00  ");
            assertEquals(1, hours.size());
            assertEquals("08:00 - 12:00", hours.get(0));
        }

        @Test
        @DisplayName("Should handle full day range")
        void shouldHandleFullDayRange() {
            List<String> hours = parser.parseHours("00:00 - 23:59");
            assertEquals(1, hours.size());
            assertEquals("00:00 - 23:59", hours.get(0));
        }

        @Test
        @DisplayName("Should handle four time ranges")
        void shouldHandleFourTimeRanges() {
            List<String> hours = parser.parseHours("00:00 - 04:00\n08:00 - 12:00\n16:00 - 20:00\n22:00 - 23:59");
            assertEquals(4, hours.size());
        }
    }
}

