package ua.rivne.electro.service;

import ua.rivne.electro.model.DailySchedule;
import ua.rivne.electro.parser.ScheduleParser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for sending power outage notifications.
 */
public class NotificationService {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    private static final int CHECK_INTERVAL_MINUTES = 1; // Check every minute for accuracy

    // Notification intervals (minutes before outage)
    private static final int[] NOTIFY_BEFORE_MINUTES = {58, 5};

    private final ScheduleParser parser;
    private final UserSettingsService userSettings;
    private final ScheduledExecutorService scheduler;

    // Track sent notifications: "chatId:hourRange:minutesBefore:date"
    private final Set<String> sentNotifications = ConcurrentHashMap.newKeySet();

    // Callback for sending messages (chatId, message)
    private java.util.function.BiConsumer<Long, String> messageSender;

    public NotificationService(ScheduleParser parser, UserSettingsService userSettings) {
        this.parser = parser;
        this.userSettings = userSettings;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Sets callback for sending messages.
     */
    public void setMessageSender(java.util.function.BiConsumer<Long, String> sender) {
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

        // Use Kyiv timezone
        LocalDateTime now = LocalDateTime.now(KYIV_ZONE);
        LocalDate today = now.toLocalDate();

        // Clean old notifications (from previous days)
        cleanOldNotifications(today);

        Set<Long> usersToNotify = userSettings.getUsersWithNotifications();

        for (Long chatId : usersToNotify) {
            String queue = userSettings.getUserQueue(chatId);
            if (queue == null) {
                continue;
            }

            // Check today's schedule
            DailySchedule todaySchedule = parser.getTodaySchedule();
            if (todaySchedule != null && todaySchedule.hasData()) {
                List<String> hours = todaySchedule.getHoursForQueue(queue);
                if (hours != null && !hours.isEmpty()) {
                    for (String hourRange : hours) {
                        checkAndSendNotification(chatId, queue, hourRange, now, today);
                    }
                }
            }

            // Check tomorrow's schedule for midnight outages (00:00 - 01:00)
            // This handles notifications at 23:30 and 23:55 for 00:00 outages
            DailySchedule tomorrowSchedule = parser.getTomorrowSchedule();
            if (tomorrowSchedule != null && tomorrowSchedule.hasData()) {
                List<String> tomorrowHours = tomorrowSchedule.getHoursForQueue(queue);
                if (tomorrowHours != null && !tomorrowHours.isEmpty()) {
                    LocalDate tomorrow = today.plusDays(1);
                    for (String hourRange : tomorrowHours) {
                        // Only check early morning outages (before 02:00)
                        LocalTime startTime = parseStartTime(hourRange);
                        if (startTime != null && startTime.getHour() < 2) {
                            checkAndSendNotification(chatId, queue, hourRange, now, tomorrow);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks and sends notification for a specific time range.
     *
     * @param chatId user's chat ID
     * @param queue user's queue
     * @param hourRange time range string (e.g., "00:00 - 04:00")
     * @param now current date and time
     * @param outageDate the date when the outage is scheduled (can be today or tomorrow)
     */
    private void checkAndSendNotification(long chatId, String queue, String hourRange,
                                          LocalDateTime now, LocalDate outageDate) {
        LocalTime startTime = parseStartTime(hourRange);
        if (startTime == null) {
            return; // Parsing error
        }

        // Create full datetime for the outage
        LocalDateTime outageDateTime = LocalDateTime.of(outageDate, startTime);

        // Check if outage time is in the future
        if (!outageDateTime.isAfter(now)) {
            return; // Time already passed
        }

        long minutesUntil = Duration.between(now, outageDateTime).toMinutes();

        for (int notifyMinutes : NOTIFY_BEFORE_MINUTES) {
            // Check if time is within notification interval (with ±2 min tolerance)
            if (minutesUntil <= notifyMinutes && minutesUntil > notifyMinutes - 3) {
                String notificationKey = buildNotificationKey(chatId, hourRange, notifyMinutes, outageDate);

                // Check if already sent this notification
                if (sentNotifications.add(notificationKey)) {
                    System.out.println("🔔 Sending notification to " + chatId + " (" + notifyMinutes + " min before " + hourRange + " on " + outageDate + ")");
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
                    messageSender.accept(chatId, message);
                }
            }
        }
    }

    /**
     * Parses start time from time range.
     * Handles formats: "13:00 - 17:00", "13:00-17:00", "8:00 - 12:00"
     * Package-private for testing.
     */
    LocalTime parseStartTime(String hourRange) {
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
     * Keeps today's and tomorrow's notifications.
     */
    private void cleanOldNotifications(LocalDate today) {
        String todayStr = today.toString();
        String tomorrowStr = today.plusDays(1).toString();
        sentNotifications.removeIf(key -> !key.endsWith(todayStr) && !key.endsWith(tomorrowStr));
    }
}

