package com.writely.syntax_analyzer.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * Runs the {@link SettingsService} contract against the platform-preferences-backed
 * implementation. Each test allocates a unique store node and removes it in teardown,
 * so runs are order-independent and never leak into a developer's real settings.
 */
@DisplayName("PreferencesSettingsService contract")
class PreferencesSettingsServiceTest extends SettingsServiceContractTest {

    private String primaryNodePath;
    private String isolatedNodePath;

    @BeforeEach
    void allocateUniqueStoreNodes() {
        String prefix = "com/writely/syntax_analyzer/test-" + UUID.randomUUID();
        primaryNodePath = prefix + "/primary";
        isolatedNodePath = prefix + "/isolated";
    }

    @Override
    protected SettingsService createService() {
        return new PreferencesSettingsService(primaryNodePath);
    }

    @Override
    protected SettingsService createIsolatedService() {
        return new PreferencesSettingsService(isolatedNodePath);
    }

    @Override
    protected void cleanUp() throws Exception {
        Preferences root = Preferences.userRoot();
        for (String path : new String[]{primaryNodePath, isolatedNodePath}) {
            if (root.nodeExists(path)) {
                root.node(path).removeNode();
            }
        }
        root.flush();
    }
}
