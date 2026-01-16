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

        @Test
        @DisplayName("Should format all queues without user queue")
        void shouldFormatAllQueuesWithoutUserQueue() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            schedule.addQueueHours("2.1", Arrays.asList("14:00 - 18:00"));

            String formatted = schedule.formatAll(null);

            assertTrue(formatted.contains("1.1"));
            assertTrue(formatted.contains("2.1"));
            assertTrue(formatted.contains("08:00 - 12:00"));
            assertTrue(formatted.contains("14:00 - 18:00"));
        }
    }

    @Nested
    @DisplayName("getAllQueues() tests")
    class GetAllQueuesTests {

        @Test
        @DisplayName("Should return empty map initially")
        void shouldReturnEmptyMapInitially() {
            assertTrue(schedule.getAllQueues().isEmpty());
        }

        @Test
        @DisplayName("Should return all added queues")
        void shouldReturnAllAddedQueues() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            schedule.addQueueHours("1.2", Arrays.asList("12:00 - 16:00"));
            schedule.addQueueHours("2.1", Arrays.asList("16:00 - 20:00"));

            assertEquals(3, schedule.getAllQueues().size());
            assertTrue(schedule.getAllQueues().containsKey("1.1"));
            assertTrue(schedule.getAllQueues().containsKey("1.2"));
            assertTrue(schedule.getAllQueues().containsKey("2.1"));
        }
    }

    @Nested
    @DisplayName("Edge cases tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle all 12 queues")
        void shouldHandleAll12Queues() {
            String[] queues = {"1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"};

            for (String queue : queues) {
                schedule.addQueueHours(queue, Arrays.asList("08:00 - 12:00"));
            }

            assertEquals(12, schedule.getAllQueues().size());
            for (String queue : queues) {
                assertNotNull(schedule.getHoursForQueue(queue));
            }
        }

        @Test
        @DisplayName("Should handle multiple time ranges per queue")
        void shouldHandleMultipleTimeRangesPerQueue() {
            List<String> hours = Arrays.asList("00:00 - 04:00", "08:00 - 12:00", "16:00 - 20:00", "22:00 - 23:59");
            schedule.addQueueHours("1.1", hours);

            List<String> retrieved = schedule.getHoursForQueue("1.1");
            assertEquals(4, retrieved.size());
        }

        @Test
        @DisplayName("Should overwrite queue hours when added twice")
        void shouldOverwriteQueueHoursWhenAddedTwice() {
            schedule.addQueueHours("1.1", Arrays.asList("08:00 - 12:00"));
            schedule.addQueueHours("1.1", Arrays.asList("14:00 - 18:00"));

            List<String> hours = schedule.getHoursForQueue("1.1");
            assertEquals(1, hours.size());
            assertEquals("14:00 - 18:00", hours.get(0));
        }

        @Test
        @DisplayName("Should handle different date formats")
        void shouldHandleDifferentDateFormats() {
            DailySchedule schedule1 = new DailySchedule("01.01.2026");
            DailySchedule schedule2 = new DailySchedule("31.12.2025");

            assertEquals("01.01.2026", schedule1.getDate());
            assertEquals("31.12.2025", schedule2.getDate());
        }
    }
}

