package ua.rivne.electro.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.rivne.electro.config.Config;
import ua.rivne.electro.model.DailySchedule;
import ua.rivne.electro.parser.ScheduleParser;
import ua.rivne.electro.service.DatabaseService;
import ua.rivne.electro.service.NotificationService;
import ua.rivne.electro.service.UserSettingsService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Main Telegram bot class.
 *
 * Handles incoming messages and commands from users.
 */
public class ElectroBot extends TelegramLongPollingBot {

    private final Config config;
    private final ScheduleParser parser;
    private final DatabaseService databaseService;
    private final UserSettingsService userSettings;
    private final NotificationService notificationService;

    public ElectroBot(Config config) {
        super(config.getBotToken());
        this.config = config;
        this.parser = new ScheduleParser();
        this.databaseService = new DatabaseService(config.getDatabaseUrl());
        this.userSettings = new UserSettingsService(databaseService);
        this.notificationService = new NotificationService(parser, userSettings);

        // Start cache updater (fetches data every 30 min)
        parser.startCacheUpdater();

        // Configure notification service
        notificationService.setMessageSender(this::sendNotificationMessage);
        notificationService.start();
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Handle button callbacks
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }

        // Check if there's a text message
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String userName = update.getMessage().getFrom().getFirstName();

            // Log command event
            databaseService.logEvent(chatId, "command", messageText);

