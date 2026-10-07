package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Common sealed interface for C++ statement AST nodes.
 */
public sealed interface CppStatement extends CppAstNode permits
    CppCompoundStmt,
    CppIfStmt,
    CppSwitchStmt,
    CppCaseStmt,
    CppDefaultStmt,
    CppForStmt,
    CppRangeForStmt,
    CppWhileStmt,
    CppDoWhileStmt,
    CppReturnStmt,
    CppBreakStmt,
    CppContinueStmt,
    CppExprStmt,
    CppDeclarationStmt,
    CppEmptyStmt {
}

record CppCompoundStmt(
    List<CppStatement> statements,
    SourceSpan span
) implements CppStatement {
    public CppCompoundStmt {
        statements = statements == null ? List.of() : List.copyOf(statements);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = statements.stream()
            .map(CppAstNode::toSyntaxNode)
            .toList();
        return SyntaxNode.of("CompoundStatement", "{}", span, children);
    }
}

record CppIfStmt(
    CppExpr condition,
    CppStatement thenBranch,
    CppStatement elseBranch,
    SourceSpan span
) implements CppStatement {
    public CppIfStmt {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(thenBranch, "thenBranch must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = new ArrayList<>();
        children.add(condition.toSyntaxNode());
        children.add(thenBranch.toSyntaxNode());
        if (elseBranch != null) {
            children.add(elseBranch.toSyntaxNode());
        }
        return SyntaxNode.of("IfStatement", "if", span, children);
    }
}

record CppSwitchStmt(
    CppExpr condition,
    CppStatement body,
    SourceSpan span
) implements CppStatement {
    public CppSwitchStmt {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "SwitchStatement",
            "switch",
            span,
            List.of(condition.toSyntaxNode(), body.toSyntaxNode())
        );
    }
}

record CppCaseStmt(
    CppExpr value,
    List<CppStatement> statements,
    SourceSpan span
) implements CppStatement {
    public CppCaseStmt {
        Objects.requireNonNull(value, "value must not be null");
        statements = statements == null ? List.of() : List.copyOf(statements);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = new ArrayList<>();
        children.add(value.toSyntaxNode());
        for (CppStatement s : statements) {
            children.add(s.toSyntaxNode());
        }
        return SyntaxNode.of("CaseStatement", "case", span, children);
    }
}

record CppDefaultStmt(
    List<CppStatement> statements,
    SourceSpan span
) implements CppStatement {
    public CppDefaultStmt {
        statements = statements == null ? List.of() : List.copyOf(statements);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = statements.stream()
            .map(CppAstNode::toSyntaxNode)
            .toList();
        return SyntaxNode.of("DefaultStatement", "default", span, children);
    }
}

record CppForStmt(
    CppAstNode init,
    CppExpr condition,
    CppExpr update,
    CppStatement body,
    SourceSpan span
) implements CppStatement {
    public CppForStmt {
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = new ArrayList<>();
        if (init != null) {
            children.add(init.toSyntaxNode());
        }
        if (condition != null) {
            children.add(condition.toSyntaxNode());
        }
        if (update != null) {
            children.add(update.toSyntaxNode());
        }
        children.add(body.toSyntaxNode());
        return SyntaxNode.of("ForStatement", "for", span, children);
    }
}

record CppRangeForStmt(
    String type,
    String variable,
    CppExpr rangeExpr,
    CppStatement body,
    SourceSpan span
) implements CppStatement {
    public CppRangeForStmt {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(variable, "variable must not be null");
        Objects.requireNonNull(rangeExpr, "rangeExpr must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("type", type);
        attrs.put("variable", variable);
        return SyntaxNode.of(
            "RangeForStatement",
            "for",
            span,
            List.of(rangeExpr.toSyntaxNode(), body.toSyntaxNode()),
            attrs
        );
    }
}

record CppWhileStmt(
    CppExpr condition,
    CppStatement body,
    SourceSpan span
) implements CppStatement {
    public CppWhileStmt {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "WhileStatement",
            "while",
            span,
            List.of(condition.toSyntaxNode(), body.toSyntaxNode())
        );
    }
}

record CppDoWhileStmt(
    CppStatement body,
    CppExpr condition,
    SourceSpan span
) implements CppStatement {
    public CppDoWhileStmt {
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "DoWhileStatement",
            "do-while",
            span,
            List.of(body.toSyntaxNode(), condition.toSyntaxNode())
        );
    }
}

record CppReturnStmt(
    CppExpr expression,
    SourceSpan span
) implements CppStatement {
    public CppReturnStmt {
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = expression != null
            ? List.of(expression.toSyntaxNode())
            : List.of();
        return SyntaxNode.of("ReturnStatement", "return", span, children);
    }
}

record CppBreakStmt(
    SourceSpan span
) implements CppStatement {
    public CppBreakStmt {
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf("BreakStatement", "break", span);
    }
}

record CppContinueStmt(
    SourceSpan span
) implements CppStatement {
    public CppContinueStmt {
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf("ContinueStatement", "continue", span);
    }
}

record CppExprStmt(
    CppExpr expression,
    SourceSpan span
) implements CppStatement {
    public CppExprStmt {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "ExpressionStatement",
            expression.toSyntaxNode().label(),
            span,
            List.of(expression.toSyntaxNode())
        );
    }
}

record CppDeclarationStmt(
    CppAstNode declaration,
    SourceSpan span
) implements CppStatement {
    public CppDeclarationStmt {
        Objects.requireNonNull(declaration, "declaration must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return declaration.toSyntaxNode();
    }
}

record CppEmptyStmt(
    SourceSpan span
) implements CppStatement {
    public CppEmptyStmt {
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf("EmptyStatement", ";", span);
    }
}
