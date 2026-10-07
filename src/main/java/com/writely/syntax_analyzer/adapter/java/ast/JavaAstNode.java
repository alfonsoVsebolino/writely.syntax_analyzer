package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

/**
 * Universal root interface for Java abstract syntax tree nodes.
 */
public interface JavaAstNode {
    SourceSpan span();
}
