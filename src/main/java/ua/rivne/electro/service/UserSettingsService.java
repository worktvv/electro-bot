package ua.rivne.electro.service;

import java.util.HashSet;
import java.util.Set;

/**
 * Service for storing user settings.
 * Uses PostgreSQL database for persistence.
 */
public class UserSettingsService {

    private final DatabaseService db;

    // In-memory set for tracking users waiting to send feedback (temporary state)
    private final Set<Long> usersWaitingForFeedback = new HashSet<>();

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

    // === Feedback state management (in-memory) ===

    /**
     * Sets user to waiting for feedback state.
     */
    public void setWaitingForFeedback(long chatId, boolean waiting) {
        if (waiting) {
            usersWaitingForFeedback.add(chatId);
        } else {
            usersWaitingForFeedback.remove(chatId);
        }
    }

    /**
     * Checks if user is waiting to send feedback.
     */
    public boolean isWaitingForFeedback(long chatId) {
        return usersWaitingForFeedback.contains(chatId);
    }
}

