package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Common sealed interface for C++ expression AST nodes.
 */
public sealed interface CppExpr extends CppAstNode permits
    CppBinaryExpr,
    CppUnaryExpr,
    CppCallExpr,
    CppMemberExpr,
    CppScopeExpr,
    CppSubscriptExpr,
    CppLiteralExpr,
    CppIdentifierExpr,
    CppTernaryExpr,
    CppInitializerListExpr,
    CppErrorExpr {
}

record CppBinaryExpr(
    String operator,
    CppExpr left,
    CppExpr right,
    SourceSpan span
) implements CppExpr {
    public CppBinaryExpr {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "BinaryExpression",
            operator,
            span,
            List.of(left.toSyntaxNode(), right.toSyntaxNode()),
            Map.of("operator", operator)
        );
    }
}

record CppUnaryExpr(
    String operator,
    CppExpr operand,
    boolean isPrefix,
    SourceSpan span
) implements CppExpr {
    public CppUnaryExpr {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(operand, "operand must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "UnaryExpression",
            operator,
            span,
            List.of(operand.toSyntaxNode()),
            Map.of("operator", operator, "prefix", String.valueOf(isPrefix))
        );
    }
}

record CppCallExpr(
    CppExpr callee,
    List<CppExpr> arguments,
    SourceSpan span
) implements CppExpr {
    public CppCallExpr {
        Objects.requireNonNull(callee, "callee must not be null");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = new ArrayList<>();
        children.add(callee.toSyntaxNode());
        for (CppExpr arg : arguments) {
            children.add(arg.toSyntaxNode());
        }
        return SyntaxNode.of("CallExpression", callee.toSyntaxNode().label(), span, children);
    }
}

record CppMemberExpr(
    CppExpr object,
    String member,
    boolean isArrow,
    SourceSpan span
) implements CppExpr {
    public CppMemberExpr {
        Objects.requireNonNull(object, "object must not be null");
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "MemberAccess",
            member,
            span,
            List.of(object.toSyntaxNode()),
            Map.of("member", member, "operator", isArrow ? "->" : ".")
        );
    }
}

record CppScopeExpr(
    CppExpr scope,
    String member,
    SourceSpan span
) implements CppExpr {
    public CppScopeExpr {
        member = member == null ? "" : member;
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = scope != null ? List.of(scope.toSyntaxNode()) : List.of();
        String label = (scope != null ? scope.toSyntaxNode().label() + "::" : "::") + member;
        return SyntaxNode.of("ScopeResolution", label, span, children, Map.of("member", member));
    }
}

record CppSubscriptExpr(
    CppExpr array,
    CppExpr index,
    SourceSpan span
) implements CppExpr {
    public CppSubscriptExpr {
        Objects.requireNonNull(array, "array must not be null");
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "SubscriptExpression",
            "[]",
            span,
            List.of(array.toSyntaxNode(), index.toSyntaxNode())
        );
    }
}

record CppLiteralExpr(
    String value,
    String literalType,
    SourceSpan span
) implements CppExpr {
    public CppLiteralExpr {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(literalType, "literalType must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf("Literal", value, span, Map.of("literalType", literalType));
    }
}

record CppIdentifierExpr(
    String name,
    SourceSpan span
) implements CppExpr {
    public CppIdentifierExpr {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf("Identifier", name, span, Map.of("name", name));
    }
}

record CppTernaryExpr(
    CppExpr condition,
    CppExpr thenExpr,
    CppExpr elseExpr,
    SourceSpan span
) implements CppExpr {
    public CppTernaryExpr {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(thenExpr, "thenExpr must not be null");
        Objects.requireNonNull(elseExpr, "elseExpr must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.of(
            "ConditionalExpression",
            "?:",
            span,
            List.of(condition.toSyntaxNode(), thenExpr.toSyntaxNode(), elseExpr.toSyntaxNode())
        );
    }
}

record CppInitializerListExpr(
    List<CppExpr> elements,
    SourceSpan span
) implements CppExpr {
    public CppInitializerListExpr {
        elements = elements == null ? List.of() : List.copyOf(elements);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        List<SyntaxNode> children = elements.stream().map(CppExpr::toSyntaxNode).toList();
        return SyntaxNode.of("InitializerList", "{}", span, children);
    }
}

record CppErrorExpr(
    SourceSpan span
) implements CppExpr {
    public CppErrorExpr {
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        return SyntaxNode.leaf("ErrorExpression", "<error>", span);
    }
}
