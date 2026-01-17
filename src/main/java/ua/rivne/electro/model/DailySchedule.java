package ua.rivne.electro.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model representing a power outage schedule for a single day.
 *
 * <p>Stores outage time ranges for all power queues (1.1 through 6.2).
 * Each queue can have multiple outage periods during the day.
 *
 * <p>Example usage:
 * <pre>{@code
 * DailySchedule schedule = new DailySchedule("14.01.2026");
 * schedule.addQueueHours("1.1", List.of("08:00 - 12:00", "16:00 - 20:00"));
 * List<String> hours = schedule.getHoursForQueue("1.1");
 * }</pre>
 *
 * @author Electro Bot Team
 * @version 1.0
 */
public class DailySchedule {

    /** All possible power queue identifiers */
    private static final String[] ALL_QUEUES = {
        "1.1", "1.2", "2.1", "2.2", "3.1", "3.2",
        "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"
    };

    /** Formatter for parsing date from "dd.MM.yyyy" */
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Formatter for displaying date as "17 січня 2026 р." */
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy 'р.'", new Locale("uk", "UA"));

    private final String date;
    private final Map<String, List<String>> queueHours;

    /**
     * Creates a new daily schedule for the specified date.
     *
     * @param date the date in format "dd.MM.yyyy"
     */
    public DailySchedule(String date) {
        this.date = date;
        this.queueHours = new HashMap<>();
    }

    /**
     * Adds outage hours for a specific queue.
     *
     * @param queueNumber the queue identifier (e.g., "1.1", "2.2")
     * @param hours list of time ranges (e.g., ["08:00 - 12:00", "16:00 - 20:00"])
     */
    public void addQueueHours(String queueNumber, List<String> hours) {
        queueHours.put(queueNumber, hours);
    }

    /**
     * Returns the date of this schedule.
     *
     * @return date string in format "dd.MM.yyyy"
     */
    public String getDate() {
        return date;
    }

    /**
     * Returns formatted date for display (e.g., "17 січня 2026 р.").
     *
     * @return formatted date string
     */
    public String getFormattedDate() {
        try {
            LocalDate localDate = LocalDate.parse(date, INPUT_FORMAT);
            return localDate.format(DISPLAY_FORMAT);
        } catch (Exception e) {
            return date; // fallback to original format
        }
    }

    /**
     * Returns outage hours for a specific queue.
     *
     * @param queueNumber the queue identifier (e.g., "1.1")
     * @return list of time ranges, empty list if no outages, or null if data is pending
     */
    public List<String> getHoursForQueue(String queueNumber) {
        if (!queueHours.containsKey(queueNumber)) {
            return null; // Data pending
        }
        return queueHours.get(queueNumber);
    }

    public Map<String, List<String>> getAllQueues() {
        return queueHours;
    }

    /**
     * Checks if there is data for this day.
     */
    public boolean hasData() {
        return !queueHours.isEmpty();
    }

    /**
     * Formats schedule for a specific queue.
     */
    public String formatForQueue(String queueNumber) {
        List<String> hours = getHoursForQueue(queueNumber);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📅 %s\n", date));
        sb.append(String.format("🔌 Черга %s\n", queueNumber));

        // Empty list = data pending
        if (hours == null || hours.isEmpty()) {
            sb.append("⏳ Очікується");
        } else {
            sb.append("⏰ Години відключень:\n");
            for (String hour : hours) {
                sb.append(String.format("   • %s\n", hour));
            }
        }
        return sb.toString();
    }

    /**
     * Formats schedule for all queues.
     */
    public String formatAll() {
        return formatAll(null);
    }

    /**
     * Formats schedule for all queues with highlighting for user's queue.
     * If user has selected a queue, shows it prominently at the top.
     *
     * @param userQueue user's selected queue (can be null)
     */
    public String formatAll(String userQueue) {
        StringBuilder sb = new StringBuilder();

        // Date at the top (formatted)
        sb.append(String.format("📅 *%s*\n\n", getFormattedDate()));

        // If no data at all (day not found on website)
        if (!hasData()) {
            sb.append("⏳ _Графік очікується..._");
            return sb.toString();
        }

        // Show user's queue prominently at the top if selected
        if (userQueue != null && !userQueue.isEmpty()) {
            List<String> userHours = queueHours.get(userQueue);
            if (userHours != null && !userHours.isEmpty()) {
                sb.append(String.format("🔌 *Черга %s:*\n", userQueue));
                sb.append(String.format("⏰ *%s*\n", String.join(", ", userHours)));
                sb.append("\n· · · · · · · · · · · · · · · · · · · · ·\n\n");
            }
        }

        // Output all queues in correct order
        for (String queue : ALL_QUEUES) {
            List<String> hours = queueHours.get(queue);
            String hoursStr;

            // Empty list = data pending
            if (hours == null || hours.isEmpty()) {
                hoursStr = "⏳ очікується";
            } else {
                hoursStr = String.join(", ", hours);
            }

            // Highlight hours for user's queue with bold
            if (queue.equals(userQueue) && hours != null && !hours.isEmpty()) {
                sb.append(String.format("*%s:* *%s*\n", queue, hoursStr));
            } else {
                sb.append(String.format("*%s:* %s\n", queue, hoursStr));
            }
        }

        return sb.toString();
    }
}

