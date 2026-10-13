package com.writely.syntax_analyzer.app.guide;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Programmatically renders {@link GuideDocument} models into JavaFX node hierarchies.
 */
public final class GuideRenderer {

    private GuideRenderer() {
    }

    /**
     * Renders a full {@link GuideDocument} into a list of section {@link Node}s.
     *
     * @param document the guide document to render
     * @return list of rendered nodes, each representing a section
     */
    public static List<Node> render(GuideDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        List<Node> nodes = new ArrayList<>();
        for (GuideSection section : document.sections()) {
            nodes.add(render(section));
        }
        return nodes;
    }

    /**
     * Renders a single {@link GuideSection} into a {@link VBox}.
     *
     * @param section the section to render
     * @return a VBox containing the section title and rendered blocks
     */
    public static Node render(GuideSection section) {
        Objects.requireNonNull(section, "section must not be null");
        VBox sectionBox = new VBox(6);
        sectionBox.getStyleClass().add("guide-section");

        Label titleLabel = new Label(section.title());
        titleLabel.getStyleClass().add("guide-section-title");
        titleLabel.setWrapText(true);
        sectionBox.getChildren().add(titleLabel);

        for (GuideBlock block : section.blocks()) {
            sectionBox.getChildren().add(renderBlock(block));
        }
        return sectionBox;
    }

    /**
     * Renders an individual {@link GuideBlock} into a JavaFX {@link Node}.
     *
     * @param block the block to render
     * @return rendered node
     */
    public static Node renderBlock(GuideBlock block) {
        Objects.requireNonNull(block, "block must not be null");
        return switch (block) {
            case ParagraphBlock p -> {
                Label label = new Label(p.text());
                label.getStyleClass().add("guide-paragraph");
                label.setWrapText(true);
                yield label;
            }
            case BulletListBlock b -> {
                VBox listBox = new VBox(4);
                listBox.getStyleClass().add("guide-bullet-list");
                for (String item : b.items()) {
                    Label itemLabel = new Label("•  " + item);
                    itemLabel.getStyleClass().add("guide-bullet-item");
                    itemLabel.setWrapText(true);
                    listBox.getChildren().add(itemLabel);
                }
                yield listBox;
            }
            case SnippetBlock s -> {
                Label snippetLabel = new Label(s.code());
                snippetLabel.getStyleClass().add("guide-snippet");
                snippetLabel.setWrapText(true);
                yield snippetLabel;
            }
        };
    }

    /**
     * Populates a JavaFX container with rendered document sections.
     *
     * @param document the guide document to render
     * @param container the target container
     */
    public static void renderTo(GuideDocument document, Pane container) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(container, "container must not be null");
        container.getChildren().setAll(render(document));
    }
}
