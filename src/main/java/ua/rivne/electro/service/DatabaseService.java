package ua.rivne.electro.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for PostgreSQL database operations.
 *
 * <p>This service handles all database interactions including:
 * <ul>
 *   <li>User settings (queue selection, notification preferences)</li>
 *   <li>Event logging for analytics</li>
 *   <li>Statistics collection (users, likes, activity)</li>
 * </ul>
 *
 * <p>Uses HikariCP for connection pooling with the following configuration:
 * <ul>
 *   <li>Maximum pool size: 5 connections</li>
 *   <li>Minimum idle: 1 connection</li>
 *   <li>Connection timeout: 30 seconds</li>
 * </ul>
 *
 * <p>Database tables are created automatically on first run:
 * <ul>
 *   <li>{@code user_settings} - User preferences and queue selection</li>
 *   <li>{@code bot_events} - Event logging for analytics</li>
 *   <li>{@code notification_messages} - Sent notification tracking</li>
 * </ul>
 *
 * @author Electro Bot Team
 * @version 1.0
 */
public class DatabaseService {

    private final HikariDataSource dataSource;

    /**
     * Creates a new DatabaseService and initializes the connection pool.
     *
     * <p>Supports Railway-style DATABASE_URL format:
     * {@code postgresql://username:password@host:port/database}
     *
     * @param databaseUrl PostgreSQL connection URL
     * @throws RuntimeException if database connection fails
     */
    public DatabaseService(String databaseUrl) {
        HikariConfig config = new HikariConfig();

        // Parse Railway's DATABASE_URL format:
        // postgresql://username:password@host:port/database
        try {
            String url = databaseUrl;

            // Remove protocol prefix
            if (url.startsWith("postgres://")) {
                url = url.substring("postgres://".length());
            } else if (url.startsWith("postgresql://")) {
                url = url.substring("postgresql://".length());
            }

            // Parse: username:password@host:port/database
            String[] atParts = url.split("@");
            String credentials = atParts[0];
            String hostPart = atParts[1];

            // Parse credentials
            String[] credParts = credentials.split(":");
            String username = credParts[0];
            String password = credParts[1];

            // Parse host:port/database
            String[] slashParts = hostPart.split("/");
            String hostPort = slashParts[0];
            String database = slashParts[1];

            String jdbcUrl = "jdbc:postgresql://" + hostPort + "/" + database + "?sslmode=require";

            config.setJdbcUrl(jdbcUrl);
            // Fix timezone issue - PostgreSQL doesn't recognize "Europe/Kiev", use UTC
            config.addDataSourceProperty("options", "-c timezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DATABASE_URL: " + databaseUrl, e);
        }

        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);
        initTables();
    }

    private void initTables() {
        String sql = """
            CREATE TABLE IF NOT EXISTS user_settings (
                chat_id BIGINT PRIMARY KEY,
                queue VARCHAR(10),
                notifications_enabled BOOLEAN DEFAULT FALSE,
                has_liked BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS notification_messages (
                id SERIAL PRIMARY KEY,
                chat_id BIGINT NOT NULL,
                message_id INTEGER NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_notification_messages_chat_id ON notification_messages(chat_id);

            CREATE TABLE IF NOT EXISTS bot_events (
                id SERIAL PRIMARY KEY,
                chat_id BIGINT NOT NULL,
                event_type VARCHAR(50) NOT NULL,
                event_data VARCHAR(255),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_bot_events_created_at ON bot_events(created_at);
            CREATE INDEX IF NOT EXISTS idx_bot_events_chat_id ON bot_events(chat_id);

            CREATE TABLE IF NOT EXISTS schedules (
                schedule_date VARCHAR(10) PRIMARY KEY,
                schedule_data TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Run migrations
        addForeignKeyConstraints();
    }

    /**
     * Adds foreign key constraints to ensure data integrity.
     * Cleans up orphaned records first.
     */
    private void addForeignKeyConstraints() {
        // First, clean up orphaned records (if any)
        String cleanupSql = """
            DELETE FROM notification_messages
            WHERE chat_id NOT IN (SELECT chat_id FROM user_settings);

            DELETE FROM bot_events
            WHERE chat_id NOT IN (SELECT chat_id FROM user_settings);
            """;

        // Then add FK constraints (if not exist)
        String fkSql = """
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_notification_messages_user'
                ) THEN
                    ALTER TABLE notification_messages
                    ADD CONSTRAINT fk_notification_messages_user
                    FOREIGN KEY (chat_id) REFERENCES user_settings(chat_id) ON DELETE CASCADE;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE constraint_name = 'fk_bot_events_user'
                ) THEN
                    ALTER TABLE bot_events
                    ADD CONSTRAINT fk_bot_events_user
                    FOREIGN KEY (chat_id) REFERENCES user_settings(chat_id) ON DELETE CASCADE;
                END IF;
            END $$;
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(cleanupSql);
            stmt.execute(fkSql);
            System.out.println("✅ Foreign key constraints verified");
        } catch (SQLException e) {
            System.err.println("⚠️ Failed to add FK constraints: " + e.getMessage());
        }
    }

    public void setUserQueue(long chatId, String queue) {
        String sql = """
            INSERT INTO user_settings (chat_id, queue) VALUES (?, ?)
            ON CONFLICT (chat_id) DO UPDATE SET queue = ?, updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            stmt.setString(2, queue);
            stmt.setString(3, queue);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getUserQueue(long chatId) {
        String sql = "SELECT queue FROM user_settings WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("queue");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean hasQueue(long chatId) {
        return getUserQueue(chatId) != null;
    }

    public void setNotificationsEnabled(long chatId, boolean enabled) {
        String sql = """
            INSERT INTO user_settings (chat_id, notifications_enabled) VALUES (?, ?)
            ON CONFLICT (chat_id) DO UPDATE SET notifications_enabled = ?, updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            stmt.setBoolean(2, enabled);
            stmt.setBoolean(3, enabled);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isNotificationsEnabled(long chatId) {
        String sql = "SELECT notifications_enabled FROM user_settings WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("notifications_enabled");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Set<Long> getUsersWithNotifications() {
        Set<Long> users = new HashSet<>();
        String sql = "SELECT chat_id FROM user_settings WHERE notifications_enabled = TRUE";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getLong("chat_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public void addLike(long chatId) {
        String sql = """
            INSERT INTO user_settings (chat_id, has_liked) VALUES (?, TRUE)
            ON CONFLICT (chat_id) DO UPDATE SET has_liked = TRUE, updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean hasLiked(long chatId) {
        String sql = "SELECT has_liked FROM user_settings WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("has_liked");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getLikesCount() {
        String sql = "SELECT COUNT(*) FROM user_settings WHERE has_liked = TRUE";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ==================== Event Logging ====================

    /**
     * Logs a bot event for analytics.
     */
    public void logEvent(long chatId, String eventType, String eventData) {
        String sql = "INSERT INTO bot_events (chat_id, event_type, event_data) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            stmt.setString(2, eventType);
            stmt.setString(3, eventData);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cleans up old events (older than specified days).
     */
    public void cleanOldEvents(int daysToKeep) {
        String sql = "DELETE FROM bot_events WHERE created_at < NOW() - INTERVAL '" + daysToKeep + " days'";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                System.out.println("Cleaned " + deleted + " old events");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== Statistics ====================

    /**
     * Returns total number of users.
     */
    public int getTotalUsers() {
        String sql = "SELECT COUNT(*) FROM user_settings";
        return getCountResult(sql);
    }

    /**
     * Returns number of users who selected a queue.
     */
    public int getUsersWithQueue() {
        String sql = "SELECT COUNT(*) FROM user_settings WHERE queue IS NOT NULL";
        return getCountResult(sql);
    }

    /**
     * Returns number of users with notifications enabled.
     */
    public int getUsersWithNotificationsEnabled() {
        String sql = "SELECT COUNT(*) FROM user_settings WHERE notifications_enabled = TRUE";
        return getCountResult(sql);
    }

    /**
     * Returns queue distribution (how many users selected each queue).
     */
    public java.util.Map<String, Integer> getQueueDistribution() {
        java.util.Map<String, Integer> distribution = new java.util.LinkedHashMap<>();
        String sql = "SELECT queue, COUNT(*) as cnt FROM user_settings WHERE queue IS NOT NULL GROUP BY queue ORDER BY queue";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                distribution.put(rs.getString("queue"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return distribution;
    }

    /**
     * Returns number of events for today.
     */
    public int getEventsToday() {
        String sql = "SELECT COUNT(*) FROM bot_events WHERE created_at >= CURRENT_DATE";
        return getCountResult(sql);
    }

    /**
     * Returns number of unique active users today.
     */
    public int getActiveUsersToday() {
        String sql = "SELECT COUNT(DISTINCT chat_id) FROM bot_events WHERE created_at >= CURRENT_DATE";
        return getCountResult(sql);
    }

    /**
     * Returns number of unique active users in last 7 days.
     */
    public int getActiveUsersWeek() {
        String sql = "SELECT COUNT(DISTINCT chat_id) FROM bot_events WHERE created_at >= NOW() - INTERVAL '7 days'";
        return getCountResult(sql);
    }

    /**
     * Returns daily user growth (new users per day for last N days).
     */
    public java.util.Map<String, Integer> getDailyUserGrowth(int days) {
        java.util.Map<String, Integer> growth = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT DATE(created_at) as day, COUNT(*) as cnt
            FROM user_settings
            WHERE created_at >= NOW() - INTERVAL '%d days'
            GROUP BY DATE(created_at)
            ORDER BY day
            """.formatted(days);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                growth.put(rs.getString("day"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return growth;
    }

    /**
     * Returns daily active users count (unique users per day for last N days).
     */
    public java.util.Map<String, Integer> getDailyActiveUsers(int days) {
        java.util.Map<String, Integer> activeUsers = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT DATE(created_at) as day, COUNT(DISTINCT chat_id) as cnt
            FROM bot_events
            WHERE created_at >= NOW() - INTERVAL '%d days'
            GROUP BY DATE(created_at)
            ORDER BY day
            """.formatted(days);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                activeUsers.put(rs.getString("day"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return activeUsers;
    }

    /**
     * Returns popular commands/actions.
     */
    public java.util.Map<String, Integer> getPopularActions(int limit) {
        java.util.Map<String, Integer> actions = new java.util.LinkedHashMap<>();
        String sql = """
            SELECT event_data, COUNT(*) as cnt
            FROM bot_events
            WHERE created_at >= NOW() - INTERVAL '7 days'
            GROUP BY event_data
            ORDER BY cnt DESC
            LIMIT %d
            """.formatted(limit);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                actions.put(rs.getString("event_data"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return actions;
    }

    private int getCountResult(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ==================== Schedule Storage ====================

    /**
     * Saves a schedule to the database.
     * Uses JSON format to store queue hours data.
     *
     * @param date schedule date in format "dd.MM.yyyy"
     * @param scheduleJson JSON representation of schedule data
     */
    public void saveSchedule(String date, String scheduleJson) {
        String sql = """
            INSERT INTO schedules (schedule_date, schedule_data, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (schedule_date) DO UPDATE SET schedule_data = ?, updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            stmt.setString(2, scheduleJson);
            stmt.setString(3, scheduleJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a schedule from the database.
     *
     * @param date schedule date in format "dd.MM.yyyy"
     * @return JSON representation of schedule data, or null if not found
     */
    public String loadSchedule(String date) {
        String sql = "SELECT schedule_data FROM schedules WHERE schedule_date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, date);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("schedule_data");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Loads all schedules from the database.
     *
     * @return Map of date -> JSON schedule data
     */
    public java.util.Map<String, String> loadAllSchedules() {
        java.util.Map<String, String> schedules = new java.util.HashMap<>();
        String sql = "SELECT schedule_date, schedule_data FROM schedules";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                schedules.put(rs.getString("schedule_date"), rs.getString("schedule_data"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return schedules;
    }

    /**
     * Deletes old schedules (older than specified days).
     *
     * @param daysToKeep number of days to keep
     */
    public void cleanOldSchedules(int daysToKeep) {
        String sql = "DELETE FROM schedules WHERE updated_at < NOW() - INTERVAL '" + daysToKeep + " days'";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                System.out.println("Cleaned " + deleted + " old schedules");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the latest update time from schedules table.
     *
     * @return latest updated_at timestamp, or null if no schedules
     */
    public java.time.LocalDateTime getSchedulesLastUpdate() {
        String sql = "SELECT MAX(updated_at) FROM schedules";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp(1);
                if (ts != null) {
                    return ts.toLocalDateTime();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}

