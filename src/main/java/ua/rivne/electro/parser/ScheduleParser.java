package ua.rivne.electro.parser;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import ua.rivne.electro.config.Config;
import ua.rivne.electro.config.ProxyConfig;
import ua.rivne.electro.model.DailySchedule;
import ua.rivne.electro.service.DatabaseService;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Proxy;
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
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Parser for fetching power outage schedules from the ROE (Rivneoblenergo) website.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Fetching HTML content from {@link Config#SCHEDULE_URL}</li>
 *   <li>Parsing schedule tables into {@link DailySchedule} objects</li>
 *   <li>Caching parsed data to minimize HTTP requests</li>
 *   <li>Automatic cache refresh every 30 minutes</li>
 * </ul>
 *
 * <p>The parser uses Jsoup for HTML parsing and handles SSL certificate issues
 * that may occur with the source website.
 *
 * <p>Usage:
 * <pre>{@code
 * ScheduleParser parser = new ScheduleParser();
 * parser.startCacheUpdater();  // Start background updates
 * DailySchedule today = parser.getTodaySchedule();
 * }</pre>
 *
 * @author Electro Bot Team
 * @version 1.0
 * @see DailySchedule
 */
public class ScheduleParser {

    /** Queue identifiers in the order they appear in the source table */
    private static final String[] QUEUE_NAMES = {
        "1.1", "1.2", "2.1", "2.2", "3.1", "3.2",
        "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"
    };

    /** How often to refresh the cache (in minutes) */
    private static final int CACHE_UPDATE_INTERVAL_MINUTES = 30;

    /** Timezone for date calculations */
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");

    /** Date format used in the source website */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Browser profiles for rotation to avoid blocking */
    private static final BrowserProfile[] BROWSER_PROFILES = {
        // Chrome on Windows 10
        new BrowserProfile(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "uk-UA,uk;q=0.9,en-US;q=0.8,en;q=0.7",
            "https://www.google.com.ua/"
        ),
        // Firefox on Windows 10
        new BrowserProfile(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "uk-UA,uk;q=0.8,en-US;q=0.5,en;q=0.3",
            "https://www.google.com.ua/"
        ),
        // Edge on Windows 11
        new BrowserProfile(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8",
            "uk,en-US;q=0.9,en;q=0.8",
            "https://www.bing.com/"
        ),
        // Chrome on Android
        new BrowserProfile(
            "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "uk-UA,uk;q=0.9,en;q=0.8",
            "https://www.google.com/"
        ),
        // Safari on macOS
        new BrowserProfile(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "uk-UA,uk;q=0.9",
            "https://www.google.com.ua/"
        )
    };

    private static final Random random = new Random();
    private volatile int lastProfileIndex = -1;

    private volatile List<DailySchedule> cachedSchedules = Collections.emptyList();
    private volatile LocalDateTime lastCacheUpdate = null;
    private volatile boolean lastFetchFailed = false;
    private final ScheduledExecutorService cacheUpdater;
    private final DatabaseService db;
    private final ProxyConfig proxyConfig;
    private Consumer<String> adminNotifier;

    /**
     * Browser profile for HTTP requests.
     */
    private static class BrowserProfile {
        final String userAgent;
        final String accept;
        final String acceptLanguage;
        final String referer;

        BrowserProfile(String userAgent, String accept, String acceptLanguage, String referer) {
            this.userAgent = userAgent;
            this.accept = accept;
            this.acceptLanguage = acceptLanguage;
            this.referer = referer;
        }
    }

    /**
     * Gets next browser profile, ensuring it's different from the last one used.
     */
    private BrowserProfile getNextBrowserProfile() {
        int newIndex;
        if (BROWSER_PROFILES.length <= 1) {
            newIndex = 0;
        } else {
            do {
                newIndex = random.nextInt(BROWSER_PROFILES.length);
            } while (newIndex == lastProfileIndex);
        }
        lastProfileIndex = newIndex;
        return BROWSER_PROFILES[newIndex];
    }

    /**
     * Creates a new ScheduleParser instance without database persistence.
     * Call {@link #startCacheUpdater()} to begin automatic cache updates.
     */
    public ScheduleParser() {
        this(null);
    }

    /**
     * Creates a new ScheduleParser instance with database persistence.
     * Call {@link #startCacheUpdater()} to begin automatic cache updates.
     *
     * @param db DatabaseService for persisting schedules (can be null)
     */
    public ScheduleParser(DatabaseService db) {
        this.cacheUpdater = Executors.newSingleThreadScheduledExecutor();
        this.db = db;
        this.proxyConfig = ProxyConfig.load();
    }

    /**
     * Sets the admin notifier callback.
     * Called when all connection attempts (direct + proxies) fail.
     *
     * @param notifier Consumer that receives error message to send to admin
     */
    public void setAdminNotifier(Consumer<String> notifier) {
        this.adminNotifier = notifier;
    }

    /**
     * Starts the background cache update scheduler.
     *
     * <p>This method should be called once when the bot starts.
     * It performs an immediate cache refresh and then schedules
     * updates every {@value #CACHE_UPDATE_INTERVAL_MINUTES} minutes.
     */
    public void startCacheUpdater() {
        // First, try to load from database
        loadFromDatabase();

        // Then fetch from website
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
     * Tries direct connection first, then proxies if configured.
     */
    private void refreshCache() {
        System.out.println("🔄 Attempting to refresh cache from website at " + LocalDateTime.now(KYIV_ZONE));

        StringBuilder errorLog = new StringBuilder();
        List<DailySchedule> schedules = null;

        // 1. Try direct connection first
        try {
            System.out.println("📡 Trying direct connection...");
            schedules = fetchSchedulesWithConnection(null, 30000);
        } catch (Exception e) {
            String error = "Direct: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            System.err.println("❌ " + error);
            errorLog.append("• ").append(error).append("\n");
        }

        // 2. If direct failed and we have proxies, try them
        if (schedules == null && proxyConfig.hasProxies()) {
            for (ProxyConfig.ProxyEntry proxyEntry : proxyConfig.getProxies()) {
                try {
                    System.out.println("📡 Trying proxy: " + proxyEntry);
                    schedules = fetchSchedulesWithConnection(proxyEntry.toProxy(), proxyConfig.getTimeoutMillis());
                    System.out.println("✅ Success via proxy: " + proxyEntry);
                    break; // Success!
                } catch (Exception e) {
                    String error = "Proxy " + proxyEntry + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
                    System.err.println("❌ " + error);
                    errorLog.append("• ").append(error).append("\n");
                }
            }
        }

        // 3. Process result
        if (schedules != null && !schedules.isEmpty()) {
            cachedSchedules = schedules;
            lastCacheUpdate = LocalDateTime.now(KYIV_ZONE);
            lastFetchFailed = false;
            saveToDatabase(schedules);
            System.out.println("✅ Cache updated at " + lastCacheUpdate + ", " + schedules.size() + " days loaded");
        } else {
            lastFetchFailed = true;
            System.err.println("❌ All connection attempts failed, keeping existing cache");

            // Notify admin if configured
            if (proxyConfig.isNotifyAdminOnFailure() && adminNotifier != null) {
                String message = "⚠️ *Не вдалося оновити графіки*\n\n" +
                    "Час: " + LocalDateTime.now(KYIV_ZONE).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n\n" +
                    "*Помилки:*\n" + errorLog.toString() + "\n" +
                    "_Дані з БД використовуються як резерв_";
                try {
                    adminNotifier.accept(message);
                } catch (Exception e) {
                    System.err.println("Failed to notify admin: " + e.getMessage());
                }
            }
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
     * Fetches schedules using specified connection (direct or via proxy).
     *
     * @param proxy Proxy to use, or null for direct connection
     * @param timeoutMs Connection timeout in milliseconds
     * @return List of daily schedules
     * @throws IOException if failed to fetch data
     */
    private List<DailySchedule> fetchSchedulesWithConnection(Proxy proxy, int timeoutMs) throws IOException {
        List<DailySchedule> schedules = new ArrayList<>();

        // Get random browser profile (different from last one)
        BrowserProfile profile = getNextBrowserProfile();
        long startTime = System.currentTimeMillis();

        // Build connection with realistic browser headers
        Connection connection = Jsoup.connect(Config.SCHEDULE_URL)
                .userAgent(profile.userAgent)
                .header("Accept", profile.accept)
                .header("Accept-Language", profile.acceptLanguage)
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
                .referrer(profile.referer)
                .timeout(timeoutMs)
                .sslSocketFactory(getInsecureSSLSocketFactory())
                .ignoreHttpErrors(true)
                .followRedirects(true);

        // Add proxy if specified
        if (proxy != null) {
            connection.proxy(proxy);
        }

        Document doc = connection.get();

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("🌐 Page loaded in " + elapsed + "ms" + (proxy != null ? " via proxy" : " direct"));

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
                        // Use html() to preserve <br> tags, then convert to newlines
                        String hoursHtml = cells.get(j + 1).html();
                        String hoursText = hoursHtml
                            .replaceAll("<br\\s*/?>", "\n")  // Convert <br> to newline
                            .replaceAll("<[^>]+>", "")       // Remove other HTML tags
                            .trim();
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
     * Package-private for testing.
     */
    List<String> parseHours(String text) {
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
     * Package-private for testing.
     */
    String normalizeTimeRange(String range) {
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

    /**
     * Checks if the last fetch attempt failed (source website unavailable).
     * Returns true if we have cached data but the last refresh failed.
     */
    public boolean isSourceUnavailable() {
        return lastFetchFailed && !cachedSchedules.isEmpty();
    }

    /**
     * Returns true if the last fetch attempt failed (regardless of cache state).
     */
    public boolean isLastFetchFailed() {
        return lastFetchFailed;
    }

    /**
     * Checks website availability and returns diagnostic info.
     * This method makes an actual HTTP request to test connectivity.
     * Tests direct connection and all configured proxies.
     *
     * @return WebsiteStatus with connection details (first successful or last failed)
     */
    public WebsiteStatus checkWebsiteStatus() {
        List<WebsiteStatus> allStatuses = checkAllConnections();

        // Return first successful or last failed
        for (WebsiteStatus status : allStatuses) {
            if (status.reachable) {
                return status;
            }
        }
        return allStatuses.isEmpty() ?
            new WebsiteStatus(false, 0, "No connections configured", false, 0, null) :
            allStatuses.get(allStatuses.size() - 1);
    }

    /**
     * Checks ALL connections (direct + all proxies) and returns list of statuses.
     * Used by /check command to show detailed diagnostics.
     *
     * @return List of WebsiteStatus for each connection attempt
     */
    public List<WebsiteStatus> checkAllConnections() {
        List<WebsiteStatus> results = new ArrayList<>();
        BrowserProfile profile = getNextBrowserProfile();

        // 1. Check direct connection
        results.add(checkConnectionStatus(profile, null, "Direct"));

        // 2. Check all proxies
        if (proxyConfig.hasProxies()) {
            for (ProxyConfig.ProxyEntry proxyEntry : proxyConfig.getProxies()) {
                results.add(checkConnectionStatus(profile, proxyEntry.toProxy(), proxyEntry.toString()));
            }
        }

        return results;
    }

    /**
     * Returns the number of configured proxies.
     */
    public int getProxyCount() {
        return proxyConfig.getProxies().size();
    }

    /**
     * Returns the configured timeout in seconds.
     */
    public int getTimeoutSeconds() {
        return proxyConfig.getTimeoutMillis() / 1000;
    }

    /**
     * Checks connection status with optional proxy.
     */
    private WebsiteStatus checkConnectionStatus(BrowserProfile profile, Proxy proxy, String connectionType) {
        long startTime = System.currentTimeMillis();
        try {
            Connection connection = Jsoup.connect(Config.SCHEDULE_URL)
                    .userAgent(profile.userAgent)
                    .header("Accept", profile.accept)
                    .header("Accept-Language", profile.acceptLanguage)
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .referrer(profile.referer)
                    .timeout(proxyConfig.getTimeoutMillis())
                    .sslSocketFactory(getInsecureSSLSocketFactory())
                    .ignoreHttpErrors(true)
                    .followRedirects(true);

            if (proxy != null) {
                connection.proxy(proxy);
            }

            Document doc = connection.get();
            long elapsed = System.currentTimeMillis() - startTime;

            // Check if page has schedule table
            Element table = doc.selectFirst("table");
            boolean hasTable = table != null;
            int rowCount = hasTable ? table.select("tr").size() : 0;

            return new WebsiteStatus(true, elapsed, connectionType + " OK", hasTable, rowCount, profile.userAgent);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return new WebsiteStatus(false, elapsed, connectionType + ": " + e.getClass().getSimpleName() + " - " + e.getMessage(), false, 0, profile.userAgent);
        }
    }

    /**
     * Website status information.
     */
    public static class WebsiteStatus {
        public final boolean reachable;
        public final long responseTimeMs;
        public final String error;
        public final boolean hasScheduleTable;
        public final int tableRowCount;
        public final String userAgent;

        public WebsiteStatus(boolean reachable, long responseTimeMs, String error, boolean hasScheduleTable, int tableRowCount, String userAgent) {
            this.reachable = reachable;
            this.responseTimeMs = responseTimeMs;
            this.error = error;
            this.hasScheduleTable = hasScheduleTable;
            this.tableRowCount = tableRowCount;
            this.userAgent = userAgent;
        }

        @Override
        public String toString() {
            String browserShort = userAgent != null ? userAgent.substring(0, Math.min(40, userAgent.length())) + "..." : "unknown";
            if (reachable) {
                return String.format("✅ Доступний (%dms), таблиця: %s, рядків: %d\nБраузер: %s",
                        responseTimeMs, hasScheduleTable ? "є" : "немає", tableRowCount, browserShort);
            } else {
                return String.format("❌ Недоступний (%dms): %s\nБраузер: %s", responseTimeMs, error, browserShort);
            }
        }
    }

    // ==================== Database Persistence ====================

    /**
     * Saves schedules to database.
     */
    private void saveToDatabase(List<DailySchedule> schedules) {
        if (db == null) return;

        for (DailySchedule schedule : schedules) {
            String json = scheduleToJson(schedule);
            db.saveSchedule(schedule.getDate(), json);
        }
    }

    /**
     * Loads schedules from database into cache.
     */
    private void loadFromDatabase() {
        if (db == null) return;

        Map<String, String> storedSchedules = db.loadAllSchedules();
        if (storedSchedules.isEmpty()) {
            System.out.println("📂 No schedules in database");
            return;
        }

        List<DailySchedule> schedules = new ArrayList<>();
        for (Map.Entry<String, String> entry : storedSchedules.entrySet()) {
            DailySchedule schedule = jsonToSchedule(entry.getKey(), entry.getValue());
            if (schedule != null) {
                schedules.add(schedule);
            }
        }

        if (!schedules.isEmpty()) {
            cachedSchedules = schedules;
            // Get last update time from database
            LocalDateTime dbUpdateTime = db.getSchedulesLastUpdate();
            if (dbUpdateTime != null) {
                lastCacheUpdate = dbUpdateTime;
            }
            System.out.println("📂 Loaded " + schedules.size() + " schedules from database (updated: " + lastCacheUpdate + ")");
        }
    }

    /**
     * Converts DailySchedule to JSON string.
     * Format: {"1.1":["08:00 - 12:00","16:00 - 20:00"],"1.2":["10:00 - 14:00"]}
     */
    private String scheduleToJson(DailySchedule schedule) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : schedule.getAllQueues().entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":[");
            boolean firstHour = true;
            for (String hour : entry.getValue()) {
                if (!firstHour) sb.append(",");
                firstHour = false;
                sb.append("\"").append(hour).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Converts JSON string to DailySchedule.
     */
    private DailySchedule jsonToSchedule(String date, String json) {
        try {
            DailySchedule schedule = new DailySchedule(date);

            // Simple JSON parsing (no external library needed)
            // Format: {"1.1":["08:00 - 12:00","16:00 - 20:00"],"1.2":[]}
            String content = json.substring(1, json.length() - 1); // Remove { }
            if (content.isEmpty()) return schedule;

            // Split by queue entries
            int pos = 0;
            while (pos < content.length()) {
                // Find queue name
                int qStart = content.indexOf("\"", pos) + 1;
                int qEnd = content.indexOf("\"", qStart);
                String queue = content.substring(qStart, qEnd);

                // Find hours array
                int arrStart = content.indexOf("[", qEnd) + 1;
                int arrEnd = content.indexOf("]", arrStart);
                String hoursStr = content.substring(arrStart, arrEnd);

                List<String> hours = new ArrayList<>();
                if (!hoursStr.isEmpty()) {
                    // Parse hours: "08:00 - 12:00","16:00 - 20:00"
                    String[] hourParts = hoursStr.split("\",\"");
                    for (String hour : hourParts) {
                        hours.add(hour.replace("\"", ""));
                    }
                }

                schedule.addQueueHours(queue, hours);
                pos = arrEnd + 1;

                // Skip comma if present
                if (pos < content.length() && content.charAt(pos) == ',') {
                    pos++;
                }
            }

            return schedule;
        } catch (Exception e) {
            System.err.println("Failed to parse schedule JSON: " + e.getMessage());
            return null;
        }
    }
}

