package com.writely.syntax_analyzer.app;

import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs the {@link SettingsService} contract against the hand-written in-memory test double,
 * proving the double honors the same contract dependent components will rely on.
 */
@DisplayName("InMemorySettingsService contract")
class InMemorySettingsServiceTest extends SettingsServiceContractTest {

    private final Map<String, Boolean> primaryStore = new HashMap<>();
    private final Map<String, Boolean> isolatedStore = new HashMap<>();

    @Override
    protected SettingsService createService() {
        return new InMemorySettingsService(primaryStore);
    }

    @Override
    protected SettingsService createIsolatedService() {
        return new InMemorySettingsService(isolatedStore);
    }

    @Override
    protected void cleanUp() {
        primaryStore.clear();
        isolatedStore.clear();
    }
}
