# 16. Settings Persistence via the JDK Platform Preference Store

- **Status**: Accepted
- **Date**: 2026-10-12

## Context

The application forgets everything between launches. Any preference a user expresses — starting with whether the Onboarding Guide has been seen — evaporates when the window closes. Features that need to remember user state cannot be built honestly today: either they nag on every session or they pretend to remember. Before this decision there was no mechanism to store user settings, and inventing ad-hoc storage per feature would scatter persistence across competing conventions.

This is the first-ever persistence in this codebase, so the mechanism had to be chosen deliberately and recorded for the oral defense.

Alternatives considered:

1. **A settings file in the user's home directory.** No file-location convention exists in this project (no XDG/AppData helper, no path-resolution code), so the location itself would be an arbitrary new convention.
2. **Session-only in-memory state.** Does not survive restarts; fails the requirement outright.
3. **The JDK's `java.util.prefs.Preferences` store.** OS-managed per-user location, shipped with the JDK (zero new dependencies), fully offline.

## Decision

1. **Storage backend: the JDK platform preference store** (`java.util.prefs.Preferences`). The trade-off against a home-directory settings file and against session-only state is settled in favor of the platform store: idiomatic per-user storage with no new dependency and no invented file-location convention.
2. **Single mechanism rule**: the `SettingsService` component is the only component permitted to touch the platform preference store. All other components depend on the `SettingsService` interface (`com.writely.syntax_analyzer.app.SettingsService`).
3. **Shape**: an interface with a default preferences-backed implementation (`PreferencesSettingsService`), mirroring the existing source-ingestion service pattern (interface + default implementation).
4. **Key inventory**: every stored key is declared on the interface (`SettingsService.STORED_KEYS`). Reads and writes of keys outside the inventory are rejected. The inventory today contains exactly one key, `onboardingSeen`, owned by the Onboarding Guide feature (ticket #26).
5. **Testability**: the service sits behind the interface so dependent components use a hand-written test double (`InMemorySettingsService`) without touching real storage. Tests allocate a unique preference node per test and remove it in teardown; persistence is proven by a simulated relaunch (write with one service instance, read with another over the same node).

## Consequences

- The application gains one auditable place where user state lives; future preferences (window geometry, recent files, divider positions) go through the same service instead of inventing storage.
- Storage location is delegated to the OS; there is no in-repo file to inspect when debugging saved state — inspection goes through the service API.
- The first-run Onboarding Guide flag (`onboardingSeen`) now survives restarts, unblocking ticket #26.
- No UI ships with this decision; a reset affordance for `onboardingSeen`, if ever wanted, writes the same single key and needs no new storage surface.
- Package placement of the Onboarding Guide's UI classes remains an open question for a follow-up design session; placement of the Settings Service itself (application infrastructure / bootstrap tier) is settled here.
