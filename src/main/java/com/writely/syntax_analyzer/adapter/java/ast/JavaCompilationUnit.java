package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Root AST node representing a parsed Java compilation unit, snippet, or command.
 */
public record JavaCompilationUnit(
    String sourceName,
    Optional<JavaPackageDeclaration> packageDeclaration,
    List<JavaImportDeclaration> imports,
    List<JavaAstNode> declarations,
    SourceSpan span
) implements JavaAstNode {
    public JavaCompilationUnit {
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(packageDeclaration, "packageDeclaration must not be null");
        imports = imports == null ? List.of() : List.copyOf(imports);
        declarations = declarations == null ? List.of() : List.copyOf(declarations);
        Objects.requireNonNull(span, "span must not be null");
    }
}
