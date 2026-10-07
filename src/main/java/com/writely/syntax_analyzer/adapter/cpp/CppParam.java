package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a formal parameter in a C++ function or method declaration.
 */
public record CppParam(
    String type,
    String name,
    String defaultValue,
    SourceSpan span
) implements CppAstNode {

    public CppParam {
        Objects.requireNonNull(type, "type must not be null");
        name = name == null ? "" : name;
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        String label = name.isBlank() ? type : type + " " + name;
        Map<String, String> attrs = new HashMap<>();
        attrs.put("type", type);
        if (!name.isBlank()) {
            attrs.put("name", name);
        }
        if (defaultValue != null && !defaultValue.isBlank()) {
            attrs.put("defaultValue", defaultValue);
        }
        return SyntaxNode.leaf("Parameter", label, span, attrs);
    }
}
