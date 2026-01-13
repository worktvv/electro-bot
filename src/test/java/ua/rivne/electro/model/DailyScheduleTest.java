package ua.rivne.electro.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DailySchedule Tests")
class DailyScheduleTest {

    private DailySchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new DailySchedule("13.01.2025");
    }

    @Nested
    @DisplayName("Basic functionality tests")
    class BasicTests {

        @Test
        @DisplayName("Should return correct date")
        void shouldReturnCorrectDate() {
            assertEquals("13.01.2025", schedule.getDate());
        }

        @Test
        @DisplayName("Should return false for hasData when empty")
        void shouldReturnFalseForHasDataWhenEmpty() {
            assertFalse(schedule.hasData());
        }

        @Test
        @DisplayName("Should return true for hasData when has queues")
        void shouldReturnTrueForHasDataWhenHasQueues() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            assertTrue(schedule.hasData());
        }
    }

    @Nested
    @DisplayName("Queue hours tests")
    class QueueHoursTests {

        @Test
        @DisplayName("Should add and retrieve queue hours")
        void shouldAddAndRetrieveQueueHours() {
            List<String> hours = Arrays.asList("08:00 - 12:00", "16:00 - 20:00");
            schedule.addQueueHours("1.1", hours);

            List<String> retrieved = schedule.getHoursForQueue("1.1");
            assertEquals(2, retrieved.size());
            assertEquals("08:00 - 12:00", retrieved.get(0));
            assertEquals("16:00 - 20:00", retrieved.get(1));
        }

        @Test
        @DisplayName("Should return null for non-existent queue")
        void shouldReturnNullForNonExistentQueue() {
            assertNull(schedule.getHoursForQueue("9.9"));
        }

        @Test
        @DisplayName("Should handle empty hours list")
        void shouldHandleEmptyHoursList() {
            schedule.addQueueHours("1.1", Collections.emptyList());
            List<String> hours = schedule.getHoursForQueue("1.1");
            assertNotNull(hours);
            assertTrue(hours.isEmpty());
        }

        @Test
        @DisplayName("Should handle midnight hours")
        void shouldHandleMidnightHours() {
            schedule.addQueueHours("2.1", Arrays.asList("00:00 - 04:00"));
            List<String> hours = schedule.getHoursForQueue("2.1");
            assertEquals(1, hours.size());
            assertEquals("00:00 - 04:00", hours.get(0));
        }
    }

    @Nested
    @DisplayName("Format tests")
    class FormatTests {

        @Test
        @DisplayName("Should format for specific queue")
        void shouldFormatForSpecificQueue() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            String formatted = schedule.formatForQueue("1.1");

            assertTrue(formatted.contains("13.01.2025"));
            assertTrue(formatted.contains("1.1"));
            assertTrue(formatted.contains("08:00 - 12:00"));
        }

        @Test
        @DisplayName("Should show pending for empty queue")
        void shouldShowPendingForEmptyQueue() {
            schedule.addQueueHours("1.1", Collections.emptyList());
            String formatted = schedule.formatForQueue("1.1");

            assertTrue(formatted.contains("Очікується"));
        }

        @Test
        @DisplayName("Should format all queues")
        void shouldFormatAllQueues() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            schedule.addQueueHours("1.2", Arrays.asList("12:00 - 16:00"));

            String formatted = schedule.formatAll();

            assertTrue(formatted.contains("13.01.2025"));
            assertTrue(formatted.contains("1.1"));
            assertTrue(formatted.contains("1.2"));
        }

        @Test
        @DisplayName("Should highlight user queue in formatAll")
        void shouldHighlightUserQueue() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            schedule.addQueueHours("1.2", Arrays.asList("12:00 - 16:00"));

            String formatted = schedule.formatAll("1.1");

            // User queue should be in italic (Markdown)
            assertTrue(formatted.contains("_08:00 - 12:00_"));
        }

        @Test
        @DisplayName("Should show pending message when no data")
        void shouldShowPendingWhenNoData() {
            String formatted = schedule.formatAll();
            assertTrue(formatted.contains("очікується"));
        }
    }
}

