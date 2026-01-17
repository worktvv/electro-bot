package ua.rivne.electro.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration loader class.
 *
 * Stores bot token and other settings.
 */
public class Config {

    private final String botToken;
    private final String botUsername;
    private final String databaseUrl;
    private final Long adminChatId;

    // URL of the power outage schedule page
    public static final String SCHEDULE_URL = "https://www.roe.vsei.ua/disconnections";

    private Config(String botToken, String botUsername, String databaseUrl, Long adminChatId) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.databaseUrl = databaseUrl;
        this.adminChatId = adminChatId;
    }

    /**
     * Loads configuration from environment variables or .env file.
     * Priority: system environment variables > .env file
     */
    public static Config load() {
        // First check system environment variables (for Railway/Docker)
        String token = System.getenv("BOT_TOKEN");
        String username = System.getenv("BOT_USERNAME");
        String databaseUrl = System.getenv("DATABASE_URL");
        String adminChatIdStr = System.getenv("ADMIN_CHAT_ID");

        // If not in system env - read from .env (for local development)
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        if (token == null) {
            token = dotenv.get("BOT_TOKEN");
        }
        if (username == null) {
            username = dotenv.get("BOT_USERNAME");
        }
        if (databaseUrl == null) {
            databaseUrl = dotenv.get("DATABASE_URL");
        }
        if (adminChatIdStr == null) {
            adminChatIdStr = dotenv.get("ADMIN_CHAT_ID");
        }

        if (token == null || token.isEmpty()) {
            throw new RuntimeException(
                "❌ BOT_TOKEN not found! Set environment variable or create .env file.\n" +
                "   Example: BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
            );
        }

        if (username == null || username.isEmpty()) {
            throw new RuntimeException(
                "❌ BOT_USERNAME not found! Set environment variable or add to .env file.\n" +
                "   Example: BOT_USERNAME=my_electro_bot"
            );
        }

        if (databaseUrl == null || databaseUrl.isEmpty()) {
            throw new RuntimeException(
                "❌ DATABASE_URL not found! Set environment variable or add to .env file.\n" +
                "   Example: DATABASE_URL=postgresql://user:password@host:5432/database"
            );
        }

        // ADMIN_CHAT_ID is optional
        Long adminChatId = null;
        if (adminChatIdStr != null && !adminChatIdStr.isEmpty()) {
            try {
                adminChatId = Long.parseLong(adminChatIdStr);
            } catch (NumberFormatException e) {
                System.err.println("⚠️ Invalid ADMIN_CHAT_ID format, ignoring: " + adminChatIdStr);
            }
        }

        return new Config(token, username, databaseUrl, adminChatId);
    }

    public String getBotToken() {
        return botToken;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public Long getAdminChatId() {
        return adminChatId;
    }

    public boolean isAdmin(long chatId) {
        return adminChatId != null && adminChatId == chatId;
    }
}

