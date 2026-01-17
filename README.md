# ⚡ Electro Bot

A Telegram bot that provides power outage schedules for a city and region in Ukraine.

## 📋 Features

- **Schedule viewing**: Today's, tomorrow's, and all available schedules
- **Queue selection**: Choose your specific power queue (1.1, 1.2, 2.1, 2.2, 3.1, 3.2)
- **Notifications**: Get alerts 30 and 5 minutes before scheduled outages
- **Persistent menu**: Easy access to all features via keyboard buttons
- **Like system**: Users can support the bot with likes

## 🛠️ Tech Stack

- **Java 17**
- **Maven** - Build tool
- **TelegramBots API** - Telegram integration
- **Jsoup** - HTML parsing for schedule data
- **PostgreSQL** - Database for user settings and analytics
- **HikariCP** - Connection pooling
- **Logback** - Logging with rotation
- **Dotenv** - Environment configuration
- **JUnit 5 + Mockito** - Testing
- **JaCoCo** - Test coverage reports

## 📁 Project Structure

```
src/main/java/ua/rivne/electro/
├── Main.java                    # Application entry point
├── bot/
│   ├── ElectroBot.java          # Main bot logic
│   └── KeyboardFactory.java     # Telegram keyboard builders
├── config/
│   └── Config.java              # Configuration loader
├── model/
│   └── DailySchedule.java       # Schedule data model
├── parser/
│   └── ScheduleParser.java      # HTML schedule parser
└── service/
    ├── DatabaseService.java     # Database operations
    ├── UserSettingsService.java # User preferences
    └── NotificationService.java # Outage notifications
```

## ⚙️ Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `BOT_TOKEN` | ✅ | Telegram bot token from @BotFather | `123456789:ABCdef...` |
| `BOT_USERNAME` | ✅ | Bot username (without @) | `my_electro_bot` |
| `DATABASE_URL` | ✅ | PostgreSQL connection URL | `postgresql://user:pass@host:5432/db` |

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL database

### Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/electro-bot.git
   cd electro-bot
   ```

2. **Create `.env` file**
   ```env
   BOT_TOKEN=your_bot_token_here
   BOT_USERNAME=your_bot_username
   DATABASE_URL=postgresql://localhost:5432/electro_bot
   ```

3. **Build the project**
   ```bash
   mvn clean package
   ```

4. **Run the bot**
   ```bash
   java -jar target/electro-bot-1.0-SNAPSHOT.jar
   ```

### Using Docker

```bash
docker build -t electro-bot .
docker run -d \
  -e BOT_TOKEN=your_token \
  -e BOT_USERNAME=your_username \
  -e DATABASE_URL=your_db_url \
  electro-bot
```

## 🤖 Bot Commands

| Command | Description |
|---------|-------------|
| `/start` | Start the bot and show welcome message |
| `/help` | Show help information |
| `/today` | Show today's schedule |
| `/tomorrow` | Show tomorrow's schedule |
| `/all` | Show all available schedules |
| `/menu` | Show persistent menu |

## 🧪 Running Tests

```bash
mvn test
```

## 📊 Database Schema

The bot automatically creates the following tables:

- `user_settings` - User preferences (queue, notifications, likes)
- `bot_events` - Event logging for analytics
- `notification_messages` - Sent notification tracking

## 🔔 Notification System

The bot sends notifications at:
- **30 minutes** before scheduled outage (⚠️ warning)
- **5 minutes** before scheduled outage (🚨 urgent)

Notifications are sent only to users who:
1. Have selected their queue
2. Have enabled notifications

## 📄 License

This project is open source and available under the MIT License.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

Made with ❤️ for Ukraine

