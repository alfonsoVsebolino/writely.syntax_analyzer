package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a C++ template declaration prefixing a class, struct, or function.
 */
public record CppTemplate(
    List<CppTemplateParam> parameters,
    CppAstNode declaration,
    SourceSpan span
) implements CppAstNode {

    public CppTemplate {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = new ArrayList<>();
        SourceSpan paramSpan = parameters.isEmpty()
            ? SourceSpan.point(span.start())
            : SourceSpan.of(parameters.get(0).span().start(), parameters.get(parameters.size() - 1).span().end());
        List<SyntaxNode> paramNodes = parameters.stream().map(CppTemplateParam::toSyntaxNode).toList();
        children.add(SyntaxNode.of("TemplateParameterList", "parameters", paramSpan, paramNodes));

        if (declaration != null) {
            children.add(declaration.toSyntaxNode());
        }

        String paramSummary = parameters.stream()
            .map(p -> p.name().isBlank() ? p.kind() : p.kind() + " " + p.name())
            .collect(Collectors.joining(", "));

        return SyntaxNode.of(
            "TemplateDeclaration",
            "template<" + paramSummary + ">",
            span,
            children,
            Map.of("parameters", paramSummary)
        );
    }
}
