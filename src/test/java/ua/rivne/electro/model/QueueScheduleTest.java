package ua.rivne.electro.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueueSchedule Tests")
class QueueScheduleTest {

    @Nested
    @DisplayName("Constructor and getters tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create QueueSchedule with valid data")
        void shouldCreateWithValidData() {
            List<String> hours = Arrays.asList("08:00 - 12:00", "16:00 - 20:00");
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", hours);

            assertEquals("1.1", schedule.getQueueNumber());
            assertEquals("16.01.2026", schedule.getDate());
            assertEquals(2, schedule.getHours().size());
            assertEquals("08:00 - 12:00", schedule.getHours().get(0));
            assertEquals("16:00 - 20:00", schedule.getHours().get(1));
        }

        @Test
        @DisplayName("Should create QueueSchedule with empty hours")
        void shouldCreateWithEmptyHours() {
            QueueSchedule schedule = new QueueSchedule("2.1", "17.01.2026", Collections.emptyList());

            assertEquals("2.1", schedule.getQueueNumber());
            assertEquals("17.01.2026", schedule.getDate());
            assertTrue(schedule.getHours().isEmpty());
        }

        @Test
        @DisplayName("Should create QueueSchedule with null hours")
        void shouldCreateWithNullHours() {
            QueueSchedule schedule = new QueueSchedule("3.2", "18.01.2026", null);

            assertEquals("3.2", schedule.getQueueNumber());
            assertNull(schedule.getHours());
        }
    }

    @Nested
    @DisplayName("getHoursFormatted() tests")
    class HoursFormattedTests {

        @Test
        @DisplayName("Should format single hour range")
        void shouldFormatSingleHourRange() {
            List<String> hours = Collections.singletonList("08:00 - 12:00");
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", hours);

            assertEquals("08:00 - 12:00", schedule.getHoursFormatted());
        }

        @Test
        @DisplayName("Should format multiple hour ranges with comma")
        void shouldFormatMultipleHourRanges() {
            List<String> hours = Arrays.asList("08:00 - 12:00", "16:00 - 20:00", "22:00 - 23:59");
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", hours);

            assertEquals("08:00 - 12:00, 16:00 - 20:00, 22:00 - 23:59", schedule.getHoursFormatted());
        }

        @Test
        @DisplayName("Should return no outages message for empty hours")
        void shouldReturnNoOutagesForEmptyHours() {
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", Collections.emptyList());

            assertEquals("Відключень не заплановано ✅", schedule.getHoursFormatted());
        }

        @Test
        @DisplayName("Should return no outages message for null hours")
        void shouldReturnNoOutagesForNullHours() {
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", null);

            assertEquals("Відключень не заплановано ✅", schedule.getHoursFormatted());
        }
    }

    @Nested
    @DisplayName("toString() tests")
    class ToStringTests {

        @Test
        @DisplayName("Should format toString with hours")
        void shouldFormatToStringWithHours() {
            List<String> hours = Arrays.asList("08:00 - 12:00", "16:00 - 20:00");
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", hours);

            String result = schedule.toString();
            assertTrue(result.contains("Черга 1.1"));
            assertTrue(result.contains("16.01.2026"));
            assertTrue(result.contains("08:00 - 12:00"));
            assertTrue(result.contains("16:00 - 20:00"));
        }

        @Test
        @DisplayName("Should format toString without hours")
        void shouldFormatToStringWithoutHours() {
            QueueSchedule schedule = new QueueSchedule("2.2", "17.01.2026", Collections.emptyList());

            String result = schedule.toString();
            assertTrue(result.contains("Черга 2.2"));
            assertTrue(result.contains("17.01.2026"));
            assertTrue(result.contains("Відключень не заплановано"));
        }
    }

    @Nested
    @DisplayName("Edge cases tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle all queue numbers")
        void shouldHandleAllQueueNumbers() {
            String[] queues = {"1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"};
            
            for (String queue : queues) {
                QueueSchedule schedule = new QueueSchedule(queue, "16.01.2026", Collections.emptyList());
                assertEquals(queue, schedule.getQueueNumber());
            }
        }

        @Test
        @DisplayName("Should handle midnight hours")
        void shouldHandleMidnightHours() {
            List<String> hours = Arrays.asList("00:00 - 04:00", "20:00 - 23:59");
            QueueSchedule schedule = new QueueSchedule("1.1", "16.01.2026", hours);

            assertEquals("00:00 - 04:00, 20:00 - 23:59", schedule.getHoursFormatted());
        }
    }
}

