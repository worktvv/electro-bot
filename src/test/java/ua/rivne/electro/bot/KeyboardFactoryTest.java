package ua.rivne.electro.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeyboardFactory Tests")
class KeyboardFactoryTest {

    @Nested
    @DisplayName("Persistent menu tests")
    class PersistentMenuTests {

        @Test
        @DisplayName("Should create persistent menu with all buttons")
        void shouldCreatePersistentMenuWithAllButtons() {
            ReplyKeyboardMarkup keyboard = KeyboardFactory.persistentMenu();

            assertNotNull(keyboard);
            List<KeyboardRow> rows = keyboard.getKeyboard();

            // Should have 3 rows
            assertEquals(3, rows.size());

            // First row: Today and Tomorrow
            assertEquals(2, rows.get(0).size());
            assertEquals(KeyboardFactory.BTN_TODAY, rows.get(0).get(0).getText());
            assertEquals(KeyboardFactory.BTN_TOMORROW, rows.get(0).get(1).getText());

            // Second row: All schedules
            assertEquals(1, rows.get(1).size());
            assertEquals(KeyboardFactory.BTN_ALL, rows.get(1).get(0).getText());

            // Third row: Notifications, My queue (center), About
            assertEquals(3, rows.get(2).size());
            assertEquals(KeyboardFactory.BTN_NOTIFICATIONS, rows.get(2).get(0).getText());
            assertEquals(KeyboardFactory.BTN_MY_QUEUE, rows.get(2).get(1).getText());
            assertEquals(KeyboardFactory.BTN_ABOUT, rows.get(2).get(2).getText());
        }

        @Test
        @DisplayName("Should have resize keyboard enabled")
        void shouldHaveResizeKeyboardEnabled() {
            ReplyKeyboardMarkup keyboard = KeyboardFactory.persistentMenu();
            assertTrue(keyboard.getResizeKeyboard());
        }

        @Test
        @DisplayName("Should be persistent")
        void shouldBePersistent() {
            ReplyKeyboardMarkup keyboard = KeyboardFactory.persistentMenu();
            assertTrue(keyboard.getIsPersistent());
        }
    }

    @Nested
    @DisplayName("Inline main menu tests (legacy)")
    class MainMenuTests {

        @Test
        @DisplayName("Should create inline main menu with basic buttons")
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
            InlineKeyboardMarkup keyboard = KeyboardFactory.mainMenu(true);

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            boolean hasFeedback = rows.stream()
                .flatMap(List::stream)
                .anyMatch(btn -> KeyboardFactory.CB_FEEDBACK.equals(btn.getCallbackData()));

            assertTrue(hasFeedback);
        }

        @Test
        @DisplayName("Should not include feedback button by default")
        void shouldNotIncludeFeedbackButtonByDefault() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.mainMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            boolean hasFeedback = rows.stream()
                .flatMap(List::stream)
                .anyMatch(btn -> KeyboardFactory.CB_FEEDBACK.equals(btn.getCallbackData()));

            assertFalse(hasFeedback);
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

            // 6 rows for queues (1.x, 2.x, 3.x, 4.x, 5.x, 6.x) - no back button (use persistent menu)
            assertEquals(6, rows.size());

            // Check first queue row
            assertEquals(2, rows.get(0).size());
            assertEquals(KeyboardFactory.CB_SET_QUEUE + "1.1", rows.get(0).get(0).getCallbackData());
            assertEquals(KeyboardFactory.CB_SET_QUEUE + "1.2", rows.get(0).get(1).getCallbackData());
        }

        @Test
        @DisplayName("Should have all queue buttons")
        void shouldHaveAllQueueButtons() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.queueSelectionMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            // Check last queue row (6.x)
            assertEquals(2, rows.get(5).size());
            assertEquals(KeyboardFactory.CB_SET_QUEUE + "6.1", rows.get(5).get(0).getCallbackData());
            assertEquals(KeyboardFactory.CB_SET_QUEUE + "6.2", rows.get(5).get(1).getCallbackData());
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

            // Only 1 row - no back button (use persistent menu)
            assertEquals(1, rows.size());
            assertEquals(KeyboardFactory.CB_NOTIFY_OFF, rows.get(0).get(0).getCallbackData());
        }

        @Test
        @DisplayName("Should show enable button when notifications disabled")
        void shouldShowEnableButtonWhenDisabled() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.notificationsMenu(false);

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            // Only 1 row - no back button (use persistent menu)
            assertEquals(1, rows.size());
            assertEquals(KeyboardFactory.CB_NOTIFY_ON, rows.get(0).get(0).getCallbackData());
        }
    }

    @Nested
    @DisplayName("Other keyboards tests")
    class OtherKeyboardsTests {

        @Test
        @DisplayName("Should create stats keyboard with close button")
        void shouldCreateStatsKeyboard() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.statsKeyboard();

            assertEquals(1, keyboard.getKeyboard().size());
            assertEquals(KeyboardFactory.CB_CLOSE_STATS, keyboard.getKeyboard().get(0).get(0).getCallbackData());
        }

        @Test
        @DisplayName("Should create feedback menu with like button only")
        void shouldCreateFeedbackMenu() {
            InlineKeyboardMarkup keyboard = KeyboardFactory.feedbackMenu();

            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();

            // Only 1 row with like button - no back button (use persistent menu)
            assertEquals(1, rows.size());
            assertEquals(KeyboardFactory.CB_LIKE, rows.get(0).get(0).getCallbackData());
        }
    }
}

