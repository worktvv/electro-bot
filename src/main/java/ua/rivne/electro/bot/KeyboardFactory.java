package ua.rivne.electro.bot;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating inline keyboards.
 */
public class KeyboardFactory {

    // Callback data constants
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
    public static final String CB_CLEAR_NOTIFICATIONS = "clear_notifications";
    public static final String CB_CLOSE_STATS = "close_stats";

    /**
     * Main menu keyboard (without optional buttons).
     */
    public static InlineKeyboardMarkup mainMenu() {
        return mainMenu(false, false);
    }

    /**
     * Main menu keyboard.
     * @param showFeedback whether to show feedback button
     * @param showClearNotifications whether to show clear notifications button
     */
    public static InlineKeyboardMarkup mainMenu(boolean showFeedback, boolean showClearNotifications) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // First row
        keyboard.add(List.of(
            button("📅 Сьогодні", CB_TODAY),
            button("📆 Завтра", CB_TOMORROW)
        ));

        // Second row
        keyboard.add(List.of(
            button("📊 Всі графіки", CB_ALL)
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

        // Sixth row - clear notifications (only if user has pending notifications)
        if (showClearNotifications) {
            keyboard.add(List.of(
                button("🧹 Очистити сповіщення", CB_CLEAR_NOTIFICATIONS)
            ));
        }

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Feedback menu keyboard.
     */
    public static InlineKeyboardMarkup feedbackMenu() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(button("👍 Так, бот корисний!", CB_LIKE)));
        keyboard.add(List.of(button("⬅️ Назад", CB_BACK)));

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

        // Back button
        keyboard.add(List.of(button("⬅️ Назад", CB_BACK)));

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * Notifications menu keyboard.
     */
    public static InlineKeyboardMarkup notificationsMenu(boolean isEnabled) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (isEnabled) {
            keyboard.add(List.of(button("🔕 Вимкнути сповіщення", CB_NOTIFY_OFF)));
        } else {
            keyboard.add(List.of(button("🔔 Увімкнути сповіщення", CB_NOTIFY_ON)));
        }

        keyboard.add(List.of(button("⬅️ Назад", CB_BACK)));

        return InlineKeyboardMarkup.builder().keyboard(keyboard).build();
    }

    /**
     * "Back to menu" button.
     */
    public static InlineKeyboardMarkup backToMenuButton() {
        return InlineKeyboardMarkup.builder()
            .keyboard(List.of(List.of(button("⬅️ Меню", CB_BACK))))
            .build();
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
     * Creates a button.
     */
    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
            .text(text)
            .callbackData(callbackData)
            .build();
    }
}
