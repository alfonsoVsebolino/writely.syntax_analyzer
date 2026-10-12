package com.writely.syntax_analyzer.app;

import java.util.Set;

/**
 * Service contract for persisting user preferences across launches.
 *
 * <p>The Settings Service is the application's single component for reading and writing
 * user preferences; no other component may touch the platform preference store directly.
 * Every stored key is inventoried on this interface so what the application remembers
 * stays auditable in one place.</p>
 */
public interface SettingsService {

    /** Inventory key: whether the user has seen the Onboarding Guide. */
    String KEY_ONBOARDING_SEEN = "onboardingSeen";

    /** The full inventory of every key this service is permitted to store. */
    Set<String> STORED_KEYS = Set.of(KEY_ONBOARDING_SEEN);

    /**
     * Reads a boolean preference.
     *
     * @param key one of {@link #STORED_KEYS}
     * @return the stored value, or {@code false} if the key has never been written
     * @throws IllegalArgumentException if the key is outside the inventory
     */
    boolean getBoolean(String key);

    /**
     * Writes a boolean preference.
     *
     * @param key   one of {@link #STORED_KEYS}
     * @param value the value to persist
     * @throws IllegalArgumentException if the key is outside the inventory
     */
    void setBoolean(String key, boolean value);

    /**
     * Guards a key against reads and writes outside the inventory.
     *
     * @param key the key to validate
     * @throws IllegalArgumentException if the key is outside the inventory
     */
    static void requireInInventory(String key) {
        if (!STORED_KEYS.contains(key)) {
            throw new IllegalArgumentException(
                    "Key '" + key + "' is not in the Settings Service key inventory: " + STORED_KEYS);
        }
    }

    /**
     * @return whether the Onboarding Guide has been seen
     */
    default boolean isOnboardingSeen() {
        return getBoolean(KEY_ONBOARDING_SEEN);
    }

    /**
     * Records whether the Onboarding Guide has been seen.
     *
     * @param seen the value to persist
     */
    default void setOnboardingSeen(boolean seen) {
        setBoolean(KEY_ONBOARDING_SEEN, seen);
    }
}
