package com.writely.syntax_analyzer.adapter.java;

import com.writely.syntax_analyzer.adapter.java.ast.*;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Transforms native Java AST structures into the universal domain {@link SyntaxNode} hierarchy.
 */
public final class JavaAstMapper {

    private JavaAstMapper() {}

    public static SyntaxNode toSyntaxNode(JavaAstNode node) {
        Objects.requireNonNull(node, "node must not be null");

        if (node instanceof JavaCompilationUnit cu) {
            List<SyntaxNode> children = new ArrayList<>();
            cu.packageDeclaration().ifPresent(pkg -> children.add(toSyntaxNode(pkg)));
            for (JavaImportDeclaration imp : cu.imports()) {
                children.add(toSyntaxNode(imp));
            }
            for (JavaAstNode decl : cu.declarations()) {
                children.add(toSyntaxNode(decl));
            }
            return SyntaxNode.of("CompilationUnit", cu.sourceName(), cu.span(), children);
        }

        if (node instanceof JavaPackageDeclaration pkg) {
            return SyntaxNode.leaf("PackageDeclaration", pkg.packageName(), pkg.span());
        }

        if (node instanceof JavaImportDeclaration imp) {
            Map<String, String> attrs = Map.of(
                "static", String.valueOf(imp.isStatic()),
                "wildcard", String.valueOf(imp.isWildcard())
            );
            return SyntaxNode.leaf("ImportDeclaration", imp.importName(), imp.span(), attrs);
        }

        if (node instanceof JavaClassDeclaration cls) {
            List<SyntaxNode> children = cls.members().stream()
                .map(JavaAstMapper::toSyntaxNode)
                .collect(Collectors.toList());
            Map<String, String> attrs = new HashMap<>();
            if (!cls.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", cls.modifiers()));
            }
            cls.superclass().ifPresent(sc -> attrs.put("superclass", sc.fullTypeName()));
            return SyntaxNode.of("ClassDeclaration", cls.name(), cls.span(), children, attrs);
        }

        if (node instanceof JavaInterfaceDeclaration iface) {
            List<SyntaxNode> children = iface.members().stream()
                .map(JavaAstMapper::toSyntaxNode)
                .collect(Collectors.toList());
            Map<String, String> attrs = new HashMap<>();
            if (!iface.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", iface.modifiers()));
            }
            return SyntaxNode.of("InterfaceDeclaration", iface.name(), iface.span(), children, attrs);
        }

        if (node instanceof JavaRecordDeclaration rec) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaParameter comp : rec.components()) {
                children.add(toSyntaxNode(comp));
            }
            for (JavaAstNode member : rec.members()) {
                children.add(toSyntaxNode(member));
            }
            Map<String, String> attrs = new HashMap<>();
            if (!rec.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", rec.modifiers()));
            }
            return SyntaxNode.of("RecordDeclaration", rec.name(), rec.span(), children, attrs);
        }

        if (node instanceof JavaEnumDeclaration enm) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaEnumConstant c : enm.constants()) {
                children.add(toSyntaxNode(c));
            }
            for (JavaAstNode member : enm.members()) {
                children.add(toSyntaxNode(member));
            }
            Map<String, String> attrs = new HashMap<>();
            if (!enm.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", enm.modifiers()));
            }
            return SyntaxNode.of("EnumDeclaration", enm.name(), enm.span(), children, attrs);
        }

        if (node instanceof JavaEnumConstant c) {
            List<SyntaxNode> children = c.arguments().stream()
                .map(JavaAstMapper::toSyntaxNode)
                .collect(Collectors.toList());
            return SyntaxNode.of("EnumConstant", c.name(), c.span(), children);
        }

        if (node instanceof JavaFieldDeclaration field) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaVariableDeclarator var : field.variables()) {
                var.initializer().ifPresent(init -> children.add(toSyntaxNode(init)));
            }
            String label = field.variables().stream().map(JavaVariableDeclarator::name).collect(Collectors.joining(", "));
            Map<String, String> attrs = new HashMap<>();
            attrs.put("type", field.type().fullTypeName());
            if (!field.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", field.modifiers()));
            }
            return SyntaxNode.of("FieldDeclaration", label, field.span(), children, attrs);
        }

        if (node instanceof JavaMethodDeclaration m) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaParameter p : m.parameters()) {
                children.add(toSyntaxNode(p));
            }
            m.body().ifPresent(body -> children.add(toSyntaxNode(body)));
            Map<String, String> attrs = new HashMap<>();
            attrs.put("returnType", m.returnType().fullTypeName());
            if (!m.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", m.modifiers()));
            }
            return SyntaxNode.of("MethodDeclaration", m.name(), m.span(), children, attrs);
        }

        if (node instanceof JavaConstructorDeclaration c) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaParameter p : c.parameters()) {
                children.add(toSyntaxNode(p));
            }
            children.add(toSyntaxNode(c.body()));
            Map<String, String> attrs = new HashMap<>();
            if (!c.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", c.modifiers()));
            }
            return SyntaxNode.of("ConstructorDeclaration", c.name(), c.span(), children, attrs);
        }

        if (node instanceof JavaInitializerBlock init) {
            List<SyntaxNode> children = init.body().statements().stream()
                .map(JavaAstMapper::toSyntaxNode)
                .collect(Collectors.toList());
            return SyntaxNode.of("InitializerBlock", init.isStatic() ? "static" : "instance", init.span(), children);
        }

        if (node instanceof JavaBlock b) {
            List<SyntaxNode> children = b.statements().stream()
                .map(JavaAstMapper::toSyntaxNode)
                .collect(Collectors.toList());
            return SyntaxNode.of("Block", "{...}", b.span(), children);
        }

        if (node instanceof JavaParameter p) {
            Map<String, String> attrs = Map.of("type", p.type().fullTypeName());
            return SyntaxNode.leaf("Parameter", p.name(), p.span(), attrs);
        }

        // Statements
        if (node instanceof JavaIfStatement s) {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(toSyntaxNode(s.condition()));
            children.add(toSyntaxNode(s.thenBranch()));
            s.elseBranch().ifPresent(el -> children.add(toSyntaxNode(el)));
            return SyntaxNode.of("IfStatement", "if", s.span(), children);
        }

        if (node instanceof JavaSwitchStatement s) {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(toSyntaxNode(s.selector()));
            for (JavaSwitchEntry entry : s.entries()) {
                children.add(toSyntaxNode(entry));
            }
            return SyntaxNode.of("SwitchStatement", "switch", s.span(), children);
        }

        if (node instanceof JavaSwitchEntry entry) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaExpression lbl : entry.labels()) {
                children.add(toSyntaxNode(lbl));
            }
            for (JavaStatement stmt : entry.statements()) {
                children.add(toSyntaxNode(stmt));
            }
            String label = entry.isDefault() ? "default" : "case";
            return SyntaxNode.of("SwitchEntry", label, entry.span(), children);
        }

        if (node instanceof JavaForStatement s) {
            List<SyntaxNode> children = new ArrayList<>();
            s.init().ifPresent(i -> children.add(toSyntaxNode(i)));
            s.condition().ifPresent(c -> children.add(toSyntaxNode(c)));
            for (JavaExpression u : s.update()) {
                children.add(toSyntaxNode(u));
            }
            children.add(toSyntaxNode(s.body()));
            return SyntaxNode.of("ForStatement", "for", s.span(), children);
        }

        if (node instanceof JavaEnhancedForStatement s) {
            List<SyntaxNode> children = List.of(
                toSyntaxNode(s.variable()),
                toSyntaxNode(s.expression()),
                toSyntaxNode(s.body())
            );
            return SyntaxNode.of("EnhancedForStatement", "for-each", s.span(), children);
        }

        if (node instanceof JavaWhileStatement s) {
            List<SyntaxNode> children = List.of(
                toSyntaxNode(s.condition()),
                toSyntaxNode(s.body())
            );
            return SyntaxNode.of("WhileStatement", "while", s.span(), children);
        }

        if (node instanceof JavaDoWhileStatement s) {
            List<SyntaxNode> children = List.of(
                toSyntaxNode(s.body()),
                toSyntaxNode(s.condition())
            );
            return SyntaxNode.of("DoWhileStatement", "do-while", s.span(), children);
        }

        if (node instanceof JavaTryStatement s) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaAstNode res : s.resources()) {
                children.add(toSyntaxNode(res));
            }
            children.add(toSyntaxNode(s.tryBlock()));
            for (JavaCatchClause cc : s.catchClauses()) {
                children.add(toSyntaxNode(cc));
            }
            s.finallyBlock().ifPresent(fin -> children.add(toSyntaxNode(fin)));
            return SyntaxNode.of("TryStatement", "try", s.span(), children);
        }

        if (node instanceof JavaCatchClause cc) {
            List<SyntaxNode> children = List.of(
                toSyntaxNode(cc.parameter()),
                toSyntaxNode(cc.body())
            );
            return SyntaxNode.of("CatchClause", "catch(" + cc.parameter().name() + ")", cc.span(), children);
        }

        if (node instanceof JavaReturnStatement s) {
            List<SyntaxNode> children = s.value().map(v -> List.of(toSyntaxNode(v))).orElse(List.of());
            return SyntaxNode.of("ReturnStatement", "return", s.span(), children);
        }

        if (node instanceof JavaThrowStatement s) {
            return SyntaxNode.of("ThrowStatement", "throw", s.span(), List.of(toSyntaxNode(s.expression())));
        }

        if (node instanceof JavaBreakStatement s) {
            String label = s.label().map(l -> "break " + l).orElse("break");
            return SyntaxNode.leaf("BreakStatement", label, s.span());
        }

        if (node instanceof JavaContinueStatement s) {
            String label = s.label().map(l -> "continue " + l).orElse("continue");
            return SyntaxNode.leaf("ContinueStatement", label, s.span());
        }

        if (node instanceof JavaYieldStatement s) {
            return SyntaxNode.of("YieldStatement", "yield", s.span(), List.of(toSyntaxNode(s.expression())));
        }

        if (node instanceof JavaAssertStatement s) {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(toSyntaxNode(s.condition()));
            s.detail().ifPresent(d -> children.add(toSyntaxNode(d)));
            return SyntaxNode.of("AssertStatement", "assert", s.span(), children);
        }

        if (node instanceof JavaVariableDeclarationStatement s) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaVariableDeclarator var : s.variables()) {
                var.initializer().ifPresent(init -> children.add(toSyntaxNode(init)));
            }
            String label = s.variables().stream().map(JavaVariableDeclarator::name).collect(Collectors.joining(", "));
            Map<String, String> attrs = new HashMap<>();
            attrs.put("type", s.type().fullTypeName());
            if (!s.modifiers().isEmpty()) {
                attrs.put("modifiers", String.join(" ", s.modifiers()));
            }
            return SyntaxNode.of("VariableDeclaration", label, s.span(), children, attrs);
        }

        if (node instanceof JavaExpressionStatement s) {
            return SyntaxNode.of("ExpressionStatement", "expr", s.span(), List.of(toSyntaxNode(s.expression())));
        }

        if (node instanceof JavaEmptyStatement s) {
            return SyntaxNode.leaf("EmptyStatement", ";", s.span());
        }

        // Expressions
        if (node instanceof JavaBinaryExpression e) {
            List<SyntaxNode> children = List.of(toSyntaxNode(e.left()), toSyntaxNode(e.right()));
            return SyntaxNode.of("BinaryExpression", e.operator(), e.span(), children);
        }

        if (node instanceof JavaUnaryExpression e) {
            Map<String, String> attrs = Map.of("prefix", String.valueOf(e.isPrefix()));
            return SyntaxNode.of("UnaryExpression", e.operator(), e.span(), List.of(toSyntaxNode(e.operand())), attrs);
        }

        if (node instanceof JavaAssignmentExpression e) {
            List<SyntaxNode> children = List.of(toSyntaxNode(e.target()), toSyntaxNode(e.value()));
            return SyntaxNode.of("AssignmentExpression", e.operator(), e.span(), children);
        }

        if (node instanceof JavaLiteralExpression e) {
            Map<String, String> attrs = Map.of("literalKind", e.literalKind());
            return SyntaxNode.leaf("Literal", e.rawText(), e.span(), attrs);
        }

        if (node instanceof JavaIdentifierExpression e) {
            return SyntaxNode.leaf("Identifier", e.name(), e.span());
        }

        if (node instanceof JavaMemberAccessExpression e) {
            return SyntaxNode.of("MemberAccess", e.memberName(), e.span(), List.of(toSyntaxNode(e.target())));
        }

        if (node instanceof JavaMethodInvocationExpression e) {
            List<SyntaxNode> children = new ArrayList<>();
            e.target().ifPresent(t -> children.add(toSyntaxNode(t)));
            for (JavaExpression arg : e.arguments()) {
                children.add(toSyntaxNode(arg));
            }
            return SyntaxNode.of("MethodInvocation", e.methodName(), e.span(), children);
        }

        if (node instanceof JavaArrayAccessExpression e) {
            List<SyntaxNode> children = List.of(toSyntaxNode(e.array()), toSyntaxNode(e.index()));
            return SyntaxNode.of("ArrayAccess", "[]", e.span(), children);
        }

        if (node instanceof JavaArrayCreationExpression e) {
            List<SyntaxNode> children = new ArrayList<>(e.dimensionExpressions().stream()
                .map(JavaAstMapper::toSyntaxNode).toList());
            e.initializer().ifPresent(inits -> {
                for (JavaExpression init : inits) {
                    children.add(toSyntaxNode(init));
                }
            });
            return SyntaxNode.of("ArrayCreation", e.elementType().fullTypeName(), e.span(), children);
        }

        if (node instanceof JavaObjectCreationExpression e) {
            List<SyntaxNode> children = e.arguments().stream()
                .map(JavaAstMapper::toSyntaxNode)
                .collect(Collectors.toList());
            return SyntaxNode.of("ObjectCreation", e.type().fullTypeName(), e.span(), children);
        }

        if (node instanceof JavaCastExpression e) {
            return SyntaxNode.of("CastExpression", e.type().fullTypeName(), e.span(), List.of(toSyntaxNode(e.expression())));
        }

        if (node instanceof JavaInstanceOfExpression e) {
            return SyntaxNode.of("InstanceOfExpression", "instanceof " + e.targetType().fullTypeName(), e.span(), List.of(toSyntaxNode(e.expression())));
        }

        if (node instanceof JavaTernaryExpression e) {
            List<SyntaxNode> children = List.of(toSyntaxNode(e.condition()), toSyntaxNode(e.thenExpr()), toSyntaxNode(e.elseExpr()));
            return SyntaxNode.of("TernaryExpression", "?:", e.span(), children);
        }

        if (node instanceof JavaLambdaExpression e) {
            List<SyntaxNode> children = new ArrayList<>();
            for (JavaParameter p : e.parameters()) {
                children.add(toSyntaxNode(p));
            }
            children.add(toSyntaxNode(e.body()));
            return SyntaxNode.of("LambdaExpression", "->", e.span(), children);
        }

        if (node instanceof JavaMethodReferenceExpression e) {
            return SyntaxNode.of("MethodReference", "::" + e.methodName(), e.span(), List.of(toSyntaxNode(e.target())));
        }

        if (node instanceof JavaParenthesizedExpression e) {
            return SyntaxNode.of("ParenthesizedExpression", "()", e.span(), List.of(toSyntaxNode(e.expression())));
        }

        if (node instanceof JavaThisExpression e) {
            String label = e.qualifier().map(q -> q.fullTypeName() + ".this").orElse("this");
            return SyntaxNode.leaf("ThisExpression", label, e.span());
        }

        if (node instanceof JavaSuperExpression e) {
            String label = e.qualifier().map(q -> q.fullTypeName() + ".super").orElse("super");
            return SyntaxNode.leaf("SuperExpression", label, e.span());
        }

        if (node instanceof JavaSwitchExpression e) {
            List<SyntaxNode> children = new ArrayList<>();
            children.add(toSyntaxNode(e.selector()));
            for (JavaSwitchEntry entry : e.entries()) {
                children.add(toSyntaxNode(entry));
            }
            return SyntaxNode.of("SwitchExpression", "switch", e.span(), children);
        }

        // Fallback for any other node
        return SyntaxNode.leaf("Node", node.getClass().getSimpleName(), node.span());
    }
}
