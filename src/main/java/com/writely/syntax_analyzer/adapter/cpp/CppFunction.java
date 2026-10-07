package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a C++ function, method, constructor, or destructor declaration/definition.
 */
public record CppFunction(
    String returnType,
    String name,
    List<CppParam> parameters,
    List<String> qualifiers,
    CppStatement body,
    boolean isDefinition,
    boolean isConstructor,
    boolean isDestructor,
    List<String> memberInitializers,
    SourceSpan span
) implements CppAstNode {

    public CppFunction {
        returnType = returnType == null ? "" : returnType;
        name = name == null ? "" : name;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
        memberInitializers = memberInitializers == null ? List.of() : List.copyOf(memberInitializers);
        Objects.requireNonNull(span, "span must not be null");
    }

    @Override
    public SyntaxNode toSyntaxNode() {
        String nodeKind = isDefinition ? "FunctionDefinition" : "FunctionDeclaration";
        String label = name.isBlank() ? "(anonymous)" : name;

        List<SyntaxNode> children = new ArrayList<>();

        // Parameter list node
        SourceSpan paramSpan = parameters.isEmpty()
            ? SourceSpan.point(span.start())
            : SourceSpan.of(parameters.get(0).span().start(), parameters.get(parameters.size() - 1).span().end());
        List<SyntaxNode> paramNodes = parameters.stream().map(CppParam::toSyntaxNode).toList();
        children.add(SyntaxNode.of("ParameterList", "parameters", paramSpan, paramNodes));

        if (body != null) {
            children.add(body.toSyntaxNode());
        }

        Map<String, String> attrs = new HashMap<>();
        attrs.put("name", label);
        if (!returnType.isBlank()) {
            attrs.put("returnType", returnType);
        }
        if (!qualifiers.isEmpty()) {
            attrs.put("qualifiers", String.join(" ", qualifiers));
        }
        if (isConstructor) {
            attrs.put("isConstructor", "true");
        }
        if (isDestructor) {
            attrs.put("isDestructor", "true");
        }
        if (!memberInitializers.isEmpty()) {
            attrs.put("initializers", String.join(", ", memberInitializers));
        }

        return SyntaxNode.of(nodeKind, label, span, children, attrs);
    }
}
