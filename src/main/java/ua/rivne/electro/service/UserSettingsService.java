package ua.rivne.electro.service;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for storing user settings.
 * Saves data to file for persistence.
 */
public class UserSettingsService {

    private static final String SETTINGS_FILE = "user_settings.dat";

    // chatId -> selected queue (e.g., "1.1")
    private final Map<Long, String> userQueues;

    // chatId -> notifications enabled
    private final Map<Long, Boolean> notificationsEnabled;

    // Set of users who liked the bot
    private final Set<Long> usersWhoLiked;

    public UserSettingsService() {
        this.userQueues = new ConcurrentHashMap<>();
        this.notificationsEnabled = new ConcurrentHashMap<>();
        this.usersWhoLiked = ConcurrentHashMap.newKeySet();
        loadSettings();
    }

    /**
     * Saves selected queue for user.
     */
    public void setUserQueue(long chatId, String queue) {
        userQueues.put(chatId, queue);
        saveSettings();
    }

    /**
     * Gets user's selected queue.
     */
    public String getUserQueue(long chatId) {
        return userQueues.get(chatId);
    }

    /**
     * Checks if user has selected a queue.
     */
    public boolean hasQueue(long chatId) {
        return userQueues.containsKey(chatId);
    }

    /**
     * Enables/disables notifications for user.
     */
    public void setNotificationsEnabled(long chatId, boolean enabled) {
        notificationsEnabled.put(chatId, enabled);
        saveSettings();
    }

    /**
     * Checks if notifications are enabled.
     */
    public boolean isNotificationsEnabled(long chatId) {
        return notificationsEnabled.getOrDefault(chatId, false);
    }

    /**
     * Returns all users with notifications enabled.
     */
    public Set<Long> getUsersWithNotifications() {
        Set<Long> users = ConcurrentHashMap.newKeySet();
        notificationsEnabled.forEach((chatId, enabled) -> {
            if (enabled) users.add(chatId);
        });
        return users;
    }

    /**
     * Adds a like from user.
     */
    public void addLike(long chatId) {
        usersWhoLiked.add(chatId);
        saveSettings();
    }

    /**
     * Checks if user already liked the bot.
     */
    public boolean hasLiked(long chatId) {
        return usersWhoLiked.contains(chatId);
    }

    /**
     * Returns total number of likes.
     */
    public int getLikesCount() {
        return usersWhoLiked.size();
    }

    /**
     * Saves settings to file.
     */
    @SuppressWarnings("unchecked")
    private void saveSettings() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SETTINGS_FILE))) {
            oos.writeObject(new ConcurrentHashMap<>(userQueues));
            oos.writeObject(new ConcurrentHashMap<>(notificationsEnabled));
            oos.writeObject(new java.util.HashSet<>(usersWhoLiked));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads settings from file.
     */
    @SuppressWarnings("unchecked")
    private void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Map<Long, String> queues = (Map<Long, String>) ois.readObject();
            Map<Long, Boolean> notifications = (Map<Long, Boolean>) ois.readObject();

            userQueues.putAll(queues);
            notificationsEnabled.putAll(notifications);

            // Try to load likes (may not exist in old files)
            try {
                Set<Long> likes = (Set<Long>) ois.readObject();
                usersWhoLiked.addAll(likes);
            } catch (Exception ignored) {
                // Old file format without likes
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}

