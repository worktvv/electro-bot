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
 * Service for sending power outage notifications to users.
 *
 * <p>This service monitors scheduled outages and sends timely notifications
 * to users who have enabled notifications and selected their power queue.
 *
 * <p>Notification timing:
 * <ul>
 *   <li>⚠️ 30 minutes before scheduled outage - warning notification</li>
 *   <li>🚨 5 minutes before scheduled outage - urgent notification</li>
 * </ul>
 *
 * <p>The service runs a background scheduler that checks every minute
 * for upcoming outages and sends notifications as needed.
 *
 * <p>Usage:
 * <pre>{@code
 * NotificationService service = new NotificationService(parser, userSettings);
 * service.setMessageSender((chatId, message) -> bot.sendMessage(chatId, message));
 * service.start();
 * }</pre>
 *
 * @author Electro Bot Team
 * @version 1.0
 */
public class NotificationService {

    /** Timezone for all time calculations */
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    /** How often to check for upcoming outages (in minutes) */
    private static final int CHECK_INTERVAL_MINUTES = 1;

    /** Minutes before outage to send notifications */
    private static final int[] NOTIFY_BEFORE_MINUTES = {30, 5};

    private final ScheduleParser parser;
    private final UserSettingsService userSettings;
    private final ScheduledExecutorService scheduler;

    /** Tracks sent notifications to prevent duplicates. Key format: "chatId:hourRange:minutesBefore:date" */
    private final Set<String> sentNotifications = ConcurrentHashMap.newKeySet();

    private java.util.function.BiConsumer<Long, String> messageSender;

    /**
     * Creates a new NotificationService.
     *
     * @param parser the schedule parser for fetching outage data
     * @param userSettings the user settings service for user preferences
     */
    public NotificationService(ScheduleParser parser, UserSettingsService userSettings) {
        this.parser = parser;
        this.userSettings = userSettings;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Sets the callback function for sending messages to users.
     *
     * @param sender a BiConsumer that accepts (chatId, message) and sends the message
     */
    public void setMessageSender(java.util.function.BiConsumer<Long, String> sender) {
        this.messageSender = sender;
    }

    /**
     * Starts the notification service.
     *
     * <p>Begins checking for upcoming outages every minute and
     * sending notifications to eligible users.
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

        // Skip midnight outages that are continuations of previous day's outage
        if (startTime.equals(LocalTime.MIDNIGHT) && isContinuationOfPreviousOutage(queue, outageDate)) {
            return; // This is a continuation, not a new outage
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
     * Parses end time from time range.
     * Handles formats: "13:00 - 17:00", "13:00-17:00", "22:00 - 00:00"
     * Package-private for testing.
     */
    LocalTime parseEndTime(String hourRange) {
        try {
            String[] parts = hourRange.split("\\s*-\\s*");
            if (parts.length < 2) {
                return null;
            }
            String endTimeStr = parts[1].trim();

            if (endTimeStr.matches("\\d{1,2}:\\d{2}")) {
                String[] timeParts = endTimeStr.split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                return LocalTime.of(hour, minute);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if a midnight outage (starting at 00:00) is a continuation of previous day's outage.
     *
     * <p>Example: If yesterday has "22:00 - 00:00" and today has "00:00 - 02:00",
     * the today's outage is a continuation, not a new outage.
     *
     * @param queue the power queue to check
     * @param outageDate the date of the midnight outage
     * @return true if this is a continuation of previous day's outage
     */
    boolean isContinuationOfPreviousOutage(String queue, LocalDate outageDate) {
        LocalDate previousDay = outageDate.minusDays(1);
        DailySchedule previousSchedule = parser.getScheduleForDate(previousDay);

        if (previousSchedule == null || !previousSchedule.hasData()) {
            return false;
        }

        List<String> previousHours = previousSchedule.getHoursForQueue(queue);
        if (previousHours == null || previousHours.isEmpty()) {
            return false;
        }

        // Check if any period from previous day ends at 00:00 (midnight)
        for (String hourRange : previousHours) {
            LocalTime endTime = parseEndTime(hourRange);
            if (endTime != null && endTime.equals(LocalTime.MIDNIGHT)) {
                return true;
            }
        }

        return false;
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

