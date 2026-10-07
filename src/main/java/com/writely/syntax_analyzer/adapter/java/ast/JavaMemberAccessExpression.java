package com.writely.syntax_analyzer.adapter.java.ast;

import com.writely.syntax_analyzer.domain.SourceSpan;

import java.util.Objects;

/**
 * Represents a member/field dereference expression ({@code target.memberName}).
 */
public record JavaMemberAccessExpression(
    JavaExpression target,
    String memberName,
    SourceSpan span
) implements JavaExpression {
    public JavaMemberAccessExpression {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(memberName, "memberName must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }
}
