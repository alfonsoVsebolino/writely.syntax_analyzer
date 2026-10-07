package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ class or struct declaration/definition.
 */
public record CppClass(
    String name,
    String kind,
    List<String> baseClasses,
    List<CppAstNode> members,
    boolean isForwardDeclaration,
    SourceSpan span
) implements CppAstNode {

    public CppClass {
        name = name == null ? "" : name;
        kind = kind == null ? "class" : kind;
        baseClasses = baseClasses == null ? List.of() : List.copyOf(baseClasses);
        members = members == null ? List.of() : List.copyOf(members);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        String nodeKind = "struct".equalsIgnoreCase(kind) ? "StructDeclaration" : "ClassDeclaration";
        String label = name.isBlank() ? "(anonymous)" : name;
        List<SyntaxNode> children = members.stream()
            .map(CppAstNode::toSyntaxNode)
            .toList();

        Map<String, String> attrs = new HashMap<>();
        attrs.put("name", label);
        attrs.put("kind", kind);
        if (!baseClasses.isEmpty()) {
            attrs.put("bases", String.join(", ", baseClasses));
        }
        if (isForwardDeclaration) {
            attrs.put("forwardDeclaration", "true");
        }

        return SyntaxNode.of(nodeKind, label, span, children, attrs);
    }
}
