package com.writely.syntax_analyzer.adapter.python.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.List;
import java.util.Objects;

/**
 * Root AST node representing a Python module.
 */
public record PyModule(
    SourceSpan span,
    List<PyStatement> statements,
    String sourceName
) implements PyNode {

    public PyModule {
        Objects.requireNonNull(span, "span must not be null");
        statements = statements == null ? List.of() : List.copyOf(statements);
        sourceName = sourceName == null ? "Module" : sourceName;
    }

    @Override
    public List<PyStatement> children() {
        return statements;
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> childNodes = statements.stream()
            .map(PyStatement::toSyntaxNode)
            .toList();
        return SyntaxNode.of("Module", sourceName, span, childNodes);
    }
}
