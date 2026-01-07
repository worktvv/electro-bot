package ua.rivne.electro.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for PostgreSQL database operations.
 */
public class DatabaseService {

    private final HikariDataSource dataSource;

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

            String jdbcUrl = "jdbc:postgresql://" + hostPort + "/" + database;

            config.setJdbcUrl(jdbcUrl);
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
            """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
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

    public void addNotificationMessageId(long chatId, int messageId) {
        String sql = "INSERT INTO notification_messages (chat_id, message_id) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            stmt.setInt(2, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Set<Integer> getAndClearNotificationMessageIds(long chatId) {
        Set<Integer> ids = new HashSet<>();
        String selectSql = "SELECT message_id FROM notification_messages WHERE chat_id = ?";
        String deleteSql = "DELETE FROM notification_messages WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setLong(1, chatId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    ids.add(rs.getInt("message_id"));
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setLong(1, chatId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ids;
    }

    public boolean hasNotifications(long chatId) {
        String sql = "SELECT COUNT(*) FROM notification_messages WHERE chat_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

