package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

/**
 * Universal interface for native C++ AST nodes.
 */
public interface CppAstNode {

    /**
     * Source span covered by this AST node.
     */
    SourceSpan span();

    /**
     * Converts this native C++ AST node into the common {@link SyntaxNode} representation.
     */
    SyntaxNode toSyntaxNode();
}
