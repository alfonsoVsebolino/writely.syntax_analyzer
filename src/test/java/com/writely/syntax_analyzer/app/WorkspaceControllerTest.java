package com.writely.syntax_analyzer.app;

import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.ingestion.DefaultSourceIngestionService;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceControllerTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Already initialized in test JVM
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
        future.get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("FXML loader initializes WorkspaceController and binds all GUI components")
    void testFxmlLoadingAndInitialization() throws Exception {
        runOnFxThread(() -> {
            try {
                URL fxmlUrl = getClass().getResource("/fxml/main-workspace.fxml");
                assertNotNull(fxmlUrl, "FXML resource /fxml/main-workspace.fxml must exist");

                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                assertNotNull(root, "Root node must not be null");

                WorkspaceController controller = loader.getController();
                assertNotNull(controller, "Controller must be injected");

                assertEquals("filename.py", controller.getCurrentFileName());
                assertEquals(Language.PYTHON, controller.getCurrentLanguage());
                assertFalse(controller.getEditorText().isBlank(), "Default python sample code should be present");
                assertEquals("IDLE", controller.getStatusBadgeText());
                assertEquals("0", controller.getTotalIssuesText());
                assertNotNull(controller.getLineGutterArea());
                assertFalse(controller.getLineGutterArea().getText().isBlank(), "Line gutter must show numbers");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("Workspace CSS loads without syntax errors")
    void testWorkspaceCssLoads() throws Exception {
        runOnFxThread(() -> {
            URL cssUrl = getClass().getResource("/css/workspace.css");
            assertNotNull(cssUrl, "CSS resource /css/workspace.css must exist");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            Parent root;
            try {
                root = loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Scene scene = new Scene(root);
            scene.getStylesheets().add(cssUrl.toExternalForm());
            assertFalse(scene.getStylesheets().isEmpty());
        });
    }

    @Test
    @DisplayName("loadSourceCode updates editor content, language, filename, and line gutter")
    void testLoadSourceCode() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            String sampleJava = "public class Hello {\n    public static void main(String[] args) {\n    }\n}";
            controller.loadSourceCode(sampleJava, "Hello.java", Language.JAVA);

            assertEquals("Hello.java", controller.getCurrentFileName());
            assertEquals(Language.JAVA, controller.getCurrentLanguage());
            assertEquals(sampleJava, controller.getEditorText());
            assertEquals("1\n2\n3\n4\n", controller.getLineGutterArea().getText());
        });
    }

    @Test
    @DisplayName("Line gutter computes accurate line numbers")
    void testLineGutterComputation() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            controller.updateLineGutter("line 1\nline 2\nline 3");
            assertEquals("1\n2\n3\n", controller.getLineGutterArea().getText());

            controller.updateLineGutter("single line");
            assertEquals("1\n", controller.getLineGutterArea().getText());
        });
    }

    @Test
    @DisplayName("applyAnalysisResult updates metric cards and diagnostics list on success")
    void testApplyAnalysisResultPassed() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            SourcePayload payload = SourcePayload.snippet("print('clean')", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of());

            controller.applyAnalysisResult(result);

            assertEquals("0", controller.getTotalIssuesText());
            assertEquals("PASSED", controller.getStatusBadgeText());
            assertEquals(1, controller.getDiagnosticsCardCount(), "Should show clean placeholder card");
        });
    }

    @Test
    @DisplayName("applyAnalysisResult renders diagnostic cards with badges on errors")
    void testApplyAnalysisResultWithErrors() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Diagnostic d1 = Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                SourceLocation.of(12, 5),
                "Syntax Error: Unexpected token '}'",
                "ERR_DELIM_01",
                "Remove unexpected token"
            );
            Diagnostic d2 = Diagnostic.warning(
                CheckCategory.LITERAL_SYNTAX,
                SourceLocation.of(34, 1),
                "Unterminated string literal",
                "WARN_LIT_02"
            );

            SourcePayload payload = SourcePayload.snippet("line1\nline2", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(d1, d2));

            controller.applyAnalysisResult(result);

            assertEquals("2", controller.getTotalIssuesText());
            assertEquals("FAILED_SYNTAX_ERRORS", controller.getStatusBadgeText());
            assertEquals(2, controller.getDiagnosticsCardCount());

            HBox card1 = (HBox) controller.getDiagnosticsListContainer().getChildren().get(0);
            assertNotNull(card1);
            assertTrue(card1.getStyleClass().contains("diagnostic-item"));
        });
    }

    @Test
    @DisplayName("ingestFile loads local source file and triggers analysis")
    void testIngestFile(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("Calculation.py");
        Files.writeString(testFile, "def add(a, b):\n    return a + b\n");

        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            controller.ingestFile(testFile);

            assertEquals("Calculation.py", controller.getCurrentFileName());
            assertEquals(Language.PYTHON, controller.getCurrentLanguage());
            assertTrue(controller.getEditorText().contains("def add(a, b):"));
        });
    }

    @Test
    @DisplayName("runAnalysis executes asynchronously and populates latestResult")
    void testAsyncDeepScanWorkflow() throws Exception {
        CompletableFuture<AnalysisResult> taskFuture = new CompletableFuture<>();

        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();
            controller.setEditorText("int x = 10;");
            controller.setCurrentLanguage(Language.JAVA);
            controller.setCurrentFileName("Test.java");

            Task<AnalysisResult> task = controller.runAnalysis();
            assertNotNull(task);

            task.setOnSucceeded(e -> taskFuture.complete(task.getValue()));
            task.setOnFailed(e -> taskFuture.completeExceptionally(task.getException()));
        });

        AnalysisResult result = taskFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(Language.JAVA, result.payload().language());
    }

    @Test
    @DisplayName("jumpToLocation positions caret in editor text")
    void testJumpToLocation() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();
            controller.setEditorText("line 1\nline 2\nline 3\n");

            controller.jumpToLocation(SourceLocation.of(2, 3));
            assertEquals(9, controller.getEditorTextArea().getCaretPosition());
        });
    }

    @Test
    @DisplayName("WorkspaceController constructor with dependencies initializes correctly")
    void testCustomConstructor() {
        DefaultSourceIngestionService ingestionService = new DefaultSourceIngestionService();
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator();
        WorkspaceController controller = new WorkspaceController(ingestionService, orchestrator);

        assertEquals(ingestionService, controller.getIngestionService());
        assertEquals(orchestrator, controller.getOrchestrator());
    }

    @Test
    @DisplayName("Search field filters tree items and resets on clear")
    void testTreeFilteringAndReset() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            assertNotNull(controller.getFileTreeView());
            assertNotNull(controller.getSearchField());

            // Type filter query
            controller.getSearchField().setText("Main");
            assertNotNull(controller.getFileTreeView().getRoot());
            assertEquals("Filtered", controller.getFileTreeView().getRoot().getValue());

            // Clear filter query
            controller.getSearchField().setText("");
            assertEquals("Workspace", controller.getFileTreeView().getRoot().getValue());
        });
    }

    @Test
    @DisplayName("New Folder action appends folder to tree")
    void testNewFolderAction() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            int initialCount = controller.getFileTreeView().getRoot().getChildren().size();
            controller.handleNewFolder(null);

            assertEquals(initialCount + 1, controller.getFileTreeView().getRoot().getChildren().size());
            assertTrue(controller.getFooterStatusLabel().getText().contains("Created Folder"));
        });
    }

    @Test
    @DisplayName("Ingest missing file handles error gracefully without throwing")
    void testIngestFileNotFoundGracefulHandling() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            controller.ingestFile(Path.of("/non/existent/file.py"));
            assertTrue(controller.getFooterStatusLabel().getText().contains("Ingestion error:"));
        });
    }

    @Test
    @DisplayName("Closing active tab recreates a blank untitled session")
    void testTabClosingRecreatesEmptySession() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            controller.getEditorTabPane().getTabs().clear();
            // Trigger closed handler
            controller.getActiveTab().getOnClosed().handle(null);

            assertEquals(1, controller.getEditorTabPane().getTabs().size());
            assertEquals("untitled.py", controller.getCurrentFileName());
            assertEquals("", controller.getEditorText());
        });
    }
}
