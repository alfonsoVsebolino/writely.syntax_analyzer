package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ preprocessor `#include` directive.
 */
public record CppInclude(
    String header,
    boolean isSystem,
    SourceSpan span
) implements CppAstNode {

    public CppInclude {
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf(
            "IncludeDirective",
            "#include " + header,
            span,
            Map.of(
                "header", header,
                "type", isSystem ? "system" : "local"
            )
        );
    }
}
