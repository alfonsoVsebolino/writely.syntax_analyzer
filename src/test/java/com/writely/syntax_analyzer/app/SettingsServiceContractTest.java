package com.writely.syntax_analyzer.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the {@link SettingsService} seam.
 *
 * <p>Runs against every implementation: the platform-preferences-backed service and the
 * hand-written in-memory test double. Tests assert external behavior only — values written
 * are values read back, persistence survives a simulated relaunch, and backing stores are
 * isolated from each other.</p>
 */
@DisplayName("Settings Service contract")
abstract class SettingsServiceContractTest {

    /** A fresh service instance over the primary backing store (same store as previous calls). */
    protected abstract SettingsService createService();

    /** A fresh service instance over a separate, isolated backing store. */
    protected abstract SettingsService createIsolatedService();

    @AfterEach
    void tearDownBackingStore() throws Exception {
        cleanUp();
    }

    /** Removes the backing stores allocated for this test. */
    protected abstract void cleanUp() throws Exception;

    @Test
    @DisplayName("value written is value read back (true and false round-trip)")
    void roundTrip() {
        SettingsService service = createService();

        service.setBoolean(SettingsService.KEY_ONBOARDING_SEEN, true);
        assertTrue(service.getBoolean(SettingsService.KEY_ONBOARDING_SEEN));

        service.setBoolean(SettingsService.KEY_ONBOARDING_SEEN, false);
        assertFalse(service.getBoolean(SettingsService.KEY_ONBOARDING_SEEN));
    }

    @Test
    @DisplayName("preference never written reads as false")
    void defaultsToFalseWhenUnset() {
        assertFalse(createService().getBoolean(SettingsService.KEY_ONBOARDING_SEEN));
    }

    @Test
    @DisplayName("simulated relaunch: a fresh service instance over the same store reads the written value")
    void persistsAcrossSimulatedRelaunch() {
        SettingsService firstLaunch = createService();
        firstLaunch.setBoolean(SettingsService.KEY_ONBOARDING_SEEN, true);

        SettingsService secondLaunch = createService();
        assertTrue(secondLaunch.getBoolean(SettingsService.KEY_ONBOARDING_SEEN),
                "a fresh instance over the same store must read the value written by the previous instance");
    }

    @Test
    @DisplayName("stores are isolated: writing one store is invisible to another")
    void storesAreIsolated() {
        SettingsService primary = createService();
        SettingsService other = createIsolatedService();

        primary.setBoolean(SettingsService.KEY_ONBOARDING_SEEN, true);

        assertFalse(other.getBoolean(SettingsService.KEY_ONBOARDING_SEEN),
                "a separate store must not observe writes made to the primary store");
    }

    @Test
    @DisplayName("key inventory lives on the interface and contains exactly onboardingSeen")
    void keyInventoryIsExactlyOnboardingSeen() {
        assertEquals(Set.of(SettingsService.KEY_ONBOARDING_SEEN), SettingsService.STORED_KEYS);
    }

    @Test
    @DisplayName("keys outside the inventory are rejected")
    void unknownKeyIsRejected() {
        SettingsService service = createService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.setBoolean("notAnInventoryKey", true));
        assertTrue(ex.getMessage().contains("notAnInventoryKey"),
                "rejection message should name the offending key, was: " + ex.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> service.getBoolean("notAnInventoryKey"));
    }

    @Test
    @DisplayName("typed accessors read and write the onboardingSeen key")
    void typedOnboardingAccessors() {
        SettingsService service = createService();

        assertFalse(service.isOnboardingSeen());

        service.setOnboardingSeen(true);
        assertTrue(service.isOnboardingSeen());
        assertTrue(service.getBoolean(SettingsService.KEY_ONBOARDING_SEEN),
                "typed accessor must write through the inventory key");

        service.setOnboardingSeen(false);
        assertFalse(service.isOnboardingSeen());
    }
}
