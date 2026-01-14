package ua.rivne.electro.service;

import java.util.Set;

/**
 * Service for storing user settings.
 * Uses PostgreSQL database for persistence.
 */
public class UserSettingsService {

    private final DatabaseService db;

    public UserSettingsService(DatabaseService db) {
        this.db = db;
    }

    public void setUserQueue(long chatId, String queue) {
        db.setUserQueue(chatId, queue);
    }

    public String getUserQueue(long chatId) {
        return db.getUserQueue(chatId);
    }

    public boolean hasQueue(long chatId) {
        return db.hasQueue(chatId);
    }

    public void setNotificationsEnabled(long chatId, boolean enabled) {
        db.setNotificationsEnabled(chatId, enabled);
    }

    public boolean isNotificationsEnabled(long chatId) {
        return db.isNotificationsEnabled(chatId);
    }

    public Set<Long> getUsersWithNotifications() {
        return db.getUsersWithNotifications();
    }

    public void addLike(long chatId) {
        db.addLike(chatId);
    }

    public boolean hasLiked(long chatId) {
        return db.hasLiked(chatId);
    }

    public int getLikesCount() {
        return db.getLikesCount();
    }
}

