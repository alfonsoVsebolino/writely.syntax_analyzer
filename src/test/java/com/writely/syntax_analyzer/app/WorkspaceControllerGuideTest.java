package com.writely.syntax_analyzer.app;

import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.ingestion.DefaultSourceIngestionService;
import com.writely.syntax_analyzer.core.report.ReportExportService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceControllerGuideTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            Platform.runLater(() -> Platform.setImplicitExit(false));
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX Platform should start within 5s");
    }

    private static void runOnFxThread(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        future.get(15, TimeUnit.SECONDS);
    }

    private WorkspaceController loadControllerWithSettings(SettingsService settingsService) throws IOException {
        URL fxmlUrl = getClass().getResource("/fxml/main-workspace.fxml");
        assertNotNull(fxmlUrl, "FXML resource /fxml/main-workspace.fxml must exist");
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        loader.setControllerFactory(param -> new WorkspaceController(
                new DefaultSourceIngestionService(),
                new AnalysisOrchestrator(),
                new ReportExportService(),
                settingsService
        ));
        Parent root = loader.load();
        assertNotNull(root);
        WorkspaceController controller = loader.getController();
        assertNotNull(controller);
        return controller;
    }

    @Test
    @DisplayName("First-run seam with unseen stub: overlay is visible and contains all five section titles verbatim")
    void testFirstRunUnseenGuideModal() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                // onboardingSeen is absent => false
                InMemorySettingsService settingsService = new InMemorySettingsService(store);
                assertFalse(settingsService.isOnboardingSeen());

                WorkspaceController controller = loadControllerWithSettings(settingsService);

                // Guide overlay should be visible
                assertTrue(controller.isGuideVisible(), "Guide overlay must be visible on first run");
                assertNotNull(controller.getGuideOverlay());
                assertTrue(controller.getGuideOverlay().isVisible());
                assertTrue(controller.getGuideOverlay().isManaged());

                // Content should contain all five canonical section titles verbatim
                List<String> titles = controller.getGuideSectionTitles();
                assertEquals(5, titles.size(), "Rendered content must contain 5 sections");
                assertEquals("What writely Is and Is Not", titles.get(0));
                assertEquals("Input Modes", titles.get(1));
                assertEquals("Check Categories", titles.get(2));
                assertEquals("Reading the Analysis Report", titles.get(3));
                assertEquals("Ingestion Limits and Supported Files", titles.get(4));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("First-run seam with seen stub: overlay is hidden")
    void testFirstRunSeenGuideModal() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                store.put(SettingsService.KEY_ONBOARDING_SEEN, true);
                InMemorySettingsService settingsService = new InMemorySettingsService(store);
                assertTrue(settingsService.isOnboardingSeen());

                WorkspaceController controller = loadControllerWithSettings(settingsService);

                // Guide overlay should be hidden
                assertFalse(controller.isGuideVisible(), "Guide overlay must be hidden if already seen");
                assertNotNull(controller.getGuideOverlay());
                assertFalse(controller.getGuideOverlay().isVisible());
                assertFalse(controller.getGuideOverlay().isManaged());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Reopen seam: invoking handleOpenGuide or openGuide shows the overlay")
    void testReopenSeam() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                store.put(SettingsService.KEY_ONBOARDING_SEEN, true);
                InMemorySettingsService settingsService = new InMemorySettingsService(store);

                WorkspaceController controller = loadControllerWithSettings(settingsService);
                assertFalse(controller.isGuideVisible());

                // Reopen via openGuide()
                controller.openGuide();
                assertTrue(controller.isGuideVisible(), "Guide overlay should be visible after openGuide()");

                // Hide then reopen via handleOpenGuide
                controller.hideGuide();
                assertFalse(controller.isGuideVisible());
                controller.handleOpenGuide(new ActionEvent());
                assertTrue(controller.isGuideVisible(), "Guide overlay should be visible after handleOpenGuide()");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Dismiss seam: invoking handleDismissGuide or dismissGuideAndMarkSeen hides overlay and records onboardingSeen=true")
    void testDismissSeam() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                InMemorySettingsService settingsService = new InMemorySettingsService(store);
                assertFalse(settingsService.isOnboardingSeen());

                WorkspaceController controller = loadControllerWithSettings(settingsService);
                assertTrue(controller.isGuideVisible());

                // Dismiss via dismissGuideAndMarkSeen()
                controller.dismissGuideAndMarkSeen();
                assertFalse(controller.isGuideVisible(), "Guide overlay should be hidden after dismiss");
                assertTrue(settingsService.isOnboardingSeen(), "Settings store must record onboardingSeen=true");

                // Reopen and test handleDismissGuide
                controller.openGuide();
                assertTrue(controller.isGuideVisible());
                controller.handleDismissGuide(new ActionEvent());
                assertFalse(controller.isGuideVisible());
                assertTrue(settingsService.isOnboardingSeen());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Header button guideButton exists, has text Guide, and triggers opening guide")
    void testGuideButtonInHeader() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                store.put(SettingsService.KEY_ONBOARDING_SEEN, true);
                InMemorySettingsService settingsService = new InMemorySettingsService(store);

                WorkspaceController controller = loadControllerWithSettings(settingsService);
                assertNotNull(controller.getGuideButton(), "guideButton must be injected");
                assertEquals("Guide", controller.getGuideButton().getText());
                assertNotNull(controller.getGuideButton().getGraphic(), "guideButton should have vector graphic");
                assertTrue(controller.getGuideButton().getStyleClass().contains("btn-guide"));

                assertFalse(controller.isGuideVisible());
                controller.getGuideButton().fire();
                assertTrue(controller.isGuideVisible(), "Firing guideButton should open guide overlay");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Close and action buttons dismiss guide modal")
    void testGuideDismissButtons() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                InMemorySettingsService settingsService = new InMemorySettingsService(store);

                WorkspaceController controller = loadControllerWithSettings(settingsService);
                assertTrue(controller.isGuideVisible());

                assertNotNull(controller.getGuideCloseButton());
                controller.getGuideCloseButton().fire();
                assertFalse(controller.isGuideVisible());
                assertTrue(settingsService.isOnboardingSeen());

                controller.openGuide();
                assertTrue(controller.isGuideVisible());

                assertNotNull(controller.getGuideGotItButton());
                controller.getGuideGotItButton().fire();
                assertFalse(controller.isGuideVisible());
                assertTrue(settingsService.isOnboardingSeen());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Clicks on guideOverlay are consumed so they do not pass through")
    void testGuideOverlayClickConsumption() throws Exception {
        runOnFxThread(() -> {
            try {
                Map<String, Boolean> store = new HashMap<>();
                InMemorySettingsService settingsService = new InMemorySettingsService(store);
                WorkspaceController controller = loadControllerWithSettings(settingsService);

                assertNotNull(controller.getGuideOverlay().getOnMouseClicked(),
                        "guideOverlay must have an onMouseClicked handler registered");

                MouseEvent click = new MouseEvent(
                        MouseEvent.MOUSE_CLICKED,
                        0, 0, 0, 0,
                        MouseButton.PRIMARY, 1,
                        false, false, false, false,
                        false, false, false, false, false, false, null
                );
                controller.getGuideOverlay().getOnMouseClicked().handle(click);
                assertTrue(click.isConsumed(), "Click event on guideOverlay must be consumed");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
