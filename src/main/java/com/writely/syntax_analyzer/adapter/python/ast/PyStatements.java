package com.writely.syntax_analyzer.adapter.python.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Container and definitions for Python statement AST records.
 */
public final class PyStatements {

    private PyStatements() {
    }

    public record PyDecorator(
        SourceSpan span,
        PyExpression expression
    ) implements PyNode {
        public PyDecorator {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(expression, "expression must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("Decorator", "@" + expression.toSyntaxNode().label(), span, List.of(expression.toSyntaxNode()));
        }
    }

    public record PyParameter(
        SourceSpan span,
        String name,
        Optional<PyExpression> typeAnnotation,
        Optional<PyExpression> defaultValue,
        boolean isVararg,
        boolean isKwarg
    ) implements PyNode {
        public PyParameter {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(typeAnnotation, "typeAnnotation must not be null");
            Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            typeAnnotation.ifPresent(t -> children.add(SyntaxNode.of("TypeAnnotation", "type", t.span(), List.of(t.toSyntaxNode()))));
            defaultValue.ifPresent(d -> children.add(SyntaxNode.of("DefaultValue", "default", d.span(), List.of(d.toSyntaxNode()))));
            String prefix = isVararg ? "*" : (isKwarg ? "**" : "");
            return SyntaxNode.of("Parameter", prefix + name, span, children, Map.of("name", name));
        }
    }

    public record PyFunctionDef(
        SourceSpan span,
        String name,
        List<PyDecorator> decorators,
        List<PyParameter> parameters,
        Optional<PyExpression> returnType,
        List<PyStatement> body
    ) implements PyStatement {
        public PyFunctionDef {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(name, "name must not be null");
            decorators = decorators == null ? List.of() : List.copyOf(decorators);
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            Objects.requireNonNull(returnType, "returnType must not be null");
            body = body == null ? List.of() : List.copyOf(body);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            decorators.forEach(d -> children.add(d.toSyntaxNode()));
            if (!parameters.isEmpty()) {
                SourceSpan paramSpan = SourceSpan.of(parameters.get(0).span().start(), parameters.get(parameters.size() - 1).span().end());
                List<SyntaxNode> paramNodes = parameters.stream().map(PyParameter::toSyntaxNode).toList();
                children.add(SyntaxNode.of("ParameterList", "parameters", paramSpan, paramNodes));
            }
            returnType.ifPresent(rt -> children.add(SyntaxNode.of("ReturnType", "returnType", rt.span(), List.of(rt.toSyntaxNode()))));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            List<SyntaxNode> bodyNodes = body.stream().map(PyStatement::toSyntaxNode).toList();
            children.add(SyntaxNode.of("Block", "body", bodySpan, bodyNodes));
            return SyntaxNode.of("FunctionDef", name, span, children, Map.of("name", name));
        }
    }

    public record PyClassDef(
        SourceSpan span,
        String name,
        List<PyDecorator> decorators,
        List<PyExpression> bases,
        List<PyStatement> body
    ) implements PyStatement {
        public PyClassDef {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(name, "name must not be null");
            decorators = decorators == null ? List.of() : List.copyOf(decorators);
            bases = bases == null ? List.of() : List.copyOf(bases);
            body = body == null ? List.of() : List.copyOf(body);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            decorators.forEach(d -> children.add(d.toSyntaxNode()));
            if (!bases.isEmpty()) {
                SourceSpan baseSpan = SourceSpan.of(bases.get(0).span().start(), bases.get(bases.size() - 1).span().end());
                List<SyntaxNode> baseNodes = bases.stream().map(PyExpression::toSyntaxNode).toList();
                children.add(SyntaxNode.of("BaseList", "bases", baseSpan, baseNodes));
            }
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            List<SyntaxNode> bodyNodes = body.stream().map(PyStatement::toSyntaxNode).toList();
            children.add(SyntaxNode.of("Block", "body", bodySpan, bodyNodes));
            return SyntaxNode.of("ClassDef", name, span, children, Map.of("name", name));
        }
    }

    public record PyElif(
        SourceSpan span,
        PyExpression condition,
        List<PyStatement> body
    ) implements PyNode {
        public PyElif {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(condition, "condition must not be null");
            body = body == null ? List.of() : List.copyOf(body);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(SyntaxNode.of("Condition", "condition", condition.span(), List.of(condition.toSyntaxNode())));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("Block", "body", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            return SyntaxNode.of("ElifClause", "elif", span, children);
        }
    }

    public record PyIf(
        SourceSpan span,
        PyExpression condition,
        List<PyStatement> thenBody,
        List<PyElif> elifClauses,
        List<PyStatement> elseBody
    ) implements PyStatement {
        public PyIf {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(condition, "condition must not be null");
            thenBody = thenBody == null ? List.of() : List.copyOf(thenBody);
            elifClauses = elifClauses == null ? List.of() : List.copyOf(elifClauses);
            elseBody = elseBody == null ? List.of() : List.copyOf(elseBody);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(SyntaxNode.of("Condition", "condition", condition.span(), List.of(condition.toSyntaxNode())));
            SourceSpan thenSpan = thenBody.isEmpty() ? span : SourceSpan.of(thenBody.get(0).span().start(), thenBody.get(thenBody.size() - 1).span().end());
            children.add(SyntaxNode.of("ThenBlock", "then", thenSpan, thenBody.stream().map(PyStatement::toSyntaxNode).toList()));
            elifClauses.forEach(e -> children.add(e.toSyntaxNode()));
            if (!elseBody.isEmpty()) {
                SourceSpan elseSpan = SourceSpan.of(elseBody.get(0).span().start(), elseBody.get(elseBody.size() - 1).span().end());
                children.add(SyntaxNode.of("ElseBlock", "else", elseSpan, elseBody.stream().map(PyStatement::toSyntaxNode).toList()));
            }
            return SyntaxNode.of("IfStatement", "if", span, children);
        }
    }

    public record PyFor(
        SourceSpan span,
        PyExpression target,
        PyExpression iterable,
        List<PyStatement> body,
        List<PyStatement> elseBody
    ) implements PyStatement {
        public PyFor {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(iterable, "iterable must not be null");
            body = body == null ? List.of() : List.copyOf(body);
            elseBody = elseBody == null ? List.of() : List.copyOf(elseBody);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(SyntaxNode.of("Target", "target", target.span(), List.of(target.toSyntaxNode())));
            children.add(SyntaxNode.of("Iterable", "iter", iterable.span(), List.of(iterable.toSyntaxNode())));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("Block", "body", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            if (!elseBody.isEmpty()) {
                SourceSpan elseSpan = SourceSpan.of(elseBody.get(0).span().start(), elseBody.get(elseBody.size() - 1).span().end());
                children.add(SyntaxNode.of("ElseBlock", "else", elseSpan, elseBody.stream().map(PyStatement::toSyntaxNode).toList()));
            }
            return SyntaxNode.of("ForStatement", "for", span, children);
        }
    }

    public record PyWhile(
        SourceSpan span,
        PyExpression condition,
        List<PyStatement> body,
        List<PyStatement> elseBody
    ) implements PyStatement {
        public PyWhile {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(condition, "condition must not be null");
            body = body == null ? List.of() : List.copyOf(body);
            elseBody = elseBody == null ? List.of() : List.copyOf(elseBody);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(SyntaxNode.of("Condition", "condition", condition.span(), List.of(condition.toSyntaxNode())));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("Block", "body", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            if (!elseBody.isEmpty()) {
                SourceSpan elseSpan = SourceSpan.of(elseBody.get(0).span().start(), elseBody.get(elseBody.size() - 1).span().end());
                children.add(SyntaxNode.of("ElseBlock", "else", elseSpan, elseBody.stream().map(PyStatement::toSyntaxNode).toList()));
            }
            return SyntaxNode.of("WhileStatement", "while", span, children);
        }
    }

    public record PyExcept(
        SourceSpan span,
        Optional<PyExpression> type,
        Optional<String> alias,
        List<PyStatement> body
    ) implements PyNode {
        public PyExcept {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(alias, "alias must not be null");
            body = body == null ? List.of() : List.copyOf(body);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            type.ifPresent(t -> children.add(t.toSyntaxNode()));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("Block", "body", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            return SyntaxNode.of("ExceptClause", "except", span, children, alias.map(a -> Map.of("as", a)).orElse(Map.of()));
        }
    }

    public record PyTry(
        SourceSpan span,
        List<PyStatement> body,
        List<PyExcept> exceptClauses,
        List<PyStatement> elseBody,
        List<PyStatement> finallyBody
    ) implements PyStatement {
        public PyTry {
            Objects.requireNonNull(span, "span must not be null");
            body = body == null ? List.of() : List.copyOf(body);
            exceptClauses = exceptClauses == null ? List.of() : List.copyOf(exceptClauses);
            elseBody = elseBody == null ? List.of() : List.copyOf(elseBody);
            finallyBody = finallyBody == null ? List.of() : List.copyOf(finallyBody);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("TryBlock", "try", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            exceptClauses.forEach(ec -> children.add(ec.toSyntaxNode()));
            if (!elseBody.isEmpty()) {
                SourceSpan elseSpan = SourceSpan.of(elseBody.get(0).span().start(), elseBody.get(elseBody.size() - 1).span().end());
                children.add(SyntaxNode.of("ElseBlock", "else", elseSpan, elseBody.stream().map(PyStatement::toSyntaxNode).toList()));
            }
            if (!finallyBody.isEmpty()) {
                SourceSpan finSpan = SourceSpan.of(finallyBody.get(0).span().start(), finallyBody.get(finallyBody.size() - 1).span().end());
                children.add(SyntaxNode.of("FinallyBlock", "finally", finSpan, finallyBody.stream().map(PyStatement::toSyntaxNode).toList()));
            }
            return SyntaxNode.of("TryStatement", "try", span, children);
        }
    }

    public record PyWithItem(
        SourceSpan span,
        PyExpression contextExpr,
        Optional<PyExpression> optionalVars
    ) implements PyNode {
        public PyWithItem {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(contextExpr, "contextExpr must not be null");
            Objects.requireNonNull(optionalVars, "optionalVars must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(contextExpr.toSyntaxNode());
            optionalVars.ifPresent(v -> children.add(v.toSyntaxNode()));
            return SyntaxNode.of("WithItem", "item", span, children);
        }
    }

    public record PyWith(
        SourceSpan span,
        List<PyWithItem> items,
        List<PyStatement> body
    ) implements PyStatement {
        public PyWith {
            Objects.requireNonNull(span, "span must not be null");
            items = items == null ? List.of() : List.copyOf(items);
            body = body == null ? List.of() : List.copyOf(body);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            items.forEach(i -> children.add(i.toSyntaxNode()));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("Block", "body", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            return SyntaxNode.of("WithStatement", "with", span, children);
        }
    }

    public record PyCase(
        SourceSpan span,
        PyExpression pattern,
        Optional<PyExpression> guard,
        List<PyStatement> body
    ) implements PyNode {
        public PyCase {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(pattern, "pattern must not be null");
            Objects.requireNonNull(guard, "guard must not be null");
            body = body == null ? List.of() : List.copyOf(body);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(pattern.toSyntaxNode());
            guard.ifPresent(g -> children.add(g.toSyntaxNode()));
            SourceSpan bodySpan = body.isEmpty() ? span : SourceSpan.of(body.get(0).span().start(), body.get(body.size() - 1).span().end());
            children.add(SyntaxNode.of("Block", "body", bodySpan, body.stream().map(PyStatement::toSyntaxNode).toList()));
            return SyntaxNode.of("CaseClause", "case", span, children);
        }
    }

    public record PyMatch(
        SourceSpan span,
        PyExpression subject,
        List<PyCase> cases
    ) implements PyStatement {
        public PyMatch {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(subject, "subject must not be null");
            cases = cases == null ? List.of() : List.copyOf(cases);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(subject.toSyntaxNode());
            cases.forEach(c -> children.add(c.toSyntaxNode()));
            return SyntaxNode.of("MatchStatement", "match", span, children);
        }
    }

    public record PyAssign(
        SourceSpan span,
        List<PyExpression> targets,
        PyExpression value
    ) implements PyStatement {
        public PyAssign {
            Objects.requireNonNull(span, "span must not be null");
            targets = targets == null ? List.of() : List.copyOf(targets);
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            targets.forEach(t -> children.add(t.toSyntaxNode()));
            children.add(value.toSyntaxNode());
            return SyntaxNode.of("AssignStatement", "=", span, children);
        }
    }

    public record PyAugAssign(
        SourceSpan span,
        PyExpression target,
        String operator,
        PyExpression value
    ) implements PyStatement {
        public PyAugAssign {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("AugAssignStatement", operator, span, List.of(target.toSyntaxNode(), value.toSyntaxNode()), Map.of("operator", operator));
        }
    }

    public record PyAnnAssign(
        SourceSpan span,
        PyExpression target,
        PyExpression annotation,
        Optional<PyExpression> value
    ) implements PyStatement {
        public PyAnnAssign {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(annotation, "annotation must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(target.toSyntaxNode());
            children.add(annotation.toSyntaxNode());
            value.ifPresent(v -> children.add(v.toSyntaxNode()));
            return SyntaxNode.of("AnnAssignStatement", ":", span, children);
        }
    }

    public record PyReturn(
        SourceSpan span,
        Optional<PyExpression> value
    ) implements PyStatement {
        public PyReturn {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(value, "value must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = value.map(v -> List.of(v.toSyntaxNode())).orElse(List.of());
            return SyntaxNode.of("ReturnStatement", "return", span, children);
        }
    }

    public record PyRaise(
        SourceSpan span,
        Optional<PyExpression> exception,
        Optional<PyExpression> cause
    ) implements PyStatement {
        public PyRaise {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(exception, "exception must not be null");
            Objects.requireNonNull(cause, "cause must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            exception.ifPresent(e -> children.add(e.toSyntaxNode()));
            cause.ifPresent(c -> children.add(c.toSyntaxNode()));
            return SyntaxNode.of("RaiseStatement", "raise", span, children);
        }
    }

    public record PyAssert(
        SourceSpan span,
        PyExpression test,
        Optional<PyExpression> message
    ) implements PyStatement {
        public PyAssert {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(test, "test must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(test.toSyntaxNode());
            message.ifPresent(m -> children.add(m.toSyntaxNode()));
            return SyntaxNode.of("AssertStatement", "assert", span, children);
        }
    }

    public record PyPass(SourceSpan span) implements PyStatement {
        public PyPass {
            Objects.requireNonNull(span, "span must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("PassStatement", "pass", span, List.of());
        }
    }

    public record PyBreak(SourceSpan span) implements PyStatement {
        public PyBreak {
            Objects.requireNonNull(span, "span must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("BreakStatement", "break", span, List.of());
        }
    }

    public record PyContinue(SourceSpan span) implements PyStatement {
        public PyContinue {
            Objects.requireNonNull(span, "span must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("ContinueStatement", "continue", span, List.of());
        }
    }

    public record PyAlias(
        SourceSpan span,
        String name,
        Optional<String> asName
    ) implements PyNode {
        public PyAlias {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(asName, "asName must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            String label = asName.map(a -> name + " as " + a).orElse(name);
            Map<String, String> attrs = asName.map(a -> Map.of("name", name, "as", a)).orElse(Map.of("name", name));
            return SyntaxNode.of("Alias", label, span, List.of(), attrs);
        }
    }

    public record PyImport(
        SourceSpan span,
        List<PyAlias> names
    ) implements PyStatement {
        public PyImport {
            Objects.requireNonNull(span, "span must not be null");
            names = names == null ? List.of() : List.copyOf(names);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = names.stream().map(PyAlias::toSyntaxNode).toList();
            return SyntaxNode.of("ImportStatement", "import", span, children);
        }
    }

    public record PyImportFrom(
        SourceSpan span,
        Optional<String> module,
        int level,
        List<PyAlias> names
    ) implements PyStatement {
        public PyImportFrom {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(module, "module must not be null");
            names = names == null ? List.of() : List.copyOf(names);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = names.stream().map(PyAlias::toSyntaxNode).toList();
            String prefix = ".".repeat(Math.max(0, level));
            String modName = module.map(m -> prefix + m).orElse(prefix);
            return SyntaxNode.of("FromImportStatement", "from " + modName + " import", span, children, Map.of("module", modName));
        }
    }

    public record PyGlobal(
        SourceSpan span,
        List<String> names
    ) implements PyStatement {
        public PyGlobal {
            Objects.requireNonNull(span, "span must not be null");
            names = names == null ? List.of() : List.copyOf(names);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("GlobalStatement", String.join(", ", names), span, List.of());
        }
    }

    public record PyNonlocal(
        SourceSpan span,
        List<String> names
    ) implements PyStatement {
        public PyNonlocal {
            Objects.requireNonNull(span, "span must not be null");
            names = names == null ? List.of() : List.copyOf(names);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            return SyntaxNode.of("NonlocalStatement", String.join(", ", names), span, List.of());
        }
    }

    public record PyDelete(
        SourceSpan span,
        List<PyExpression> targets
    ) implements PyStatement {
        public PyDelete {
            Objects.requireNonNull(span, "span must not be null");
            targets = targets == null ? List.of() : List.copyOf(targets);
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            List<SyntaxNode> children = targets.stream().map(PyExpression::toSyntaxNode).toList();
            return SyntaxNode.of("DeleteStatement", "del", span, children);
        }
    }

    public record PyExprStmt(
        SourceSpan span,
        PyExpression expression
    ) implements PyStatement {
        public PyExprStmt {
            Objects.requireNonNull(span, "span must not be null");
            Objects.requireNonNull(expression, "expression must not be null");
        }

        @Override
        public SyntaxNode toSyntaxNode() {
            SyntaxNode exprNode = expression.toSyntaxNode();
            return SyntaxNode.of("ExpressionStatement", exprNode.label(), span, List.of(exprNode));
        }
    }
}