            // Handle commands
            switch (messageText) {
                case "/start":
                    sendWelcomeMessage(chatId, userName);
                    break;
                case "/help":
                    sendHelpMessage(chatId);
                    break;
                case "/today":
                    sendTodaySchedule(chatId);
                    break;
                case "/tomorrow":
                    sendTomorrowSchedule(chatId);
                    break;
                case "/all":
                    sendAllSchedules(chatId);
                    break;
                case "/menu":
                    sendMainMenu(chatId);
                    break;
                case "/stats":
                    sendStats(chatId);
                    break;
                case "/debug":
                    sendDebugInfo(chatId);
                    break;
                default:
                    sendMessage(chatId, "🤔 Невідома команда. Напишіть /help для списку команд.");
            }
        }
    }

    /**
     * Handles callbacks from inline buttons.
     */
    private void handleCallback(CallbackQuery callback) {
        String data = callback.getData();
        long chatId = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();

        // Answer callback to remove "loading" indicator
        answerCallback(callback.getId());

        // Log button event
        databaseService.logEvent(chatId, "button", data);

        // Clear notifications and stats on any button press (except clear/close buttons)
        if (!data.equals(KeyboardFactory.CB_CLEAR_NOTIFICATIONS) && !data.equals(KeyboardFactory.CB_CLOSE_STATS)) {
            clearNotifications(chatId);
        }

        if (data.equals(KeyboardFactory.CB_CLOSE_STATS)) {
            handleCloseStats(chatId, messageId);
        } else if (data.equals(KeyboardFactory.CB_TODAY)) {
            editMessageWithSchedule(chatId, messageId, getTodayText(chatId));
        } else if (data.equals(KeyboardFactory.CB_TOMORROW)) {
            editMessageWithSchedule(chatId, messageId, getTomorrowText(chatId));
        } else if (data.equals(KeyboardFactory.CB_ALL)) {
            editMessageWithSchedule(chatId, messageId, getAllSchedulesText(chatId));
        } else if (data.equals(KeyboardFactory.CB_MY_QUEUE)) {
            showMyQueue(chatId, messageId);
        } else if (data.startsWith(KeyboardFactory.CB_SET_QUEUE)) {
            String queue = data.substring(KeyboardFactory.CB_SET_QUEUE.length());
            setUserQueue(chatId, messageId, queue);
        } else if (data.equals(KeyboardFactory.CB_NOTIFICATIONS)) {
            showNotificationsMenu(chatId, messageId);
        } else if (data.equals(KeyboardFactory.CB_NOTIFY_ON)) {
            toggleNotifications(chatId, messageId, true);
        } else if (data.equals(KeyboardFactory.CB_NOTIFY_OFF)) {
            toggleNotifications(chatId, messageId, false);
        } else if (data.equals(KeyboardFactory.CB_ABOUT)) {
            showAbout(chatId, messageId);
        } else if (data.equals(KeyboardFactory.CB_FEEDBACK)) {
            showFeedback(chatId, messageId);
        } else if (data.equals(KeyboardFactory.CB_LIKE)) {
            handleLike(chatId, messageId);
        } else if (data.equals(KeyboardFactory.CB_CLEAR_NOTIFICATIONS)) {
            handleClearNotifications(chatId, messageId);
        } else if (data.equals(KeyboardFactory.CB_BACK)) {
            showMainMenu(chatId, messageId);
        }
    }

    private void answerCallback(String callbackId) {
        try {
            execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends welcome message with main menu.
     */
    private void sendWelcomeMessage(long chatId, String userName) {
        int likesCount = userSettings.getLikesCount();
        String text = String.format(
            "👋 Привіт, *%s*!\n\n" +
            "Я бот для відстеження графіків відключень електроенергії " +
            "у м. Рівне та Рівненській області.\n\n" +
            "🔌 *Що я вмію:*\n" +
            "• Показувати актуальний графік відключень\n" +
            "• Надсилати сповіщення за 30 хв та 5 хв до відключення\n" +
            "• Зберігати вашу чергу для швидкого доступу\n\n" +
            "👍 Цей бот корисний *%d* %s\n\n" +
            "📧 Адміністратор: email@example.com\n\n" +
            "Оберіть дію з меню нижче 👇",
            userName, likesCount, getUserDeclension(likesCount)
        );
        boolean showFeedback = !userSettings.hasLiked(chatId);
        boolean showClearNotifications = userSettings.hasNotifications(chatId);
        sendMessageWithKeyboard(chatId, text, KeyboardFactory.mainMenu(showFeedback, showClearNotifications));
    }

    /**
     * Sends help message.
     */
    private void sendHelpMessage(long chatId) {
        String text =
            "📋 *Доступні команди:*\n\n" +
            "/start - Почати роботу з ботом\n" +
            "/menu - Показати головне меню\n" +
            "/today - Графік на сьогодні\n" +
            "/tomorrow - Графік на завтра\n" +
            "/all - Показати всі графіки\n" +
            "/help - Показати цю довідку";
        sendMarkdownMessage(chatId, text);
    }

    private void sendMainMenu(long chatId) {
        boolean showFeedback = !userSettings.hasLiked(chatId);
        boolean showClearNotifications = userSettings.hasNotifications(chatId);
        sendMessageWithKeyboard(chatId, "📋 *Головне меню*\n\nОберіть дію:", KeyboardFactory.mainMenu(showFeedback, showClearNotifications));
    }

    /**
     * Sends statistics (admin only).
     */
    private void sendStats(long chatId) {
        if (!config.isAdmin(chatId)) {
            sendMessage(chatId, "⛔ Ця команда доступна тільки адміністратору.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Статистика бота*\n\n");

        // Basic stats
        int totalUsers = databaseService.getTotalUsers();
        int usersWithQueue = databaseService.getUsersWithQueue();
        int usersWithNotifications = databaseService.getUsersWithNotificationsEnabled();
        int likesCount = userSettings.getLikesCount();

        sb.append("👥 *Користувачі:*\n");
        sb.append(String.format("• Всього: *%d*\n", totalUsers));
        sb.append(String.format("• Обрали чергу: *%d*\n", usersWithQueue));
        sb.append(String.format("• Сповіщення увімкнено: *%d*\n", usersWithNotifications));
        sb.append(String.format("• Лайків: *%d*\n\n", likesCount));

        // Activity stats
        int eventsToday = databaseService.getEventsToday();
        int activeToday = databaseService.getActiveUsersToday();
        int activeWeek = databaseService.getActiveUsersWeek();

        sb.append("📈 *Активність:*\n");
        sb.append(String.format("• Запитів сьогодні: *%d*\n", eventsToday));
        sb.append(String.format("• Активних сьогодні: *%d*\n", activeToday));
        sb.append(String.format("• Активних за тиждень: *%d*\n\n", activeWeek));

        // Queue distribution
        Map<String, Integer> queueDist = databaseService.getQueueDistribution();
        if (!queueDist.isEmpty()) {
            sb.append("🔌 *Розподіл по чергах:*\n");
            for (Map.Entry<String, Integer> entry : queueDist.entrySet()) {
                sb.append(String.format("• %s: *%d*\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\n");
        }

        // Daily active users (last 7 days)
        Map<String, Integer> dailyActive = databaseService.getDailyActiveUsers(7);
        if (!dailyActive.isEmpty()) {
            sb.append("👤 *Активних користувачів (7 днів):*\n");
            for (Map.Entry<String, Integer> entry : dailyActive.entrySet()) {
                sb.append(String.format("• %s: *%d*\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\n");
        }

        // Daily growth (last 7 days)
        Map<String, Integer> growth = databaseService.getDailyUserGrowth(7);
        if (!growth.isEmpty()) {
            sb.append("📅 *Нові користувачі (7 днів):*\n");
            for (Map.Entry<String, Integer> entry : growth.entrySet()) {
                sb.append(String.format("• %s: *+%d*\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\n");
        }

        // Popular actions
        Map<String, Integer> actions = databaseService.getPopularActions(5);
        if (!actions.isEmpty()) {
            sb.append("🔥 *Популярні дії (тиждень):*\n");
            for (Map.Entry<String, Integer> entry : actions.entrySet()) {
                sb.append(String.format("• %s: *%d*\n", entry.getKey(), entry.getValue()));
            }
        }

        // Send with close button and save message_id for auto-delete
        try {
            SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(KeyboardFactory.statsKeyboard())
                .build();
            Message sent = execute(message);
            // Save message_id so it gets deleted with notifications
            userSettings.addNotificationMessageId(chatId, sent.getMessageId());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles close stats button - deletes the stats message.
     */
    private void handleCloseStats(long chatId, int messageId) {
        try {
            execute(DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build());
        } catch (TelegramApiException e) {
            // Message may already be deleted
        }
    }

    /**
     * Sends debug info for troubleshooting notifications (admin only).
     */
    private void sendDebugInfo(long chatId) {
        if (!config.isAdmin(chatId)) {
            sendMessage(chatId, "⛔ Ця команда доступна тільки адміністратору.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔧 *Debug Info*\n\n");

        // Your settings
        String myQueue = userSettings.getUserQueue(chatId);
        boolean myNotifications = userSettings.isNotificationsEnabled(chatId);
        sb.append("*Ваші налаштування:*\n");
        sb.append(String.format("• Chat ID: `%d`\n", chatId));
        sb.append(String.format("• Черга: %s\n", myQueue != null ? myQueue : "не обрана"));
        sb.append(String.format("• Сповіщення: %s\n\n", myNotifications ? "✅ увімкнено" : "❌ вимкнено"));

        // Users with notifications
        java.util.Set<Long> usersWithNotifications = userSettings.getUsersWithNotifications();
        sb.append(String.format("*Користувачі з сповіщеннями:* %d\n", usersWithNotifications.size()));
        for (Long userId : usersWithNotifications) {
            String queue = userSettings.getUserQueue(userId);
            sb.append(String.format("• `%d` → %s\n", userId, queue != null ? queue : "без черги"));
        }
        sb.append("\n");

        // Today's schedule
        var todaySchedule = parser.getTodaySchedule();
        sb.append("*Графік на сьогодні:*\n");
        if (todaySchedule != null && todaySchedule.hasData()) {
            sb.append(String.format("• Дата: %s\n", todaySchedule.getDate()));
            if (myQueue != null) {
                var hours = todaySchedule.getHoursForQueue(myQueue);
                sb.append(String.format("• Години для %s: %s\n", myQueue,
                    hours != null && !hours.isEmpty() ? String.join(", ", hours) : "немає"));
            }
        } else {
            sb.append("• Дані відсутні!\n");
        }
        sb.append("\n");

        // Cache info
        var lastUpdate = parser.getLastCacheUpdate();
        sb.append("*Кеш:*\n");
        sb.append(String.format("• Останнє оновлення: %s\n", lastUpdate != null ? lastUpdate.toString() : "ніколи"));
        sb.append(String.format("• Є дані: %s\n", parser.hasCachedData() ? "так" : "ні"));

        sendMessageWithKeyboard(chatId, sb.toString(), KeyboardFactory.statsKeyboard());
    }

    private void sendTodaySchedule(long chatId) {
        sendMarkdownMessage(chatId, getTodayText(chatId));
    }

    private void sendTomorrowSchedule(long chatId) {
        sendMarkdownMessage(chatId, getTomorrowText(chatId));
    }

    private void sendAllSchedules(long chatId) {
        sendMarkdownMessage(chatId, getAllSchedulesText(chatId));
    }

    // === Methods for getting text ===

    private static final DateTimeFormatter UPDATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'о' HH:mm");

    /**
     * Returns formatted string with last cache update time.
     */
    private String getLastUpdateText() {
        LocalDateTime lastUpdate = parser.getLastCacheUpdate();
        if (lastUpdate != null) {
            return "\n\n_Дані оновлено " + lastUpdate.format(UPDATE_TIME_FORMAT) + "_";
        }
        return "";
    }

    private String getTodayText(long chatId) {
        if (!parser.hasCachedData()) {
            return "⏳ Дані завантажуються, спробуйте через хвилину...";
        }
        DailySchedule schedule = parser.getTodaySchedule();
        if (schedule != null) {
            String userQueue = userSettings.getUserQueue(chatId);
            return "📅 *Графік на сьогодні*\n\n" + schedule.formatAll(userQueue) + getLastUpdateText();
        }
        return "❌ Не вдалося отримати графік на сьогодні.";
    }

    private String getTomorrowText(long chatId) {
        if (!parser.hasCachedData()) {
            return "⏳ Дані завантажуються, спробуйте через хвилину...";
        }
        DailySchedule schedule = parser.getTomorrowSchedule();
        if (schedule != null) {
            String userQueue = userSettings.getUserQueue(chatId);
            return "📆 *Графік на завтра*\n\n" + schedule.formatAll(userQueue) + getLastUpdateText();
        }
        return "❌ Графік на завтра ще недоступний.";
    }

    private String getAllSchedulesText(long chatId) {
        if (!parser.hasCachedData()) {
            return "⏳ Дані завантажуються, спробуйте через хвилину...";
        }
        List<DailySchedule> schedules = parser.fetchSchedules();
        if (schedules.isEmpty()) {
            return "❌ Графіки не знайдено.";
        }
        String userQueue = userSettings.getUserQueue(chatId);
        StringBuilder sb = new StringBuilder("📊 *Всі графіки:*\n\n");
        for (DailySchedule schedule : schedules) {
            sb.append(schedule.formatAll(userQueue)).append("\n");
        }
        sb.append(getLastUpdateText());
        return sb.toString();
    }

    // === Methods for working with buttons ===

    private void editMessageWithSchedule(long chatId, int messageId, String text) {
        editMessage(chatId, messageId, text, KeyboardFactory.backToMenuButton());
    }

    private void showMainMenu(long chatId, int messageId) {
        boolean showFeedback = !userSettings.hasLiked(chatId);
        boolean showClearNotifications = userSettings.hasNotifications(chatId);
        editMessage(chatId, messageId, "📋 *Головне меню*\n\nОберіть дію:", KeyboardFactory.mainMenu(showFeedback, showClearNotifications));
    }

    private void showMyQueue(long chatId, int messageId) {
        String queue = userSettings.getUserQueue(chatId);
        String text;
        if (queue != null) {
            text = String.format("🔌 *Ваша черга:* %s\n\nОберіть нову чергу або поверніться назад:", queue);
            // Show schedule for selected queue
            if (parser.hasCachedData()) {
                DailySchedule today = parser.getTodaySchedule();
                List<String> hours = today.getHoursForQueue(queue);

                // Empty list = data pending
                if (hours == null || hours.isEmpty()) {
                    text += String.format("\n\n📅 *Сьогодні (%s):*\n⏳ Очікується", today.getDate());
                } else {
                    text += String.format("\n\n📅 *Сьогодні (%s):*\n⏰ %s", today.getDate(), String.join(", ", hours));
                }
            }
        } else {
            text = "🔌 *Оберіть вашу чергу:*\n\nЦе дозволить бачити графік тільки для вашої черги та отримувати сповіщення.";
        }
        editMessage(chatId, messageId, text, KeyboardFactory.queueSelectionMenu());
    }

    private void setUserQueue(long chatId, int messageId, String queue) {
        userSettings.setUserQueue(chatId, queue);
        String text = String.format("✅ Чергу *%s* збережено!\n\nТепер ви можете увімкнути сповіщення.", queue);
        editMessage(chatId, messageId, text, KeyboardFactory.backToMenuButton());
    }

    private void showNotificationsMenu(long chatId, int messageId) {
        boolean enabled = userSettings.isNotificationsEnabled(chatId);
        String queue = userSettings.getUserQueue(chatId);

        String text;
        if (queue == null) {
            text = "⚠️ *Спочатку оберіть чергу!*\n\nДля отримання сповіщень потрібно обрати вашу чергу.";
            editMessage(chatId, messageId, text, KeyboardFactory.backToMenuButton());
            return;
        }

        String status = enabled ? "🔔 Увімкнено" : "🔕 Вимкнено";
        text = String.format(
            "🔔 *Сповіщення*\n\n" +
            "Статус: %s\n" +
            "Черга: *%s*\n\n" +
            "Бот надішле повідомлення за 30 хвилин та 5 хвилин до можливого відключення.",
            status, queue
        );
        editMessage(chatId, messageId, text, KeyboardFactory.notificationsMenu(enabled));
    }

    private void toggleNotifications(long chatId, int messageId, boolean enable) {
        userSettings.setNotificationsEnabled(chatId, enable);
        String text = enable
            ? "✅ *Сповіщення увімкнено!*\n\nВи отримаєте повідомлення за 30 хв та 5 хв до відключення."
            : "🔕 *Сповіщення вимкнено.*";
        editMessage(chatId, messageId, text, KeyboardFactory.backToMenuButton());
    }

    private void showAbout(long chatId, int messageId) {
        int likesCount = userSettings.getLikesCount();
        String text = String.format(
            "ℹ️ *Про бота*\n\n" +
            "Я бот для відстеження графіків відключень електроенергії " +
            "у м. Рівне та Рівненській області.\n\n" +
            "🔌 *Що я вмію:*\n" +
            "• Показувати актуальний графік відключень\n" +
            "• Надсилати сповіщення за 30 хв та 5 хв до відключення\n" +
            "• Зберігати вашу чергу для швидкого доступу\n\n" +
            "👍 Цей бот корисний *%d* %s\n\n" +
            "📧 Адміністратор: email@example.com",
            likesCount, getUserDeclension(likesCount)
        );
        editMessage(chatId, messageId, text, KeyboardFactory.backToMenuButton());
    }

    private void showFeedback(long chatId, int messageId) {
        int likesCount = userSettings.getLikesCount();
        String text = String.format(
            "💬 *Цей бот корисний?*\n\n" +
            "👍 Цей бот сподобався *%d* %s.\n\n" +
            "Якщо бот вам корисний, натисніть кнопку нижче!",
            likesCount, getUserDeclension(likesCount)
        );
        editMessage(chatId, messageId, text, KeyboardFactory.feedbackMenu());
    }

    private void handleLike(long chatId, int messageId) {
        userSettings.addLike(chatId);
        int likesCount = userSettings.getLikesCount();
        String text = String.format(
            "❤️ *Дякуємо за вашу підтримку!*\n\n" +
            "👍 Цей бот сподобався *%d* %s.",
            likesCount, getUserDeclension(likesCount)
        );
        editMessage(chatId, messageId, text, KeyboardFactory.backToMenuButton());
    }

    private void handleClearNotifications(long chatId, int messageId) {
        clearNotifications(chatId);
        showMainMenu(chatId, messageId);
    }

    /**
     * Clears all notification messages for user.
     */
    private void clearNotifications(long chatId) {
        java.util.Set<Integer> messageIds = userSettings.getAndClearNotificationMessageIds(chatId);
        for (Integer msgId : messageIds) {
            try {
                execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(msgId)
                    .build());
            } catch (TelegramApiException e) {
                // Message may already be deleted, ignore
            }
        }
    }

    /**
     * Sends notification message and returns its ID.
     */
    private Integer sendNotificationMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        try {
            Message sent = execute(message);
            return sent.getMessageId();
        } catch (TelegramApiException e) {
            e.printStackTrace();
            return null;
        }
    }

    // === Methods for sending messages ===

    private void sendMarkdownMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId);
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setParseMode("Markdown");
        edit.setReplyMarkup(keyboard);
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns correct Ukrainian declension for "користувач" based on number.
     * 1 -> користувачу
     * 2-4 -> користувачам
     * 5-20 -> користувачам
     * 21 -> користувачу
     * 22-24 -> користувачам
     * etc.
     */
    private String getUserDeclension(int count) {
        int lastTwo = count % 100;
        int lastOne = count % 10;

        if (lastTwo >= 11 && lastTwo <= 19) {
            return "користувачам";
        }

        if (lastOne == 1) {
            return "користувачу";
        }

        return "користувачам";
    }
}

