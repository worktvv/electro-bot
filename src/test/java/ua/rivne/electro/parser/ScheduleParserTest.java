package ua.rivne.electro.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
}

