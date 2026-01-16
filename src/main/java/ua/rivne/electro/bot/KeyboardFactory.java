package ua.rivne.electro.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating Telegram keyboards.
 *
 * <p>This class provides static methods to create various keyboard types:
 * <ul>
 *   <li>{@link #persistentMenu()} - Bottom reply keyboard always visible to users</li>
 *   <li>{@link #mainMenu()} - Inline keyboard for main menu actions</li>
 *   <li>{@link #queueSelectionMenu()} - Inline keyboard for queue selection</li>
 *   <li>{@link #notificationsMenu(boolean)} - Inline keyboard for notification settings</li>
 * </ul>
 *
 * <p>All button labels are in Ukrainian as per user interface requirements.
 *
 * @author Electro Bot Team
 * @version 1.0
 */
public class KeyboardFactory {

    // Callback data constants for inline keyboards
    public static final String CB_TODAY = "today";
    public static final String CB_TOMORROW = "tomorrow";
    public static final String CB_ALL = "all";
    public static final String CB_MY_QUEUE = "my_queue";
    public static final String CB_SET_QUEUE = "set_queue:";
    public static final String CB_NOTIFICATIONS = "notifications";
    public static final String CB_NOTIFY_ON = "notify_on";
    public static final String CB_NOTIFY_OFF = "notify_off";
    public static final String CB_ABOUT = "about";
    public static final String CB_BACK = "back";
    public static final String CB_FEEDBACK = "feedback";
    public static final String CB_LIKE = "like";
    public static final String CB_CLOSE_STATS = "close_stats";
    public static final String CB_CONTACT_DEV = "contact_dev";

    // Share bot URL
    public static final String SHARE_BOT_URL = "https://t.me/share/url?url=https://t.me/electrorivne_bot";

    // Button text constants for reply keyboard (used for matching incoming messages)
    public static final String BTN_TODAY = "📅 Сьогодні";
    public static final String BTN_TOMORROW = "📆 Завтра";
    public static final String BTN_ALL = "📊 Всі графіки";
    public static final String BTN_MY_QUEUE = "🔌 Моя черга";
    public static final String BTN_NOTIFICATIONS = "🔔";
    public static final String BTN_ABOUT = "ℹ️";

    /**
     * Creates a persistent reply keyboard (bottom menu).
     *
     * <p>This keyboard is always visible at the bottom of the chat screen
     * and provides quick access to main bot functions:
     * <ul>
     *   <li>Row 1: "📅 Сьогодні" (Today), "📆 Завтра" (Tomorrow)</li>
     *   <li>Row 2: "📊 Всі графіки" (All schedules)</li>
     *   <li>Row 3: "🔔" (Notifications), "🔌 Моя черга" (My queue), "ℹ️" (About)</li>
     * </ul>
     *
     * @return configured ReplyKeyboardMarkup with persistent visibility
     */
    public static ReplyKeyboardMarkup persistentMenu() {
        List<KeyboardRow> keyboard = new ArrayList<>();

        // First row: Today and Tomorrow
        KeyboardRow row1 = new KeyboardRow();
        row1.add(BTN_TODAY);
        row1.add(BTN_TOMORROW);
        keyboard.add(row1);

        // Second row: All schedules
        KeyboardRow row2 = new KeyboardRow();
        row2.add(BTN_ALL);
        keyboard.add(row2);

        // Third row: Notifications, My queue, About (My queue in the middle)
        KeyboardRow row3 = new KeyboardRow();
        row3.add(BTN_NOTIFICATIONS);
        row3.add(BTN_MY_QUEUE);
        row3.add(BTN_ABOUT);
        keyboard.add(row3);

        return ReplyKeyboardMarkup.builder()
            .keyboard(keyboard)
            .resizeKeyboard(true)  // Resize to fit buttons
            .isPersistent(true)    // Keep visible
            .build();
    }

    /**
     * Creates inline keyboard with share button for schedule messages.
     *
     * @return inline keyboard with share bot button
     */
    public static InlineKeyboardMarkup shareKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(
            urlButton("📤 Поділитися ботом", SHARE_BOT_URL)
        ));

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Feedback menu keyboard with like button (for users who haven't liked yet).
     */
    public static InlineKeyboardMarkup feedbackMenu() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(button("👍 Так, бот корисний!", CB_LIKE)));
        keyboard.add(List.of(button("📩 Написати розробнику", CB_CONTACT_DEV)));

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * About menu keyboard with contact button only (for users who already liked).
     */
    public static InlineKeyboardMarkup aboutMenu() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(button("📩 Написати розробнику", CB_CONTACT_DEV)));

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Queue selection menu keyboard.
     */
    public static InlineKeyboardMarkup queueSelectionMenu() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Queues 1.x
        keyboard.add(List.of(
            button("1.1", CB_SET_QUEUE + "1.1"),
            button("1.2", CB_SET_QUEUE + "1.2")
        ));

        // Queues 2.x
        keyboard.add(List.of(
            button("2.1", CB_SET_QUEUE + "2.1"),
            button("2.2", CB_SET_QUEUE + "2.2")
        ));

        // Queues 3.x
        keyboard.add(List.of(
            button("3.1", CB_SET_QUEUE + "3.1"),
            button("3.2", CB_SET_QUEUE + "3.2")
        ));

        // Queues 4.x
        keyboard.add(List.of(
            button("4.1", CB_SET_QUEUE + "4.1"),
            button("4.2", CB_SET_QUEUE + "4.2")
        ));

        // Queues 5.x
        keyboard.add(List.of(
            button("5.1", CB_SET_QUEUE + "5.1"),
            button("5.2", CB_SET_QUEUE + "5.2")
        ));

        // Queues 6.x
        keyboard.add(List.of(
            button("6.1", CB_SET_QUEUE + "6.1"),
            button("6.2", CB_SET_QUEUE + "6.2")
        ));

        // No back button - use persistent menu instead

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Notifications menu keyboard (no back button - use persistent menu).
     */
    public static InlineKeyboardMarkup notificationsMenu(boolean isEnabled) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (isEnabled) {
            keyboard.add(List.of(button("🔕 Вимкнути сповіщення", CB_NOTIFY_OFF)));
        } else {
            keyboard.add(List.of(button("🔔 Увімкнути сповіщення", CB_NOTIFY_ON)));
        }

        // No back button - use persistent menu instead

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Keyboard for stats message with close button.
     */
    public static InlineKeyboardMarkup statsKeyboard() {
        return InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                List.of(button("❌ Закрити", CB_CLOSE_STATS))
            ))
            .build();
    }

    /**
     * Creates a callback button.
     */
    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
            .text(text)
            .callbackData(callbackData)
            .build();
    }

    /**
     * Creates a URL button that opens a link when clicked.
     */
    private static InlineKeyboardButton urlButton(String text, String url) {
        return InlineKeyboardButton.builder()
            .text(text)
            .url(url)
            .build();
    }
}

