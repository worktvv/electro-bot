package ua.rivne.electro.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeyboardFactory Tests")
class KeyboardFactoryTest {

    @Nested
    @DisplayName("Main menu tests")
    class MainMenuTests {

        @Test
        @DisplayName("Should create main menu with basic buttons")
        void shouldCreateMainMenuWithBasicButtons() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.mainMenu();

            assertNotNull(keyboard);
            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            // Should have at least 4 rows (Today/Tomorrow, All, MyQueue/Notifications, About)
            assertTrue(rows.size() >= 4);

            // First row: Today and Tomorrow
            assertEquals(2, rows.get(0).size());
            assertEquals(KeyboardFactory.CB_TODAY, rows.get(0).get(0).getCallbackData());
            assertEquals(KeyboardFactory.CB_TOMORROW, rows.get(0).get(1).getCallbackData());
        }

        @Test
        @DisplayName("Should include feedback button when showFeedback is true")
        void shouldIncludeFeedbackButton() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.mainMenu(true, false);

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            boolean hasFeedback = rows.stream()
                .flatMap(List::stream)
                .anyMatch(btn -> KeyboardFactory.CB_FEEDBACK.equals(btn.getCallbackData()));

            assertTrue(hasFeedback);
        }

        @Test
        @DisplayName("Should include clear notifications button when showClearNotifications is true")
        void shouldIncludeClearNotificationsButton() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.mainMenu(false, true);

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            boolean hasClearNotifications = rows.stream()
                .flatMap(List::stream)
                .anyMatch(btn -> KeyboardFactory.CB_CLEAR_NOTIFICATIONS.equals(btn.getCallbackData()));

            assertTrue(hasClearNotifications);
        }

        @Test
        @DisplayName("Should not include optional buttons by default")
        void shouldNotIncludeOptionalButtonsByDefault() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.mainMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            boolean hasFeedback = rows.stream()
                .flatMap(List::stream)
                .anyMatch(btn -> KeyboardFactory.CB_FEEDBACK.equals(btn.getCallbackData()));

            boolean hasClearNotifications = rows.stream()
                .flatMap(List::stream)
                .anyMatch(btn -> KeyboardFactory.CB_CLEAR_NOTIFICATIONS.equals(btn.getCallbackData()));

            assertFalse(hasFeedback);
            assertFalse(hasClearNotifications);
        }
    }

    @Nested
    @DisplayName("Queue selection menu tests")
    class QueueSelectionMenuTests {

        @Test
        @DisplayName("Should create queue selection with all 12 queues")
        void shouldCreateQueueSelectionWithAllQueues() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.queueSelectionMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            // 6 rows for queues (1.x, 2.x, 3.x, 4.x, 5.x, 6.x) + 1 back button
            assertEquals(7, rows.size());

            // Check first queue row
            assertEquals(2, rows.get(0).size());
            assertEquals(KeyboardFactory.CB_SET_QUEUE + "1.1", rows.get(0).get(0).getCallbackData());
            assertEquals(KeyboardFactory.CB_SET_QUEUE + "1.2", rows.get(0).get(1).getCallbackData());
        }

        @Test
        @DisplayName("Should have back button")
        void shouldHaveBackButton() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.queueSelectionMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
            List<InlineKeyboardButton> lastRow = rows.get(rows.size() - 1);

            assertEquals(1, lastRow.size());
            assertEquals(KeyboardFactory.CB_BACK, lastRow.get(0).getCallbackData());
        }
    }

    @Nested
    @DisplayName("Notifications menu tests")
    class NotificationsMenuTests {

        @Test
        @DisplayName("Should show disable button when notifications enabled")
        void shouldShowDisableButtonWhenEnabled() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.notificationsMenu(true);

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            assertEquals(KeyboardFactory.CB_NOTIFY_OFF, rows.get(0).get(0).getCallbackData());
        }

        @Test
        @DisplayName("Should show enable button when notifications disabled")
        void shouldShowEnableButtonWhenDisabled() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.notificationsMenu(false);

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            assertEquals(KeyboardFactory.CB_NOTIFY_ON, rows.get(0).get(0).getCallbackData());
        }
    }

    @Nested
    @DisplayName("Other keyboards tests")
    class OtherKeyboardsTests {

        @Test
        @DisplayName("Should create back to menu button")
        void shouldCreateBackToMenuButton() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.backToMenuButton();

            assertEquals(1, keyboard.getKeyboard().size());
            assertEquals(KeyboardFactory.CB_BACK, keyboard.getKeyboard().get(0).get(0).getCallbackData());
        }

        @Test
        @DisplayName("Should create stats keyboard with close button")
        void shouldCreateStatsKeyboard() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.statsKeyboard();

            assertEquals(1, keyboard.getKeyboard().size());
            assertEquals(KeyboardFactory.CB_CLOSE_STATS, keyboard.getKeyboard().get(0).get(0).getCallbackData());
        }

        @Test
        @DisplayName("Should create feedback menu")
        void shouldCreateFeedbackMenu() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.feedbackMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            assertEquals(2, rows.size());
            assertEquals(KeyboardFactory.CB_LIKE, rows.get(0).get(0).getCallbackData());
            assertEquals(KeyboardFactory.CB_BACK, rows.get(1).get(0).getCallbackData());
        }
    }
}

