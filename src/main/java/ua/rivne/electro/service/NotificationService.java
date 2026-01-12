package ua.rivne.electro.service;

import ua.rivne.electro.model.DailySchedule;
import ua.rivne.electro.parser.ScheduleParser;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Service for sending power outage notifications.
 */
public class NotificationService {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    private static final int CHECK_INTERVAL_MINUTES = 1; // Check every minute for accuracy

    // Notification intervals (minutes before outage)
    private static final int[] NOTIFY_BEFORE_MINUTES = {45, 5};

    private final ScheduleParser parser;
    private final UserSettingsService userSettings;
    private final ScheduledExecutorService scheduler;

    // Track sent notifications: "chatId:hourRange:minutesBefore:date"
    private final Set<String> sentNotifications = ConcurrentHashMap.newKeySet();

    // Callback for sending messages (chatId, message, isLast) -> returns messageId
    private NotificationMessageSender messageSender;

    /**
     * Functional interface for sending notification messages.
     */
    @FunctionalInterface
    public interface NotificationMessageSender {
        Integer send(long chatId, String message, boolean isLast);
    }

    public NotificationService(ScheduleParser parser, UserSettingsService userSettings) {
        this.parser = parser;
        this.userSettings = userSettings;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Sets callback for sending messages.
     * The function should return the message ID of the sent message.
     */
    public void setMessageSender(NotificationMessageSender sender) {
        this.messageSender = sender;
    }

    /**
     * Starts the notification service.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
            this::checkAndNotify,
            1,
            CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        System.out.println("🔔 Notification service started");
    }

    /**
     * Stops the notification service.
     */
    public void stop() {
        scheduler.shutdown();
    }

    /**
     * Checks schedules and sends notifications.
     */
    private void checkAndNotify() {
        if (messageSender == null) {
            return;
        }

        DailySchedule todaySchedule = parser.getTodaySchedule();
        if (todaySchedule == null || !todaySchedule.hasData()) {
            return;
        }

        // Use Kyiv timezone
        LocalTime now = LocalTime.now(KYIV_ZONE);
        LocalDate today = LocalDate.now(KYIV_ZONE);

        // Clean old notifications (from previous days)
        cleanOldNotifications(today);

        Set<Long> usersToNotify = userSettings.getUsersWithNotifications();

        for (Long chatId : usersToNotify) {
            String queue = userSettings.getUserQueue(chatId);
            if (queue == null) {
                continue;
            }

            List<String> hours = todaySchedule.getHoursForQueue(queue);
            if (hours == null || hours.isEmpty()) {
                continue; // Data pending
            }

            // Collect all notifications for this user first
            java.util.List<NotificationData> pendingNotifications = new java.util.ArrayList<>();
            for (String hourRange : hours) {
                collectNotification(chatId, queue, hourRange, now, today, pendingNotifications);
            }

            // Send all notifications, marking the last one
            for (int i = 0; i < pendingNotifications.size(); i++) {
                NotificationData data = pendingNotifications.get(i);
                boolean isLast = (i == pendingNotifications.size() - 1);
                sendNotification(data, isLast);
            }
        }
    }

    /**
     * Data class for pending notification.
     */
    private static class NotificationData {
        final long chatId;
        final String message;
        final String notificationKey;

        NotificationData(long chatId, String message, String notificationKey) {
            this.chatId = chatId;
            this.message = message;
            this.notificationKey = notificationKey;
        }
    }

    /**
     * Collects notification for a specific time range if needed.
     */
    private void collectNotification(long chatId, String queue, String hourRange,
                                     LocalTime now, LocalDate today,
                                     java.util.List<NotificationData> pendingNotifications) {
        LocalTime startTime = parseStartTime(hourRange);
        if (startTime == null || !startTime.isAfter(now)) {
            return; // Time already passed or parsing error
        }

        long minutesUntil = java.time.Duration.between(now, startTime).toMinutes();

        for (int notifyMinutes : NOTIFY_BEFORE_MINUTES) {
            // Check if time is within notification interval (with ±2 min tolerance)
            if (minutesUntil <= notifyMinutes && minutesUntil > notifyMinutes - 3) {
                String notificationKey = buildNotificationKey(chatId, hourRange, notifyMinutes, today);

                // Check if already sent this notification
                if (!sentNotifications.contains(notificationKey)) {
                    System.out.println("🔔 Preparing notification for " + chatId + " (" + notifyMinutes + " min before " + hourRange + ")");
                    String emoji = notifyMinutes <= 5 ? "🚨" : "⚠️";
                    String urgency = notifyMinutes <= 5 ? "ТЕРМІНОВО! " : "";

                    String message = String.format(
                        "%s *%sУвага!*\n\n" +
                        "Через *%d хвилин* можливе відключення електроенергії!\n\n" +
                        "🔌 Черга: *%s*\n" +
                        "⏰ Час: *%s*\n\n" +
                        "Підготуйтесь заздалегідь!",
                        emoji, urgency, notifyMinutes, queue, hourRange
                    );
                    pendingNotifications.add(new NotificationData(chatId, message, notificationKey));
                }
            }
        }
    }

    /**
     * Sends a notification and marks it as sent.
     */
    private void sendNotification(NotificationData data, boolean isLast) {
        // Mark as sent before sending to avoid duplicates
        sentNotifications.add(data.notificationKey);

        Integer messageId = messageSender.send(data.chatId, data.message, isLast);
        if (messageId != null) {
            userSettings.addNotificationMessageId(data.chatId, messageId);
        }
    }

    /**
     * Parses start time from time range.
     * Handles formats: "13:00 - 17:00", "13:00-17:00", "8:00 - 12:00"
     */
    private LocalTime parseStartTime(String hourRange) {
        try {
            // Split by dash (with or without spaces)
            String startTimeStr = hourRange.split("\\s*-\\s*")[0].trim();

            // Handle both "HH:mm" and "H:mm" formats
            if (startTimeStr.matches("\\d{1,2}:\\d{2}")) {
                String[] parts = startTimeStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                return LocalTime.of(hour, minute);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds unique notification key.
     */
    private String buildNotificationKey(long chatId, String hourRange, int minutesBefore, LocalDate date) {
        return chatId + ":" + hourRange + ":" + minutesBefore + ":" + date;
    }

    /**
     * Cleans notifications from previous days.
     */
    private void cleanOldNotifications(LocalDate today) {
        String todayStr = today.toString();
        sentNotifications.removeIf(key -> !key.endsWith(todayStr));
    }
}

