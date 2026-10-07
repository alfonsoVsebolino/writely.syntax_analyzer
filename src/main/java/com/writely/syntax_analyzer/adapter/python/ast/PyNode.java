package com.writely.syntax_analyzer.adapter.python.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.List;

/**
 * Common interface for Python AST nodes.
 */
public interface PyNode {

    SourceSpan span();

    SyntaxNode toSyntaxNode();

    default List<? extends PyNode> children() {
        return List.of();
    }
}
