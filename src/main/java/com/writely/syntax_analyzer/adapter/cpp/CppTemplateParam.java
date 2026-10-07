package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a template parameter in a C++ template declaration.
 */
public record CppTemplateParam(
    String kind,
    String name,
    String defaultValue,
    SourceSpan span
) implements CppAstNode {

    public CppTemplateParam {
        Objects.requireNonNull(kind, "kind must not be null");
        name = name == null ? "" : name;
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        String label = name.isBlank() ? kind : kind + " " + name;
        Map<String, String> attrs = new HashMap<>();
        attrs.put("kind", kind);
        if (!name.isBlank()) {
            attrs.put("name", name);
        }
        if (defaultValue != null && !defaultValue.isBlank()) {
            attrs.put("defaultValue", defaultValue);
        }
        return SyntaxNode.leaf("TemplateParameter", label, span, attrs);
    }
}
