package com.writely.syntax_analyzer.app;

import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.core.ingestion.DefaultSourceIngestionService;
import com.writely.syntax_analyzer.core.report.ReportFormat;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceControllerTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            // Already initialized in test JVM
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
            assertEquals("PASSED", controller.getStatusBannerText());
            assertTrue(controller.getEmptyStateBanner().isVisible());
            assertTrue(controller.getEmptyStateBanner().isManaged());
            assertEquals(0, controller.getDiagnosticsCardCount());
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
            assertEquals("FAILED_SYNTAX_ERRORS", controller.getStatusBannerText());
            assertFalse(controller.getEmptyStateBanner().isVisible());
            assertEquals(2, controller.getDiagnosticsCardCount());

            VBox card1 = (VBox) controller.getDiagnosticsListContainer().getChildren().get(0);
            assertNotNull(card1);
            assertTrue(card1.getStyleClass().contains("diagnostic-card"));
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
    @DisplayName("Ingest file into selected folder adds file to that folder in tree")
    void testIngestFileIntoSelectedFolder(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("Helper.java");
        Files.writeString(testFile, "public class Helper {}");

        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            // Create new folder and ensure it is selected
            controller.handleNewFolder(null);
            TreeItem<String> newFolder = controller.getFileTreeView().getSelectionModel().getSelectedItem();
            assertNotNull(newFolder);
            assertTrue(newFolder.getValue().contains("Folder"));

            // Ingest file into this folder
            controller.ingestFile(testFile);

            assertEquals("Helper.java", controller.getCurrentFileName());
            assertTrue(newFolder.getChildren().stream().anyMatch(c -> c.getValue().equals("Helper.java")));
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
            assertNotNull(controller.getLatestAlert());
            assertEquals(Alert.AlertType.ERROR, controller.getLatestAlert().getAlertType());
            if (controller.getLatestAlert() != null) {
                controller.getLatestAlert().close();
            }
        });
    }

    @Test
    @DisplayName("Ingest binary file detects null byte, updates status label, and shows error alert")
    void testIngestBinaryFileDisplaysErrorAlert(@TempDir Path tempDir) throws Exception {
        Path binaryFile = tempDir.resolve("binary_code.py");
        Files.write(binaryFile, new byte[]{0x00, 'p', 'r', 'i', 'n', 't'});

        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            controller.ingestFile(binaryFile);
            assertTrue(controller.getFooterStatusLabel().getText().contains("Ingestion error:"));
            assertTrue(controller.getFooterStatusLabel().getText().contains("Binary file detected"));
            assertNotNull(controller.getLatestAlert());
            assertEquals(Alert.AlertType.ERROR, controller.getLatestAlert().getAlertType());
            assertTrue(controller.getLatestAlert().getContentText().contains("Binary file detected"));
            if (controller.getLatestAlert() != null) {
                controller.getLatestAlert().close();
            }
        });
    }

    @Test
    @DisplayName("Ingest oversized file exceeding 10MB updates status label and shows error alert")
    void testIngestOversizedFileDisplaysErrorAlert(@TempDir Path tempDir) throws Exception {
        Path largeFile = tempDir.resolve("large_source.java");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(largeFile.toFile(), "rw")) {
            raf.setLength(10L * 1024L * 1024L + 1L);
        }

        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            controller.ingestFile(largeFile);
            assertTrue(controller.getFooterStatusLabel().getText().contains("Ingestion error:"));
            assertTrue(controller.getFooterStatusLabel().getText().contains("File exceeds 10 MB limit"));
            assertNotNull(controller.getLatestAlert());
            assertEquals(Alert.AlertType.ERROR, controller.getLatestAlert().getAlertType());
            if (controller.getLatestAlert() != null) {
                controller.getLatestAlert().close();
            }
        });
    }

    @Test
    @DisplayName("Ingest file with unexpected service error handles error without crashing")
    void testIngestFileUnexpectedServiceErrorHandlesGracefully(@TempDir Path tempDir) throws Exception {
        Path testFile = tempDir.resolve("script.py");
        Files.writeString(testFile, "print('test')");

        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();
            controller.setIngestionService(new DefaultSourceIngestionService() {
                @Override
                public SourcePayload ingestFile(Path filePath, Language explicitLanguage) {
                    throw new RuntimeException("Simulated unexpected ingestion failure");
                }
            });

            controller.ingestFile(testFile);
            assertTrue(controller.getFooterStatusLabel().getText().contains("Ingestion error:"));
            assertNotNull(controller.getLatestAlert());
            assertEquals(Alert.AlertType.ERROR, controller.getLatestAlert().getAlertType());
            if (controller.getLatestAlert() != null) {
                controller.getLatestAlert().close();
            }
        });
    }

    @Test
    @DisplayName("Closing active tab enters empty state, clears editor buffer, and disables actions")
    void testClosingActiveTabEntersEmptyState() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            assertNotNull(controller.getActiveTab());
            assertNotNull(controller.getEditorTabPane());
            assertNotNull(controller.getEditorEmptyStatePane());
            assertFalse(controller.getDeepScanButton().isDisable());

            // Close the active tab
            controller.handleActiveTabClosed();

            assertTrue(controller.getEditorEmptyStatePane().isVisible());
            assertTrue(controller.getEditorEmptyStatePane().isManaged());
            assertFalse(controller.getEditorTabPane().isVisible());
            assertTrue(controller.getEditorTabPane().getTabs().isEmpty());
            assertTrue(controller.getDeepScanButton().isDisable());
            assertEquals("No active file", controller.getFooterStatusLabel().getText());
            assertEquals("", controller.getEditorText());
            assertEquals("", controller.getLineGutterArea().getText());
            assertNull(controller.getCurrentFileName());
        });
    }

    @Test
    @DisplayName("Calling loadSourceCode or selecting explorer file when in empty state re-attaches activeTab and re-enables scan")
    void testReopeningFileFromEmptyStateReattachesTabAndEnablesScan() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            // 1. Enter empty state
            controller.handleActiveTabClosed();
            assertTrue(controller.getEditorEmptyStatePane().isVisible());
            assertTrue(controller.getDeepScanButton().isDisable());

            // 2. Load code / re-ingest
            String code = "x = 42\nprint(x)\n";
            controller.loadSourceCode(code, "script.py", Language.PYTHON);

            assertFalse(controller.getEditorEmptyStatePane().isVisible());
            assertFalse(controller.getEditorEmptyStatePane().isManaged());
            assertTrue(controller.getEditorTabPane().isVisible());
            assertEquals(1, controller.getEditorTabPane().getTabs().size());
            assertNotNull(controller.getActiveTab());
            assertEquals("script.py", controller.getActiveTab().getText());
            assertEquals(code, controller.getEditorText());
            assertFalse(controller.getDeepScanButton().isDisable());
            assertEquals("Loaded script.py", controller.getFooterStatusLabel().getText());

            // 3. Re-test reopening from tree view file click when in empty state
            controller.handleActiveTabClosed();
            assertTrue(controller.getEditorEmptyStatePane().isVisible());
            assertTrue(controller.getDeepScanButton().isDisable());

            controller.getFileTreeView().getSelectionModel().clearSelection();
            TreeItem<String> projectFolder = controller.getFileTreeView().getRoot().getChildren().get(0);
            TreeItem<String> pyFile = projectFolder.getChildren().get(0);
            controller.getFileTreeView().getSelectionModel().select(pyFile);

            assertFalse(controller.getEditorEmptyStatePane().isVisible());
            assertTrue(controller.getEditorTabPane().isVisible());
            assertFalse(controller.getDeepScanButton().isDisable());
            assertEquals("filename.py", controller.getCurrentFileName());
        });
    }

    @Test
    @DisplayName("Tokens table is populated with tokens from analysis result")
    void testTokensTablePopulatedFromAnalysisResult() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Token t1 = Token.of(TokenType.KEYWORD, "def", SourceSpan.of(1, 1, 0, 1, 4, 3));
            Token t2 = Token.of(TokenType.IDENTIFIER, "foo", SourceSpan.of(1, 5, 4, 1, 8, 7));
            Token t3 = Token.of(TokenType.WHITESPACE, " ", SourceSpan.of(1, 4, 3, 1, 5, 4));

            SourcePayload payload = SourcePayload.snippet("def foo", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(t1, t2, t3), List.of());

            controller.applyAnalysisResult(result);

            assertEquals(3, controller.getTokensTableView().getItems().size());
            assertEquals(3, controller.getMasterTokensList().size());
            assertEquals(t1, controller.getTokensTableView().getItems().get(0));
            assertEquals(t2, controller.getTokensTableView().getItems().get(1));
            assertEquals(t3, controller.getTokensTableView().getItems().get(2));
        });
    }

    @Test
    @DisplayName("Token search filter dynamically filters tokens in tokens table")
    void testTokenFiltering() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Token t1 = Token.of(TokenType.KEYWORD, "def", SourceSpan.of(1, 1, 0, 1, 4, 3));
            Token t2 = Token.of(TokenType.IDENTIFIER, "calculate", SourceSpan.of(1, 5, 4, 1, 14, 13));
            Token t3 = Token.of(TokenType.WHITESPACE, " ", SourceSpan.of(1, 4, 3, 1, 5, 4));

            SourcePayload payload = SourcePayload.snippet("def calculate", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(t1, t2, t3), List.of());

            controller.applyAnalysisResult(result);
            assertEquals(3, controller.getTokensTableView().getItems().size());

            // Filter by lexeme
            controller.filterTokens("calculate");
            assertEquals(1, controller.getTokensTableView().getItems().size());
            assertEquals("calculate", controller.getTokensTableView().getItems().get(0).lexeme());

            // Filter by channel
            controller.filterTokens("trivia");
            assertEquals(1, controller.getTokensTableView().getItems().size());
            assertEquals(TokenType.WHITESPACE, controller.getTokensTableView().getItems().get(0).tokenType());

            // Filter by type
            controller.filterTokens("KEYWORD");
            assertEquals(1, controller.getTokensTableView().getItems().size());
            assertEquals("def", controller.getTokensTableView().getItems().get(0).lexeme());

            // Clear filter
            controller.filterTokens("");
            assertEquals(3, controller.getTokensTableView().getItems().size());
        });
    }

    @Test
    @DisplayName("Token row click positions editor caret at token start location and selects span")
    void testTokenClickJumpsEditorCaret() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            String sample = "def greet():\n    return 'hello'";
            controller.setEditorText(sample);

            Token token = Token.of(TokenType.KEYWORD, "return", SourceSpan.of(2, 5, 17, 2, 11, 23));
            controller.selectToken(token);

            assertEquals("return", controller.getEditorTextArea().getSelectedText());
            assertEquals(17, controller.getEditorTextArea().getSelection().getStart());
            assertEquals(23, controller.getEditorTextArea().getSelection().getEnd());
        });
    }

    @Test
    @DisplayName("AST TreeView builds hierarchy and supports expand/collapse when rootNode is present")
    void testAstTreeViewHierarchyWhenRootNodePresent() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            String code = "x = 42";
            controller.setEditorText(code);

            SyntaxNode child = SyntaxNode.leaf("Literal", "42", SourceSpan.of(1, 5, 4, 1, 7, 6));
            SyntaxNode root = SyntaxNode.of("Assignment", "=", SourceSpan.of(1, 1, 0, 1, 7, 6), List.of(child));

            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), root, List.of());

            controller.applyAnalysisResult(result);

            assertFalse(controller.getAstEmptyStateLabel().isVisible());
            assertTrue(controller.getAstTreeView().isVisible());
            assertNotNull(controller.getAstTreeView().getRoot());
            assertEquals("Assignment", controller.getAstTreeView().getRoot().getValue().kind());
            assertEquals(1, controller.getAstTreeView().getRoot().getChildren().size());
            assertEquals("Literal", controller.getAstTreeView().getRoot().getChildren().get(0).getValue().kind());

            // Expand and collapse actions
            controller.collapseAllAst();
            assertFalse(controller.getAstTreeView().getRoot().isExpanded());

            controller.expandAllAst();
            assertTrue(controller.getAstTreeView().getRoot().isExpanded());

            // Selecting AST node selects span in editor
            controller.selectAstNode(child);
            assertEquals("42", controller.getEditorTextArea().getSelectedText());
        });
    }

    @Test
    @DisplayName("AST TreeView displays empty state banner when rootNode is empty")
    void testAstTreeViewDisplaysEmptyStateWhenRootNodeEmpty() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Diagnostic err = Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                SourceLocation.of(1, 1, 0),
                "Syntax error",
                "ERR_SYNTAX_01"
            );
            SourcePayload payload = SourcePayload.snippet("invalid {{", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(err));

            controller.applyAnalysisResult(result);

            assertTrue(controller.getAstEmptyStateLabel().isVisible());
            assertTrue(controller.getAstEmptyStateLabel().isManaged());
            assertEquals(
                "Syntax Tree unavailable due to syntax errors. Fix diagnostics in the Insights tab to generate complete AST.",
                controller.getAstEmptyStateLabel().getText()
            );
            assertFalse(controller.getAstTreeView().isVisible());
        });
    }

    @Test
    @DisplayName("Status banner updates to PASSED on valid code and FAILED_SYNTAX_ERRORS on syntax errors")
    void testStatusBannerUpdatesToPassedAndFailed() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            // 1. Valid code -> PASSED
            SourcePayload validPayload = SourcePayload.snippet("x = 10\ny = 20\n", Language.PYTHON);
            AnalysisResult passedResult = AnalysisResult.of(validPayload, List.of(), List.of());
            controller.applyAnalysisResult(passedResult);

            assertEquals("PASSED", controller.getStatusBannerText());
            assertTrue(controller.getStatusBanner().getStyleClass().contains("status-banner-passed"));
            assertFalse(controller.getStatusBanner().getStyleClass().contains("status-banner-failed"));
            if (controller.getStatusBannerContainer() != null) {
                assertTrue(controller.getStatusBannerContainer().getStyleClass().contains("status-banner-passed"));
            }

            // 2. Syntax errors -> FAILED_SYNTAX_ERRORS
            Diagnostic err = Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                SourceLocation.of(1, 1),
                "Missing closing delimiter",
                "ERR_DELIM"
            );
            AnalysisResult failedResult = AnalysisResult.of(validPayload, List.of(), List.of(err));
            controller.applyAnalysisResult(failedResult);

            assertEquals("FAILED_SYNTAX_ERRORS", controller.getStatusBannerText());
            assertTrue(controller.getStatusBanner().getStyleClass().contains("status-banner-failed"));
            assertFalse(controller.getStatusBanner().getStyleClass().contains("status-banner-passed"));
            if (controller.getStatusBannerContainer() != null) {
                assertTrue(controller.getStatusBannerContainer().getStyleClass().contains("status-banner-failed"));
            }
        });
    }

    @Test
    @DisplayName("Summary metrics populate matching DiagnosticSummary")
    void testSummaryMetricsPopulateMatchingDiagnosticSummary() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            String code = "line 1\nline 2\nline 3\nline 4\nline 5";
            SourcePayload payload = SourcePayload.snippet(code, Language.PYTHON);

            Diagnostic d1 = Diagnostic.error(CheckCategory.STATEMENT_TERMINATOR, SourceLocation.of(2, 5), "Missing colon", "ERR_TERM");
            Diagnostic d2 = Diagnostic.warning(CheckCategory.IDENTIFIER_NAMING, SourceLocation.of(4, 1), "Bad variable name", "WARN_NAME");

            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(d1, d2));

            controller.applyAnalysisResult(result);

            assertEquals("5", controller.getTotalLinesText());
            assertEquals("3", controller.getValidLinesText());
            assertEquals("2", controller.getFlaggedLinesText());
            assertEquals("2", controller.getTotalIssuesText());
            assertEquals("1", controller.getErrorCountLabel().getText());
            assertEquals("1", controller.getWarningCountLabel().getText());
        });
    }

    @Test
    @DisplayName("Category breakdown badges appear for active error categories")
    void testCategoryBreakdownBadgesAppearForActiveCategories() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Diagnostic d1 = Diagnostic.error(CheckCategory.DELIMITER_MATCH, SourceLocation.of(1, 1), "Error 1", "E1");
            Diagnostic d2 = Diagnostic.error(CheckCategory.DELIMITER_MATCH, SourceLocation.of(2, 1), "Error 2", "E2");
            Diagnostic d3 = Diagnostic.warning(CheckCategory.LITERAL_SYNTAX, SourceLocation.of(3, 1), "Warning 1", "W1");

            SourcePayload payload = SourcePayload.snippet("a\nb\nc", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(d1, d2, d3));

            controller.applyAnalysisResult(result);

            FlowPane container = controller.getCategoryBreakdownContainer();
            assertNotNull(container);
            assertEquals(2, container.getChildren().size());

            boolean foundDelimiter = false;
            boolean foundLiteral = false;
            for (var node : container.getChildren()) {
                assertTrue(node instanceof Label);
                Label label = (Label) node;
                assertTrue(label.getStyleClass().contains("category-chip"));
                if (label.getText().contains("DELIMITER_MATCH: 2")) {
                    foundDelimiter = true;
                }
                if (label.getText().contains("LITERAL_SYNTAX: 1")) {
                    foundLiteral = true;
                }
            }
            assertTrue(foundDelimiter, "DELIMITER_MATCH: 2 pill must be rendered");
            assertTrue(foundLiteral, "LITERAL_SYNTAX: 1 pill must be rendered");
        });
    }

    @Test
    @DisplayName("Diagnostic cards populate with correct codes, locations, categories, and messages")
    void testDiagnosticCardsPopulateWithCorrectMetadata() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Diagnostic diag = Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                SourceLocation.of(7, 14),
                "Missing colon after if condition",
                "ERR_CTRL_01",
                "Add ':' at end of line"
            );

            SourcePayload payload = SourcePayload.snippet("if True\n    pass", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(diag));

            controller.applyAnalysisResult(result);

            assertEquals(1, controller.getDiagnosticsCardCount());
            VBox card = (VBox) controller.getDiagnosticsListContainer().getChildren().get(0);
            assertNotNull(card);
            assertTrue(card.getStyleClass().contains("diagnostic-card"));

            // Find child labels
            FlowPane header = (FlowPane) card.getChildren().get(0);
            Label severityBadge = (Label) header.getChildren().get(0);
            assertEquals("ERROR", severityBadge.getText());
            assertTrue(severityBadge.getStyleClass().contains("badge-error"));

            Label codeBadge = (Label) header.getChildren().get(1);
            assertEquals("ERR_CTRL_01", codeBadge.getText());
            assertTrue(codeBadge.getStyleClass().contains("code-badge"));

            Label locBadge = (Label) header.getChildren().get(2);
            assertEquals("Line 7, Col 14", locBadge.getText());
            assertTrue(locBadge.getStyleClass().contains("loc-badge"));

            Label categoryBadge = (Label) header.getChildren().get(3);
            assertEquals("CONTROL_HEADER", categoryBadge.getText());

            Label messageLabel = (Label) card.getChildren().get(1);
            assertEquals("Missing colon after if condition", messageLabel.getText());

            Label fixLabel = (Label) card.getChildren().get(2);
            assertTrue(fixLabel.getText().contains("Add ':' at end of line"));
        });
    }

    @Test
    @DisplayName("Clicking a diagnostic card jumps caret and selects text in codeEditorArea")
    void testClickingDiagnosticCardJumpsCaretAndSelectsText() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            String source = "def calculate():\n    return 42 + invalid\n";
            controller.setEditorText(source);

            Diagnostic diag = Diagnostic.error(
                CheckCategory.OPERATOR_SYNTAX,
                SourceLocation.of(2, 5),
                "Invalid operator syntax",
                "ERR_OP_01"
            );

            SourcePayload payload = SourcePayload.snippet(source, Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(diag));

            controller.applyAnalysisResult(result);

            assertEquals(1, controller.getDiagnosticsCardCount());
            VBox card = (VBox) controller.getDiagnosticsListContainer().getChildren().get(0);

            // Simulate clicking the diagnostic card
            card.getOnMouseClicked().handle(null);

            // Target offset for line 2, col 5 in "def calculate():\n    return 42 + invalid\n"
            // Line 1: length 17 ('\n' is index 16)
            // Line 2 starts at 17. Col 5 is 17 + 4 = 21.
            assertEquals(21, controller.getEditorTextArea().getCaretPosition());
            assertFalse(controller.getEditorTextArea().getSelectedText().isEmpty());
            assertTrue(controller.getEditorTextArea().getSelectedText().contains("return"));
        });
    }

    @Test
    @DisplayName("Clean empty state banner is displayed when analysis produces 0 errors")
    void testEmptyStateBannerDisplayedWhenZeroErrors() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            SourcePayload payload = SourcePayload.snippet("x = 10", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of());

            controller.applyAnalysisResult(result);

            assertTrue(controller.getEmptyStateBanner().isVisible());
            assertTrue(controller.getEmptyStateBanner().isManaged());
            assertEquals("No syntax issues detected. Code is syntactically valid.", controller.getEmptyStateBannerText());
            assertFalse(controller.getDiagnosticsScrollPane().isVisible());
            assertEquals(0, controller.getDiagnosticsCardCount());
        });
    }

    @Test
    @DisplayName("Export buttons disabled initially and enabled after analysis result")
    void testExportButtonsBindingAndState() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            assertNotNull(controller.getExportTextButton());
            assertNotNull(controller.getExportJsonButton());
            assertTrue(controller.getExportTextButton().isDisable());
            assertTrue(controller.getExportJsonButton().isDisable());

            SourcePayload payload = SourcePayload.snippet("x = 10", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of());
            controller.applyAnalysisResult(result);

            assertFalse(controller.getExportTextButton().isDisable());
            assertFalse(controller.getExportJsonButton().isDisable());
        });
    }

    @Test
    @DisplayName("exportReport writes formatted text and JSON reports to disk")
    void testExportReportWritesFilesToDisk(@TempDir Path tempDir) throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Diagnostic err = Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                SourceLocation.of(1, 1),
                "Missing delimiter",
                "ERR_DELIM_01"
            );
            SourcePayload payload = SourcePayload.file("x = 10", "test.py", Language.PYTHON);
            AnalysisResult result = AnalysisResult.of(payload, List.of(), List.of(err));
            controller.applyAnalysisResult(result);

            Path txtOut = tempDir.resolve("report.txt");
            controller.exportReport(ReportFormat.TEXT, txtOut);
            assertTrue(Files.exists(txtOut));
            try {
                String txtContent = Files.readString(txtOut);
                assertTrue(txtContent.contains("SYNTACTICAL ANALYSIS REPORT"));
                assertTrue(txtContent.contains("Missing delimiter"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Path jsonOut = tempDir.resolve("report.json");
            controller.exportReport(ReportFormat.JSON, jsonOut);
            assertTrue(Files.exists(jsonOut));
            try {
                String jsonContent = Files.readString(jsonOut);
                assertTrue(jsonContent.contains("\"language\": \"PYTHON\""));
                assertTrue(jsonContent.contains("\"ERR_DELIM_01\""));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("workspaceSplitPane divider positions initialized to [0.20, 0.55]")
    void testWorkspaceSplitPaneInitialization() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            assertNotNull(controller.getWorkspaceSplitPane());
            assertEquals(3, controller.getWorkspaceSplitPane().getItems().size());
            assertEquals(controller.getExplorerPane(), controller.getWorkspaceSplitPane().getItems().get(0));
            assertEquals(controller.getEditorPane(), controller.getWorkspaceSplitPane().getItems().get(1));
            assertEquals(controller.getRightDockPane(), controller.getWorkspaceSplitPane().getItems().get(2));

            double[] positions = controller.getWorkspaceSplitPane().getDividerPositions();
            assertEquals(2, positions.length);
            assertEquals(0.20, positions[0], 0.01);
            assertEquals(0.55, positions[1], 0.01);
        });
    }

    @Test
    @DisplayName("Diagnostic cards use FlowPane headers and wrap badges")
    void testDiagnosticCardsUseFlowPaneHeaderAndWrapBadges() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            Diagnostic diag = Diagnostic.error(
                CheckCategory.DELIMITER_MATCH,
                SourceLocation.of(1, 1),
                "Unmatched paren",
                "ERR_PAREN_01"
            );
            VBox card = controller.createDiagnosticCard(diag);
            assertNotNull(card);
            assertTrue(card.getChildren().get(0) instanceof FlowPane);

            FlowPane header = (FlowPane) card.getChildren().get(0);
            assertTrue(header.getStyleClass().contains("card-header-flow"));
            assertEquals(6.0, header.getHgap());
            assertEquals(4.0, header.getVgap());
            assertEquals(4, header.getChildren().size());
        });
    }

    @Test
    @DisplayName("Insights tab content is wrapped in insightsScrollPane")
    void testInsightsScrollPaneExists() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-workspace.fxml"));
            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            WorkspaceController controller = loader.getController();

            assertNotNull(controller.getInsightsScrollPane());
            assertTrue(controller.getInsightsScrollPane().isFitToWidth());
            assertFalse(controller.getInsightsScrollPane().isFitToHeight());
            assertTrue(controller.getInsightsScrollPane().getStyleClass().contains("tab-scroll-pane"));
        });
    }
}
