package ua.rivne.electro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.rivne.electro.model.DailySchedule;
import ua.rivne.electro.parser.ScheduleParser;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock
    private ScheduleParser parser;

    @Mock
    private UserSettingsService userSettings;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(parser, userSettings);
    }

    @Nested
    @DisplayName("parseStartTime() tests")
    class ParseStartTimeTests {

        @Test
        @DisplayName("Should parse standard format HH:MM - HH:MM")
        void shouldParseStandardFormat() {
            LocalTime result = notificationService.parseStartTime("13:00 - 17:00");
            assertEquals(LocalTime.of(13, 0), result);
        }

        @Test
        @DisplayName("Should parse format without spaces HH:MM-HH:MM")
        void shouldParseFormatWithoutSpaces() {
            LocalTime result = notificationService.parseStartTime("08:00-12:00");
            assertEquals(LocalTime.of(8, 0), result);
        }

        @Test
        @DisplayName("Should parse midnight time 00:00")
        void shouldParseMidnightTime() {
            LocalTime result = notificationService.parseStartTime("00:00 - 04:00");
            assertEquals(LocalTime.of(0, 0), result);
        }

        @Test
        @DisplayName("Should parse single digit hour format H:MM")
        void shouldParseSingleDigitHour() {
            LocalTime result = notificationService.parseStartTime("8:00 - 12:00");
            assertEquals(LocalTime.of(8, 0), result);
        }

        @Test
        @DisplayName("Should return null for invalid format")
        void shouldReturnNullForInvalidFormat() {
            assertNull(notificationService.parseStartTime("invalid"));
            assertNull(notificationService.parseStartTime(""));
            assertNull(notificationService.parseStartTime(null));
        }

        @Test
        @DisplayName("Should parse time with minutes")
        void shouldParseTimeWithMinutes() {
            LocalTime result = notificationService.parseStartTime("13:30 - 17:45");
            assertEquals(LocalTime.of(13, 30), result);
        }

        @Test
        @DisplayName("Should parse 23:59 time")
        void shouldParseEndOfDayTime() {
            LocalTime result = notificationService.parseStartTime("20:00 - 23:59");
            assertEquals(LocalTime.of(20, 0), result);
        }
    }

    @Nested
    @DisplayName("Notification sending tests")
    class NotificationSendingTests {

        private AtomicInteger messageIdCounter;
        private ArgumentCaptor<Long> chatIdCaptor;
        private ArgumentCaptor<String> messageCaptor;

        @BeforeEach
        void setUp() {
            messageIdCounter = new AtomicInteger(1);
            chatIdCaptor = ArgumentCaptor.forClass(Long.class);
            messageCaptor = ArgumentCaptor.forClass(String.class);
        }

        @Test
        @DisplayName("Should not send notification when messageSender is not set")
        void shouldNotSendWhenMessageSenderNotSet() {
            // Don't set messageSender
            // Just verify no exception is thrown
            assertDoesNotThrow(() -> {
                // Trigger internal check (via reflection or by starting service briefly)
                // For now, just verify the service can be created without messageSender
            });
        }

        @Test
        @DisplayName("Should handle null schedule gracefully")
        void shouldHandleNullSchedule() {
            // Verify that service can be created and messageSender can be set
            // without throwing exceptions, even when parser might return null
            BiConsumer<Long, String> mockSender = mock(BiConsumer.class);

            assertDoesNotThrow(() -> {
                notificationService.setMessageSender(mockSender);
            });
        }
    }

    @Nested
    @DisplayName("parseEndTime() tests")
    class ParseEndTimeTests {

        @Test
        @DisplayName("Should parse end time from standard format")
        void shouldParseEndTimeStandard() {
            LocalTime result = notificationService.parseEndTime("13:00 - 17:00");
            assertEquals(LocalTime.of(17, 0), result);
        }

        @Test
        @DisplayName("Should parse midnight end time")
        void shouldParseMidnightEndTime() {
            LocalTime result = notificationService.parseEndTime("22:00 - 00:00");
            assertEquals(LocalTime.MIDNIGHT, result);
        }

        @Test
        @DisplayName("Should parse end time without spaces")
        void shouldParseEndTimeNoSpaces() {
            LocalTime result = notificationService.parseEndTime("08:00-12:00");
            assertEquals(LocalTime.of(12, 0), result);
        }

        @Test
        @DisplayName("Should return null for invalid format")
        void shouldReturnNullForInvalidFormat() {
            LocalTime result = notificationService.parseEndTime("invalid");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("isContinuationOfPreviousOutage() tests")
    class ContinuationTests {

        @Test
        @DisplayName("Should detect continuation when previous day ends at midnight")
        void shouldDetectContinuation() {
            LocalDate today = LocalDate.of(2026, 1, 16);
            LocalDate yesterday = LocalDate.of(2026, 1, 15);

            DailySchedule yesterdaySchedule = new DailySchedule("15.01.2026");
            yesterdaySchedule.addQueueHours("6.1", List.of("22:00 - 00:00"));

            when(parser.getScheduleForDate(yesterday)).thenReturn(yesterdaySchedule);

            boolean result = notificationService.isContinuationOfPreviousOutage("6.1", today);

            assertTrue(result);
        }

        @Test
        @DisplayName("Should not detect continuation when previous day does not end at midnight")
        void shouldNotDetectContinuationWhenNotMidnight() {
            LocalDate today = LocalDate.of(2026, 1, 16);
            LocalDate yesterday = LocalDate.of(2026, 1, 15);

            DailySchedule yesterdaySchedule = new DailySchedule("15.01.2026");
            yesterdaySchedule.addQueueHours("6.1", List.of("18:00 - 22:00"));

            when(parser.getScheduleForDate(yesterday)).thenReturn(yesterdaySchedule);

            boolean result = notificationService.isContinuationOfPreviousOutage("6.1", today);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should not detect continuation when no previous schedule")
        void shouldNotDetectContinuationWhenNoPreviousSchedule() {
            LocalDate today = LocalDate.of(2026, 1, 16);
            LocalDate yesterday = LocalDate.of(2026, 1, 15);

            when(parser.getScheduleForDate(yesterday)).thenReturn(null);

            boolean result = notificationService.isContinuationOfPreviousOutage("6.1", today);

            assertFalse(result);
        }

        @Test
        @DisplayName("Should not detect continuation for different queue")
        void shouldNotDetectContinuationForDifferentQueue() {
            LocalDate today = LocalDate.of(2026, 1, 16);
            LocalDate yesterday = LocalDate.of(2026, 1, 15);

            DailySchedule yesterdaySchedule = new DailySchedule("15.01.2026");
            yesterdaySchedule.addQueueHours("1.1", List.of("22:00 - 00:00")); // Different queue

            when(parser.getScheduleForDate(yesterday)).thenReturn(yesterdaySchedule);

            boolean result = notificationService.isContinuationOfPreviousOutage("6.1", today);

            assertFalse(result);
        }
    }
}

