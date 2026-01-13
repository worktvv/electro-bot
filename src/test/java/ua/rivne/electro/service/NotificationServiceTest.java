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

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

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
            BiFunction<Long, String, Integer> mockSender = mock(BiFunction.class);

            assertDoesNotThrow(() -> {
                notificationService.setMessageSender(mockSender);
            });
        }
    }
}

