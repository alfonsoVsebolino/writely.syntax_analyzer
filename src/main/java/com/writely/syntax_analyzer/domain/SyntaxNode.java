package com.writely.syntax_analyzer.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Immutable representation of a node in a syntax/parse tree.
 */
public record SyntaxNode(
    String kind,
    String label,
    SourceSpan span,
    List<SyntaxNode> children,
    Map<String, String> attributes
) {
    public SyntaxNode {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(span, "span must not be null");
        children = children == null ? List.of() : List.copyOf(children);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static SyntaxNode leaf(String kind, String label, SourceSpan span) {
        return new SyntaxNode(kind, label, span, List.of(), Map.of());
    }

    public static SyntaxNode leaf(String kind, String label, SourceSpan span, Map<String, String> attributes) {
        return new SyntaxNode(kind, label, span, List.of(), attributes);
    }

    public static SyntaxNode of(String kind, String label, SourceSpan span) {
        return new SyntaxNode(kind, label, span, List.of(), Map.of());
    }

    public static SyntaxNode of(String kind, String label, SourceSpan span, List<SyntaxNode> children) {
        return new SyntaxNode(kind, label, span, children, Map.of());
    }

    public static SyntaxNode of(
        String kind,
        String label,
        SourceSpan span,
        List<SyntaxNode> children,
        Map<String, String> attributes
    ) {
        return new SyntaxNode(kind, label, span, children, attributes);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public int childCount() {
        return children.size();
    }

    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    /**
     * Traverses this node and its descendants in pre-order.
     */
    public void walkPreorder(Consumer<SyntaxNode> visitor) {
        Objects.requireNonNull(visitor, "visitor must not be null");
        visitor.accept(this);
        for (SyntaxNode child : children) {
            child.walkPreorder(visitor);
        }
    }
}
