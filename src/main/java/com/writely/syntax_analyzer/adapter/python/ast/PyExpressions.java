package com.writely.syntax_analyzer.adapter.python.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Container and definitions for Python expression AST records.
 */
public final class PyExpressions {

    private PyExpressions() {
    }

    public record PyIdentifier(
        SourceSpan span,
        String name
    ) implements PyExpression {
        public PyIdentifier {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(name, "name must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.leaf("Identifier", name, span, Map.of("name", name));
        }
    }

    public record PyLiteral(
        SourceSpan span,
        String raw,
        String literalType,
        Object value
    ) implements PyExpression {
        public PyLiteral {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(raw, "raw must not be null");
            Objects.requireNonNull(literalType, "literalType must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.leaf("Literal", raw, span, Map.of("type", literalType, "raw", raw));
        }
    }

    public record PyBinaryExpr(
        SourceSpan span,
        PyExpression left,
        String operator,
        PyExpression right
    ) implements PyExpression {
        public PyBinaryExpr {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("BinaryExpr", operator, span, List.of(left.toSyntaxNode(), right.toSyntaxNode()), Map.of("operator", operator));
        }
    }

    public record PyUnaryExpr(
        SourceSpan span,
        String operator,
        PyExpression operand
    ) implements PyExpression {
        public PyUnaryExpr {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(operand, "operand must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("UnaryExpr", operator, span, List.of(operand.toSyntaxNode()), Map.of("operator", operator));
        }
    }

    public record PyTernaryExpr(
        SourceSpan span,
        PyExpression condition,
        PyExpression trueValue,
        PyExpression falseValue
    ) implements PyExpression {
        public PyTernaryExpr {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(condition, "condition must not be null");
            Objects.requireNonNull(trueValue, "trueValue must not be null");
            Objects.requireNonNull(falseValue, "falseValue must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("TernaryExpr", "if-else", span, List.of(trueValue.toSyntaxNode(), condition.toSyntaxNode(), falseValue.toSyntaxNode()));
        }
    }

    public record PyWalrusExpr(
        SourceSpan span,
        String targetName,
        PyExpression value
    ) implements PyExpression {
        public PyWalrusExpr {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(targetName, "targetName must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("WalrusExpr", ":=", span, List.of(value.toSyntaxNode()), Map.of("target", targetName));
        }
    }

    public record PyArgument(
        SourceSpan span,
        Optional<String> name,
        PyExpression value,
        boolean isVararg,
        boolean isKwarg
    ) implements PyNode {
        public PyArgument {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            String prefix = isVararg ? "*" : (isKwarg ? "**" : "");
            String label = name.map(n -> n + "=").orElse(prefix);
            return SyntaxNode.of("Argument", label, span, List.of(value.toSyntaxNode()));
        }
    }

    public record PyCall(
        SourceSpan span,
        PyExpression callee,
        List<PyArgument> arguments
    ) implements PyExpression {
        public PyCall {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(callee, "callee must not be null");
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(callee.toSyntaxNode());
            if (!arguments.isEmpty()) {
                SourceSpan argSpan = SourceSpan.of(arguments.get(0).span().start(), arguments.get(arguments.size() - 1).span().end());
                children.add(SyntaxNode.of("ArgumentList", "args", argSpan, arguments.stream().map(PyArgument::toSyntaxNode).toList()));
            }
            return SyntaxNode.of("Call", callee.toSyntaxNode().label(), span, children);
        }
    }

    public record PyAttribute(
        SourceSpan span,
        PyExpression value,
        String attribute
    ) implements PyExpression {
        public PyAttribute {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(value, "value must not be null");
            Objects.requireNonNull(attribute, "attribute must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("Attribute", attribute, span, List.of(value.toSyntaxNode()), Map.of("attr", attribute));
        }
    }

    public record PySlice(
        SourceSpan span,
        Optional<PyExpression> lower,
        Optional<PyExpression> upper,
        Optional<PyExpression> step
    ) implements PyExpression {
        public PySlice {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(lower, "lower must not be null");
            Objects.requireNonNull(upper, "upper must not be null");
            Objects.requireNonNull(step, "step must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            lower.ifPresent(l -> children.add(l.toSyntaxNode()));
            upper.ifPresent(u -> children.add(u.toSyntaxNode()));
            step.ifPresent(s -> children.add(s.toSyntaxNode()));
            return SyntaxNode.of("Slice", ":", span, children);
        }
    }

    public record PySubscript(
        SourceSpan span,
        PyExpression value,
        PyExpression slice
    ) implements PyExpression {
        public PySubscript {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(value, "value must not be null");
            Objects.requireNonNull(slice, "slice must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("Subscript", "[]", span, List.of(value.toSyntaxNode(), slice.toSyntaxNode()));
        }
    }

    public record PyList(
        SourceSpan span,
        List<PyExpression> elements
    ) implements PyExpression {
        public PyList {
            Objects.requireNonNull(span, "span must not be null");
            elements = elements == null ? List.of() : List.copyOf(elements);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("ListLiteral", "[]", span, elements.stream().map(PyExpression::toSyntaxNode).toList());
        }
    }

    public record PyTuple(
        SourceSpan span,
        List<PyExpression> elements
    ) implements PyExpression {
        public PyTuple {
            Objects.requireNonNull(span, "span must not be null");
            elements = elements == null ? List.of() : List.copyOf(elements);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("TupleLiteral", "()", span, elements.stream().map(PyExpression::toSyntaxNode).toList());
        }
    }

    public record PyDictEntry(
        SourceSpan span,
        PyExpression key,
        PyExpression value
    ) implements PyNode {
        public PyDictEntry {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("DictEntry", ":", span, List.of(key.toSyntaxNode(), value.toSyntaxNode()));
        }
    }

    public record PyDict(
        SourceSpan span,
        List<PyDictEntry> entries
    ) implements PyExpression {
        public PyDict {
            Objects.requireNonNull(span, "span must not be null");
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("DictLiteral", "{}", span, entries.stream().map(PyDictEntry::toSyntaxNode).toList());
        }
    }

    public record PySet(
        SourceSpan span,
        List<PyExpression> elements
    ) implements PyExpression {
        public PySet {
            Objects.requireNonNull(span, "span must not be null");
            elements = elements == null ? List.of() : List.copyOf(elements);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("SetLiteral", "{}", span, elements.stream().map(PyExpression::toSyntaxNode).toList());
        }
    }

    public record PyComprehensionClause(
        SourceSpan span,
        PyExpression target,
        PyExpression iter,
        List<PyExpression> ifs
    ) implements PyNode {
        public PyComprehensionClause {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(iter, "iter must not be null");
            ifs = ifs == null ? List.of() : List.copyOf(ifs);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(target.toSyntaxNode());
            children.add(iter.toSyntaxNode());
            ifs.forEach(i -> children.add(i.toSyntaxNode()));
            return SyntaxNode.of("ComprehensionClause", "for-in", span, children);
        }
    }

    public record PyListComp(
        SourceSpan span,
        PyExpression element,
        List<PyComprehensionClause> clauses
    ) implements PyExpression {
        public PyListComp {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(element, "element must not be null");
            clauses = clauses == null ? List.of() : List.copyOf(clauses);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(element.toSyntaxNode());
            clauses.forEach(c -> children.add(c.toSyntaxNode()));
            return SyntaxNode.of("ListComp", "listcomp", span, children);
        }
    }

    public record PyDictComp(
        SourceSpan span,
        PyExpression key,
        PyExpression value,
        List<PyComprehensionClause> clauses
    ) implements PyExpression {
        public PyDictComp {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(value, "value must not be null");
            clauses = clauses == null ? List.of() : List.copyOf(clauses);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(key.toSyntaxNode());
            children.add(value.toSyntaxNode());
            clauses.forEach(c -> children.add(c.toSyntaxNode()));
            return SyntaxNode.of("DictComp", "dictcomp", span, children);
        }
    }

    public record PySetComp(
        SourceSpan span,
        PyExpression element,
        List<PyComprehensionClause> clauses
    ) implements PyExpression {
        public PySetComp {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(element, "element must not be null");
            clauses = clauses == null ? List.of() : List.copyOf(clauses);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(element.toSyntaxNode());
            clauses.forEach(c -> children.add(c.toSyntaxNode()));
            return SyntaxNode.of("SetComp", "setcomp", span, children);
        }
    }

    public record PyGeneratorExpr(
        SourceSpan span,
        PyExpression element,
        List<PyComprehensionClause> clauses
    ) implements PyExpression {
        public PyGeneratorExpr {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(element, "element must not be null");
            clauses = clauses == null ? List.of() : List.copyOf(clauses);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(element.toSyntaxNode());
            clauses.forEach(c -> children.add(c.toSyntaxNode()));
            return SyntaxNode.of("GeneratorExpr", "genexpr", span, children);
        }
    }

    public record PyLambda(
        SourceSpan span,
        List<String> parameters,
        PyExpression body
    ) implements PyExpression {
        public PyLambda {
            Objects.requireNonNull(span, "span must not be null");
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            Objects.requireNonNull(body, "body must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("Lambda", "lambda", span, List.of(body.toSyntaxNode()), Map.of("params", String.join(", ", parameters)));
        }
    }

    public record PyYieldExpr(
        SourceSpan span,
        Optional<PyExpression> value,
        boolean isFrom
    ) implements PyExpression {
        public PyYieldExpr {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = value.map(v -> List.of(v.toSyntaxNode())).orElse(List.of());
            String label = isFrom ? "yield from" : "yield";
            return SyntaxNode.of("YieldExpr", label, span, children);
        }
    }
}
