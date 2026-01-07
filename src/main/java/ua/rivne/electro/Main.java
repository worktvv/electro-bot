package ua.rivne.electro;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ua.rivne.electro.bot.ElectroBot;
import ua.rivne.electro.config.Config;

/**
 * Main class for launching the Telegram bot.
 *
 * The bot provides information about power outage schedules
 * for Rivne city and Rivne region.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("🔌 Starting Electro Bot...");

        try {
            // Load configuration
            Config config = Config.load();

            // Create Telegram Bots API
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Register our bot
            ElectroBot bot = new ElectroBot(config);
            botsApi.registerBot(bot);

            System.out.println("✅ Bot started successfully!");
            System.out.println("📱 Username: @" + config.getBotUsername());

        } catch (TelegramApiException e) {
            System.err.println("❌ Bot startup error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

