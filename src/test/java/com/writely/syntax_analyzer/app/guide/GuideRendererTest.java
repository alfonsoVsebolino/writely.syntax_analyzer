package com.writely.syntax_analyzer.app.guide;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRendererTest {

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

    @Test
    @DisplayName("GuideRenderer renders full document with correct node count and style classes")
    void testRenderDocument() {
        GuideDocument document = OnboardingGuideContent.getDocument();
        List<Node> renderedNodes = GuideRenderer.render(document);

        assertNotNull(renderedNodes);
        assertEquals(5, renderedNodes.size(), "Should produce 5 section nodes");

        for (int i = 0; i < renderedNodes.size(); i++) {
            Node node = renderedNodes.get(i);
            assertTrue(node instanceof VBox, "Each rendered section should be a VBox");
            VBox box = (VBox) node;
            assertTrue(box.getStyleClass().contains("guide-section"));

            Node firstChild = box.getChildren().get(0);
            assertTrue(firstChild instanceof Label, "First child should be title label");
            Label titleLabel = (Label) firstChild;
            assertTrue(titleLabel.getStyleClass().contains("guide-section-title"));
            assertEquals(document.sections().get(i).title(), titleLabel.getText());
        }
    }

    @Test
    @DisplayName("GuideRenderer renders paragraphs with guide-paragraph style class")
    void testRenderParagraph() {
        ParagraphBlock paragraph = new ParagraphBlock("Sample test paragraph");
        Node node = GuideRenderer.renderBlock(paragraph);

        assertTrue(node instanceof Label);
        Label label = (Label) node;
        assertEquals("Sample test paragraph", label.getText());
        assertTrue(label.getStyleClass().contains("guide-paragraph"));
        assertTrue(label.isWrapText());
    }

    @Test
    @DisplayName("GuideRenderer renders bullet lists with guide-bullet-item style class")
    void testRenderBulletList() {
        BulletListBlock bulletList = new BulletListBlock(List.of("Item One", "Item Two"));
        Node node = GuideRenderer.renderBlock(bulletList);

        assertTrue(node instanceof VBox);
        VBox box = (VBox) node;
        assertTrue(box.getStyleClass().contains("guide-bullet-list"));
        assertEquals(2, box.getChildren().size());

        for (int i = 0; i < box.getChildren().size(); i++) {
            Node itemNode = box.getChildren().get(i);
            assertTrue(itemNode instanceof Label);
            Label itemLabel = (Label) itemNode;
            assertTrue(itemLabel.getStyleClass().contains("guide-bullet-item"));
            assertTrue(itemLabel.getText().contains(bulletList.items().get(i)));
            assertTrue(itemLabel.isWrapText());
        }
    }

    @Test
    @DisplayName("GuideRenderer renders snippets with guide-snippet style class")
    void testRenderSnippet() {
        SnippetBlock snippet = new SnippetBlock("HelloWorld(print)");
        Node node = GuideRenderer.renderBlock(snippet);

        assertTrue(node instanceof Label);
        Label label = (Label) node;
        assertEquals("HelloWorld(print)", label.getText());
        assertTrue(label.getStyleClass().contains("guide-snippet"));
    }

    @Test
    @DisplayName("GuideRenderer rejects null arguments")
    void testNullChecks() {
        assertThrows(NullPointerException.class, () -> GuideRenderer.render((GuideDocument) null));
        assertThrows(NullPointerException.class, () -> GuideRenderer.render((GuideSection) null));
        assertThrows(NullPointerException.class, () -> GuideRenderer.renderBlock(null));
        assertThrows(NullPointerException.class, () -> GuideRenderer.renderTo(null, new VBox()));
        assertThrows(NullPointerException.class, () -> GuideRenderer.renderTo(OnboardingGuideContent.getDocument(), null));
    }
}
