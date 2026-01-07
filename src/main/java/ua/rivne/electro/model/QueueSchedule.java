package ua.rivne.electro.model;

import java.util.List;

/**
 * Model for storing power outage schedule for one queue for one day.
 *
 * Example: Queue 1.1, date 06.01.2026, hours ["13:00 - 17:00", "20:00 - 23:59"]
 */
public class QueueSchedule {

    private final String queueNumber;    // Queue number (e.g., "1.1", "2.2")
    private final String date;           // Date (e.g., "06.01.2026")
    private final List<String> hours;    // Outage hours

    public QueueSchedule(String queueNumber, String date, List<String> hours) {
        this.queueNumber = queueNumber;
        this.date = date;
        this.hours = hours;
    }

    public String getQueueNumber() {
        return queueNumber;
    }

    public String getDate() {
        return date;
    }

    public List<String> getHours() {
        return hours;
    }

    /**
     * Returns hours as a formatted string for display.
     */
    public String getHoursFormatted() {
        if (hours == null || hours.isEmpty()) {
            return "Відключень не заплановано ✅";
        }
        return String.join(", ", hours);
    }

    @Override
    public String toString() {
        return String.format("Черга %s (%s): %s", queueNumber, date, getHoursFormatted());
    }
}

