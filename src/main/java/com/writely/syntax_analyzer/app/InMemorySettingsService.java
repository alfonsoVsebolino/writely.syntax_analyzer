package com.writely.syntax_analyzer.app;

import java.util.Map;
import java.util.Objects;

/**
 * Hand-written in-memory test double for the {@link SettingsService} contract.
 *
 * <p>Dependent components test against this double without touching real storage.
 * Two doubles constructed over the same map simulate relaunch; maps kept apart
 * simulate isolated stores.</p>
 */
public class InMemorySettingsService implements SettingsService {

    private final Map<String, Boolean> store;

    public InMemorySettingsService(Map<String, Boolean> store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean getBoolean(String key) {
        SettingsService.requireInInventory(key);
        return store.getOrDefault(key, false);
    }

    @Override
    public void setBoolean(String key, boolean value) {
        SettingsService.requireInInventory(key);
        store.put(key, value);
    }
}
