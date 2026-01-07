package ua.rivne.electro.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ua.rivne.electro.config.Config;
import ua.rivne.electro.model.DailySchedule;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Parser for fetching power outage schedules from roe.vsei.ua website.
 * Implements caching to reduce requests to the website.
 */
public class ScheduleParser {

    // Sub-queue names in table order
    private static final String[] QUEUE_NAMES = {
        "1.1", "1.2", "2.1", "2.2", "3.1", "3.2",
        "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"
    };

    // Cache settings
    private static final int CACHE_UPDATE_INTERVAL_MINUTES = 30;
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // Cached data
    private volatile List<DailySchedule> cachedSchedules = Collections.emptyList();
    private volatile LocalDateTime lastCacheUpdate = null;
    private final ScheduledExecutorService cacheUpdater;

    public ScheduleParser() {
        this.cacheUpdater = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts the cache update scheduler.
     * Should be called once when the bot starts.
     */
    public void startCacheUpdater() {
        // Initial fetch
        refreshCache();

        // Schedule periodic updates
        cacheUpdater.scheduleAtFixedRate(
            this::refreshCache,
            CACHE_UPDATE_INTERVAL_MINUTES,
            CACHE_UPDATE_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        System.out.println("📊 Schedule cache updater started (every " + CACHE_UPDATE_INTERVAL_MINUTES + " min)");
    }

    /**
     * Stops the cache updater.
     */
    public void stopCacheUpdater() {
        cacheUpdater.shutdown();
    }

    /**
     * Refreshes the cache by fetching data from the website.
     */
    private void refreshCache() {
        try {
            List<DailySchedule> schedules = fetchSchedulesFromWebsite();
            cachedSchedules = schedules;
            lastCacheUpdate = LocalDateTime.now(KYIV_ZONE);
            System.out.println("✅ Cache updated at " + lastCacheUpdate + ", " + schedules.size() + " days loaded");
        } catch (IOException e) {
            System.err.println("❌ Failed to update cache: " + e.getMessage());
            // Keep old cache if update fails
        }
    }

    /**
     * Forces cache refresh. Use sparingly.
     */
    public void forceRefresh() {
        refreshCache();
    }

    static {
        // Disable SSL certificate verification (for roe.vsei.ua website)
        disableSSLVerification();
    }

    /**
     * Disables SSL certificate verification.
     * Required because roe.vsei.ua has certificate issues.
     */
    private static void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns cached schedules. Does not make HTTP requests.
     *
     * @return List of cached daily schedules
     */
    public List<DailySchedule> fetchSchedules() {
        return new ArrayList<>(cachedSchedules);
    }

    /**
     * Fetches current power outage schedules directly from the website.
     * Used internally for cache updates.
     *
     * @return List of daily schedules
     * @throws IOException if failed to fetch data from website
     */
    private List<DailySchedule> fetchSchedulesFromWebsite() throws IOException {
        List<DailySchedule> schedules = new ArrayList<>();

        // Load page (ignoring SSL errors)
        Document doc = Jsoup.connect(Config.SCHEDULE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .sslSocketFactory(getInsecureSSLSocketFactory())
                .get();

        // Find schedule table
        Element table = doc.selectFirst("table");
        if (table == null) {
            throw new IOException("Schedule table not found on page");
        }

        // Get all table rows
        Elements rows = table.select("tr");

        // Skip headers (first 2 rows) and process data
        for (int i = 2; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cells = row.select("td");

            if (cells.size() >= 13) {
                // First column - date
                String date = cells.get(0).text().trim();

                if (!date.isEmpty() && date.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
                    DailySchedule dailySchedule = new DailySchedule(date);

                    // Columns 1-12 - hours for each sub-queue
                    for (int j = 0; j < QUEUE_NAMES.length && j + 1 < cells.size(); j++) {
                        String hoursText = cells.get(j + 1).text().trim();
                        List<String> hours = parseHours(hoursText);
                        dailySchedule.addQueueHours(QUEUE_NAMES[j], hours);
                    }

                    schedules.add(dailySchedule);
                }
            }
        }

        return schedules;
    }

    /**
     * Parses text with outage hours.
     * Handles various formats:
     * - "13:00 - 17:00" (with spaces)
     * - "13:00-17:00" (without spaces)
     * - "08:00 - 12:00, 20:00 - 23:59" (comma separated)
     * - "08:00 - 12:00\n20:00 - 23:59" (newline separated)
     * - "08:00 - 12:0020:00 - 23:59" (no separator)
     */
    private List<String> parseHours(String text) {
        List<String> hours = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return hours;
        }

        // Normalize: replace newlines and commas with a unique separator
        String normalized = text
            .replace("\n", "|")
            .replace("\r", "")
            .replace(",", "|");

        // Split by separator or by pattern where one time range ends and another begins
        // Pattern: after HH:MM, before HH: (handles "12:0020:00" case)
        String[] parts = normalized.split("\\|+|(?<=\\d{2}:\\d{2})(?=\\d{2}:)");

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.contains(":")) {
                // Normalize the time range format: ensure "HH:MM - HH:MM" format
                String normalizedRange = normalizeTimeRange(trimmed);
                if (normalizedRange != null) {
                    hours.add(normalizedRange);
                }
            }
        }

        return hours;
    }

    /**
     * Normalizes time range to consistent format "HH:MM - HH:MM".
     * Handles: "13:00-17:00", "13:00 -17:00", "13:00- 17:00", "13:00 - 17:00"
     */
    private String normalizeTimeRange(String range) {
        // Remove extra spaces and normalize dashes
        String cleaned = range.trim()
            .replaceAll("\\s*-\\s*", " - ")  // Normalize "HH:MM-HH:MM" to "HH:MM - HH:MM"
            .replaceAll("\\s+", " ");         // Remove multiple spaces

        // Validate format: should match "HH:MM - HH:MM"
        if (cleaned.matches("\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}")) {
            return cleaned;
        }

        return null; // Invalid format
    }

    /**
     * Creates SSLSocketFactory that ignores certificate errors.
     */
    private static SSLSocketFactory getInsecureSSLSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets today's schedule from cache (using Kyiv timezone).
     */
    public DailySchedule getTodaySchedule() {
        LocalDate today = LocalDate.now(KYIV_ZONE);
        return getScheduleForDate(today);
    }

    /**
     * Gets tomorrow's schedule from cache (using Kyiv timezone).
     */
    public DailySchedule getTomorrowSchedule() {
        LocalDate tomorrow = LocalDate.now(KYIV_ZONE).plusDays(1);
        return getScheduleForDate(tomorrow);
    }

    /**
     * Finds schedule for a specific date from cache.
     */
    public DailySchedule getScheduleForDate(LocalDate date) {
        String dateStr = date.format(DATE_FORMAT);
        List<DailySchedule> schedules = fetchSchedules();

        for (DailySchedule schedule : schedules) {
            if (schedule.getDate().equals(dateStr)) {
                return schedule;
            }
        }

        // If schedule not found - return empty with date
        return new DailySchedule(dateStr);
    }

    /**
     * Returns the time of last cache update.
     */
    public LocalDateTime getLastCacheUpdate() {
        return lastCacheUpdate;
    }

    /**
     * Checks if cache has data.
     */
    public boolean hasCachedData() {
        return !cachedSchedules.isEmpty();
    }
}

