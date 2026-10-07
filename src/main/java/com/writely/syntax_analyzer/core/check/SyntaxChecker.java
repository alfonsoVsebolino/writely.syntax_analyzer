package com.writely.syntax_analyzer.core.check;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;

import java.util.List;

/**
 * Contract for syntax checking components analyzing source payloads and token streams.
 */
public interface SyntaxChecker {

    /**
     * The syntax check category performed by this checker.
     *
     * @return the check category
     */
    CheckCategory category();

    /**
     * Inspects the source payload and token stream for syntactic defects.
     *
     * @param payload the ingested source payload, must not be null
     * @param tokens  the token stream produced by tokenization, must not be null
     * @return list of diagnostics found
     */
    List<Diagnostic> check(SourcePayload payload, List<Token> tokens);
}
