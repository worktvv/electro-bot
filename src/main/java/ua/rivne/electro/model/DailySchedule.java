package ua.rivne.electro.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model for storing power outage schedule for one day for all queues.
 */
public class DailySchedule {

    // All possible queues
    private static final String[] ALL_QUEUES = {
        "1.1", "1.2", "2.1", "2.2", "3.1", "3.2",
        "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"
    };

    private final String date;
    // Key - queue number (e.g., "1.1"), value - outage hours (null = pending)
    private final Map<String, List<String>> queueHours;

    public DailySchedule(String date) {
        this.date = date;
        this.queueHours = new HashMap<>();
    }

    public void addQueueHours(String queueNumber, List<String> hours) {
        queueHours.put(queueNumber, hours);
    }

    public String getDate() {
        return date;
    }

    /**
     * Returns hours for a queue. null means "pending".
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
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📅 *%s*\n\n", date));

        // If no data at all (day not found on website)
        if (!hasData()) {
            sb.append("⏳ _Графік очікується..._");
            return sb.toString();
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

            sb.append(String.format("*%s:* %s\n", queue, hoursStr));
        }

        return sb.toString();
    }
}

