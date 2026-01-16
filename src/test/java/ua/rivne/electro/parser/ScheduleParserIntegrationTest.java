package ua.rivne.electro.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ua.rivne.electro.config.Config;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify website parsing.
 * Run manually to debug parsing issues.
 */
@Disabled("Integration test - run manually")
class ScheduleParserIntegrationTest {

    @Test
    @DisplayName("Debug: Check website structure")
    void debugWebsiteStructure() throws Exception {
        System.out.println("Fetching: " + Config.SCHEDULE_URL);
        
        Document doc = Jsoup.connect(Config.SCHEDULE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get();
        
        System.out.println("Page title: " + doc.title());
        
        // Find all tables
        Elements tables = doc.select("table");
        System.out.println("Found " + tables.size() + " tables");
        
        for (int t = 0; t < tables.size(); t++) {
            Element table = tables.get(t);
            Elements rows = table.select("tr");
            System.out.println("\n=== Table " + t + " has " + rows.size() + " rows ===");
            
            // Print first 5 rows
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                Element row = rows.get(i);
                Elements cells = row.select("td, th");
                System.out.println("Row " + i + " has " + cells.size() + " cells:");
                for (int j = 0; j < Math.min(3, cells.size()); j++) {
                    System.out.println("  Cell " + j + ": " + cells.get(j).text().substring(0, Math.min(50, cells.get(j).text().length())));
                }
            }
        }
    }

    @Test
    @DisplayName("Debug: Test actual parsing")
    void debugActualParsing() {
        ScheduleParser parser = new ScheduleParser();
        
        // Force refresh
        parser.forceRefresh();
        
        System.out.println("Has cached data: " + parser.hasCachedData());
        System.out.println("Is source unavailable: " + parser.isSourceUnavailable());
        System.out.println("Last cache update: " + parser.getLastCacheUpdate());
        
        var schedules = parser.fetchSchedules();
        System.out.println("Schedules count: " + schedules.size());
        
        for (var schedule : schedules) {
            System.out.println("\nDate: " + schedule.getDate());
            System.out.println("Has data: " + schedule.hasData());
            var queues = schedule.getAllQueues();
            System.out.println("Queues: " + queues.size());
            for (var entry : queues.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }
}

