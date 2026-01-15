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
     * Creates the main menu inline keyboard without optional buttons.
     *
     * @return inline keyboard with standard menu options
     * @see #mainMenu(boolean)
     */
    public static InlineKeyboardMarkup mainMenu() {
        return mainMenu(false);
    }

    /**
     * Creates the main menu inline keyboard.
     *
     * <p>The menu includes:
     * <ul>
     *   <li>"📅 Сьогодні" / "📆 Завтра" - View today's/tomorrow's schedule</li>
     *   <li>"📊 Всі графіки" - View all available schedules</li>
     *   <li>"🔌 Моя черга" / "🔔 Сповіщення" - Queue and notification settings</li>
     *   <li>"ℹ️ Про бота" - About the bot</li>
     *   <li>"💬 Цей бот корисний?" - Feedback (optional)</li>
     * </ul>
     *
     * @param showFeedback if true, includes the feedback button for users who haven't liked yet
     * @return configured InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup mainMenu(boolean showFeedback) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // First row: Today, All schedules, Tomorrow
        keyboard.add(List.of(
            button("📅 Сьогодні", CB_TODAY),
            button("📊 Всі графіки", CB_ALL),
            button("📆 Завтра", CB_TOMORROW)
        ));

        // Second row: Share bot
        keyboard.add(List.of(
            urlButton("📤 Поділитися ботом", SHARE_BOT_URL)
        ));

        // Third row
        keyboard.add(List.of(
            button("🔌 Моя черга", CB_MY_QUEUE),
            button("🔔 Сповіщення", CB_NOTIFICATIONS)
        ));

        // Fourth row
        keyboard.add(List.of(
            button("ℹ️ Про бота", CB_ABOUT)
        ));

        // Fifth row - feedback (only if user hasn't liked yet)
        if (showFeedback) {
            keyboard.add(List.of(
                button("💬 Цей бот корисний?", CB_FEEDBACK)
            ));
        }

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Feedback menu keyboard (just like button, no back - use persistent menu).
     */
    public static InlineKeyboardMarkup feedbackMenu() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(button("👍 Так, бот корисний!", CB_LIKE)));

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

