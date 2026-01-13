package ua.rivne.electro.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSettingsService Tests")
class UserSettingsServiceTest {

    @Mock
    private DatabaseService databaseService;

    private UserSettingsService userSettingsService;

    private static final long TEST_CHAT_ID = 123456789L;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(databaseService);
    }

    @Nested
    @DisplayName("Queue management tests")
    class QueueManagementTests {

        @Test
        @DisplayName("Should set user queue")
        void shouldSetUserQueue() {
            userSettingsService.setUserQueue(TEST_CHAT_ID, "1.1");
            verify(databaseService).setUserQueue(TEST_CHAT_ID, "1.1");
        }

        @Test
        @DisplayName("Should get user queue")
        void shouldGetUserQueue() {
            when(databaseService.getUserQueue(TEST_CHAT_ID)).thenReturn("2.1");

            String queue = userSettingsService.getUserQueue(TEST_CHAT_ID);

            assertEquals("2.1", queue);
            verify(databaseService).getUserQueue(TEST_CHAT_ID);
        }

        @Test
        @DisplayName("Should check if user has queue")
        void shouldCheckIfUserHasQueue() {
            when(databaseService.hasQueue(TEST_CHAT_ID)).thenReturn(true);

            assertTrue(userSettingsService.hasQueue(TEST_CHAT_ID));
            verify(databaseService).hasQueue(TEST_CHAT_ID);
        }

        @Test
        @DisplayName("Should return false when user has no queue")
        void shouldReturnFalseWhenNoQueue() {
            when(databaseService.hasQueue(TEST_CHAT_ID)).thenReturn(false);

            assertFalse(userSettingsService.hasQueue(TEST_CHAT_ID));
        }
    }

    @Nested
    @DisplayName("Notifications tests")
    class NotificationsTests {

        @Test
        @DisplayName("Should enable notifications")
        void shouldEnableNotifications() {
            userSettingsService.setNotificationsEnabled(TEST_CHAT_ID, true);
            verify(databaseService).setNotificationsEnabled(TEST_CHAT_ID, true);
        }

        @Test
        @DisplayName("Should disable notifications")
        void shouldDisableNotifications() {
            userSettingsService.setNotificationsEnabled(TEST_CHAT_ID, false);
            verify(databaseService).setNotificationsEnabled(TEST_CHAT_ID, false);
        }

        @Test
        @DisplayName("Should check if notifications enabled")
        void shouldCheckIfNotificationsEnabled() {
            when(databaseService.isNotificationsEnabled(TEST_CHAT_ID)).thenReturn(true);

            assertTrue(userSettingsService.isNotificationsEnabled(TEST_CHAT_ID));
        }

        @Test
        @DisplayName("Should get users with notifications")
        void shouldGetUsersWithNotifications() {
            Set<Long> users = new HashSet<>();
            users.add(111L);
            users.add(222L);
            when(databaseService.getUsersWithNotifications()).thenReturn(users);

            Set<Long> result = userSettingsService.getUsersWithNotifications();

            assertEquals(2, result.size());
            assertTrue(result.contains(111L));
            assertTrue(result.contains(222L));
        }
    }

    @Nested
    @DisplayName("Likes tests")
    class LikesTests {

        @Test
        @DisplayName("Should add like")
        void shouldAddLike() {
            userSettingsService.addLike(TEST_CHAT_ID);
            verify(databaseService).addLike(TEST_CHAT_ID);
        }

        @Test
        @DisplayName("Should check if user has liked")
        void shouldCheckIfUserHasLiked() {
            when(databaseService.hasLiked(TEST_CHAT_ID)).thenReturn(true);

            assertTrue(userSettingsService.hasLiked(TEST_CHAT_ID));
        }

        @Test
        @DisplayName("Should get likes count")
        void shouldGetLikesCount() {
            when(databaseService.getLikesCount()).thenReturn(42);

            assertEquals(42, userSettingsService.getLikesCount());
        }
    }
}

