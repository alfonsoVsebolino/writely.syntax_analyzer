package com.writely.syntax_analyzer.regression;

import com.writely.syntax_analyzer.app.WorkspaceController;
import com.writely.syntax_analyzer.core.analysis.AnalysisOrchestrator;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Diagnostic Deduplication and Friendly Formatting Reproduction Test")
public class DiagnosticReproductionTest {

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
        assertTrue(latch.await(5, TimeUnit.SECONDS));
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
        future.get(10, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Repro Bug 1: Java missing semicolon should produce exactly 1 diagnostic, not duplicate cards")
    void testJavaMissingSemicolonNoDuplicates() {
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator();
        String code = "public class Foo { void test() { int a = 10\n int b = 20; } }";
        SourcePayload payload = SourcePayload.snippet(code, Language.JAVA);
        AnalysisResult result = orchestrator.analyze(payload);

        List<Diagnostic> diags = result.diagnostics();
        assertEquals(1, diags.size(), () -> "Expected 1 diagnostic but got: " + diags);
        assertEquals("ERR_MISSING_SEMICOLON", diags.get(0).code());
    }

    @Test
    @DisplayName("Repro Bug 1: Cpp missing semicolon should produce exactly 1 diagnostic, not duplicate cards")
    void testCppMissingSemicolonNoDuplicates() {
        AnalysisOrchestrator orchestrator = new AnalysisOrchestrator();
        String code = "int main() { int a = 10\n return 0; }";
        SourcePayload payload = SourcePayload.snippet(code, Language.CPP);
        AnalysisResult result = orchestrator.analyze(payload);

        List<Diagnostic> diags = result.diagnostics();
        assertEquals(1, diags.size(), () -> "Expected 1 diagnostic but got: " + diags);
        assertEquals("ERR_MISSING_SEMICOLON", diags.get(0).code());
    }

    @Test
    @DisplayName("Repro Bug 2: Diagnostic card badge displays friendly slug without underscores")
    void testDiagnosticCardFriendlySlug() throws Exception {
        runOnFxThread(() -> {
            WorkspaceController controller = new WorkspaceController();
            Diagnostic diag = Diagnostic.error(
                CheckCategory.STATEMENT_TERMINATOR,
                SourceLocation.of(1, 10),
                "Missing semicolon",
                "ERR_JAVA_MISSING_SEMICOLON"
            );
            VBox card = controller.createDiagnosticCard(diag);
            FlowPane header = (FlowPane) card.getChildren().get(0);
            Label codeBadge = (Label) header.getChildren().get(1);

            assertEquals("Java Missing Semicolon", codeBadge.getText());
        });
    }
}
