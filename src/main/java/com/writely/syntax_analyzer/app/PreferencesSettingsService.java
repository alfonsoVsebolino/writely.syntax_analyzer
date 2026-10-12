package com.writely.syntax_analyzer.app;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Preferences-backed implementation of the {@link SettingsService} contract.
 *
 * <p>Stores preferences on the JDK platform preference store (per-user, OS-managed,
 * zero new dependencies, fully offline). This class is the only component permitted
 * to touch the platform preference store; all other components consume the
 * {@link SettingsService} interface.</p>
 *
 * <p>Constructed with an explicit preference node path so tests can allocate a unique
 * store node per test and remove it in teardown.</p>
 */
public class PreferencesSettingsService implements SettingsService {

    /** Preference node path used by the running application. */
    public static final String APPLICATION_NODE_PATH = "com/writely/syntax_analyzer/settings";

    private final Preferences node;

    public PreferencesSettingsService(String nodePath) {
        this.node = Preferences.userRoot().node(nodePath);
    }

    public PreferencesSettingsService() {
        this(APPLICATION_NODE_PATH);
    }

    @Override
    public boolean getBoolean(String key) {
        SettingsService.requireInInventory(key);
        return node.getBoolean(key, false);
    }

    @Override
    public void setBoolean(String key, boolean value) {
        SettingsService.requireInInventory(key);
        node.putBoolean(key, value);
        try {
            node.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("Cannot flush preference node: " + node.absolutePath(), e);
        }
    }

    /** Removes the preference node at the given path, if it exists. Used by test teardown. */
    public static void removeNode(String nodePath) throws BackingStoreException {
        Preferences root = Preferences.userRoot();
        if (root.nodeExists(nodePath)) {
            root.node(nodePath).removeNode();
            root.flush();
        }
    }
}
