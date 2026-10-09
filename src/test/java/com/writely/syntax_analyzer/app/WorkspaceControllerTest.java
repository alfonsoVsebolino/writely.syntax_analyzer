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
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TreeItem;
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
}
