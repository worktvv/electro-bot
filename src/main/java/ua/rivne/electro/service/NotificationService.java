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
    private static final int[] NOTIFY_BEFORE_MINUTES = {30, 5};

    private final ScheduleParser parser;
    private final UserSettingsService userSettings;
    private final ScheduledExecutorService scheduler;

    // Track sent notifications: "chatId:hourRange:minutesBefore:date"
    private final Set<String> sentNotifications = ConcurrentHashMap.newKeySet();

    // Callback for sending messages (chatId, message) -> returns messageId
    private java.util.function.BiFunction<Long, String, Integer> messageSender;

    public NotificationService(ScheduleParser parser, UserSettingsService userSettings) {
        this.parser = parser;
        this.userSettings = userSettings;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Sets callback for sending messages.
     * The function should return the message ID of the sent message.
     */
    public void setMessageSender(java.util.function.BiFunction<Long, String, Integer> sender) {
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

            for (String hourRange : hours) {
                checkAndSendNotification(chatId, queue, hourRange, now, today);
            }
        }
    }

    /**
     * Checks and sends notification for a specific time range.
     */
    private void checkAndSendNotification(long chatId, String queue, String hourRange,
                                          LocalTime now, LocalDate today) {
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
                if (sentNotifications.add(notificationKey)) {
                    System.out.println("🔔 Sending notification to " + chatId + " (" + notifyMinutes + " min before " + hourRange + ")");
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
                    Integer messageId = messageSender.apply(chatId, message);
                    if (messageId != null) {
                        userSettings.addNotificationMessageId(chatId, messageId);
                    }
                }
            }
        }
    }

    /**
     * Parses start time from time range.
     */
    private LocalTime parseStartTime(String hourRange) {
        try {
            String startTimeStr = hourRange.split("-")[0].trim();
            return LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
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

