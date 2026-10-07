package com.writely.syntax_analyzer.core.analysis;

import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.adapter.ParserAdapter;
import com.writely.syntax_analyzer.adapter.ParserAdapterRegistry;
import com.writely.syntax_analyzer.adapter.RuleCapabilities;
import com.writely.syntax_analyzer.core.check.ControlStructureHeaderChecker;
import com.writely.syntax_analyzer.core.check.DelimiterMatchingChecker;
import com.writely.syntax_analyzer.core.check.IdentifierSyntaxChecker;
import com.writely.syntax_analyzer.core.check.OperatorSyntaxChecker;
import com.writely.syntax_analyzer.core.check.StatementTerminatorAndBlockChecker;
import com.writely.syntax_analyzer.core.check.StringAndCharacterLiteralChecker;
import com.writely.syntax_analyzer.core.check.SyntaxChecker;
import com.writely.syntax_analyzer.domain.AnalysisResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Severity;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Analysis orchestrator: the single entry point for a full analysis run.
 *
 * <p>{@link #analyze(SourcePayload)} resolves the language adapter from the
 * {@link ParserAdapterRegistry}, tokenizes, parses, runs only those
 * {@link SyntaxChecker}s whose {@link CheckCategory} is declared by the adapter's
 * {@link RuleCapabilities#supportedCategories()}, merges the {@link ParseResult}
 * diagnostics with the checker diagnostics, de-duplicates and orders the findings via
 * {@link DiagnosticAggregator}, and returns a complete in-memory {@link AnalysisResult}.
 * Nothing is serialized or written to disk.</p>
 *
 * <p>The orchestrator never throws for stage failures: every stage (adapter resolution,
 * tokenization, parsing, rule-capability resolution, and each individual checker) is
 * guarded, and a failure is converted into a {@link Severity#ERROR} diagnostic with the
 * reserved code {@link #ERR_INTERNAL_CHECK_FAILURE} while the run still returns a
 * complete {@link AnalysisResult} holding whatever diagnostics were produced.</p>
 *
 * <p><b>Internal-failure category choice:</b> {@link CheckCategory} has no {@code INTERNAL}
 * member (extending it is out of scope for this issue), so the reserved code
 * {@code ERR_INTERNAL_CHECK_FAILURE} is the distinguishing signal. A failure inside a
 * single checker is attributed to that checker's own {@link CheckCategory} so the finding
 * localizes to the rule area that broke; failures that cannot be attributed to one checker
 * (adapter resolution, tokenization, parsing, capability resolution) use
 * {@link CheckCategory#DELIMITER_MATCH} — the enum's ordinal-0 member — as the documented
 * default slot for unattributable internal failures.</p>
 */
public class AnalysisOrchestrator {

    /**
     * Reserved diagnostic code emitted whenever a pipeline stage fails internally.
     */
    public static final String ERR_INTERNAL_CHECK_FAILURE = "ERR_INTERNAL_CHECK_FAILURE";

    /**
     * Category used for internal failures that cannot be attributed to a single syntax checker.
     */
    static final CheckCategory PIPELINE_FAILURE_CATEGORY = CheckCategory.DELIMITER_MATCH;

    private final ParserAdapterRegistry registry;
    private final List<SyntaxChecker> checkers;

    /**
     * Creates an orchestrator backed by the default adapter registry and the full
     * set of syntax checkers.
     */
    public AnalysisOrchestrator() {
        this(ParserAdapterRegistry.createDefault(), defaultCheckers());
    }

    /**
     * Creates an orchestrator with an explicit adapter registry and checker set.
     */
    public AnalysisOrchestrator(ParserAdapterRegistry registry, List<SyntaxChecker> checkers) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(checkers, "checkers must not be null");
        this.registry = registry;
        this.checkers = List.copyOf(checkers);
    }

    /**
     * Runs the full analysis pipeline for the given payload.
     *
     * <p>Never throws for stage failures; stage failures become
     * {@link #ERR_INTERNAL_CHECK_FAILURE} diagnostics inside the returned result.</p>
     *
     * @param payload the ingested source payload
     * @return a complete in-memory analysis result
     */
    public AnalysisResult analyze(SourcePayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        List<Diagnostic> collected = new ArrayList<>();

        ParserAdapter adapter;
        try {
            adapter = registry.resolveRequiredAdapter(payload);
        } catch (Exception e) {
            collected.add(internalFailure("adapter resolution", e, PIPELINE_FAILURE_CATEGORY));
            return AnalysisResult.of(payload, List.of(), Optional.empty(),
                DiagnosticAggregator.aggregate(collected));
        }

        List<Token> tokens;
        try {
            List<Token> scanned = adapter.tokenize(payload);
            tokens = scanned == null ? List.of() : scanned;
        } catch (Exception e) {
            collected.add(internalFailure("tokenization", e, PIPELINE_FAILURE_CATEGORY));
            return AnalysisResult.of(payload, List.of(), Optional.empty(),
                DiagnosticAggregator.aggregate(collected));
        }

        Optional<SyntaxNode> syntaxTree = Optional.empty();
        try {
            ParseResult parseResult = adapter.parse(payload, tokens);
            if (parseResult != null) {
                syntaxTree = parseResult.syntaxTree();
                collected.addAll(parseResult.diagnostics());
            }
        } catch (Exception e) {
            collected.add(internalFailure("parsing", e, PIPELINE_FAILURE_CATEGORY));
        }

        collectCheckerDiagnostics(adapter, payload, tokens, collected);

        return AnalysisResult.of(payload, tokens, syntaxTree, DiagnosticAggregator.aggregate(collected));
    }

    /**
     * Runs every checker whose category the adapter's rule capabilities declare,
     * guarding each checker individually.
     */
    private void collectCheckerDiagnostics(
        ParserAdapter adapter,
        SourcePayload payload,
        List<Token> tokens,
        List<Diagnostic> collected
    ) {
        RuleCapabilities capabilities;
        try {
            capabilities = adapter.ruleCapabilities();
        } catch (Exception e) {
            collected.add(internalFailure("rule capability resolution", e, PIPELINE_FAILURE_CATEGORY));
            return;
        }
        if (capabilities == null) {
            return;
        }

        for (SyntaxChecker checker : checkers) {
            CheckCategory category;
            try {
                category = checker.category();
            } catch (Exception e) {
                collected.add(internalFailure("checker category resolution", e, PIPELINE_FAILURE_CATEGORY));
                continue;
            }
            if (category == null || !capabilities.supportsCategory(category)) {
                continue;
            }
            try {
                List<Diagnostic> findings = checker.check(payload, tokens);
                if (findings != null) {
                    collected.addAll(findings);
                }
            } catch (Exception e) {
                collected.add(internalFailure("syntax check (" + category.name() + ")", e, category));
            }
        }
    }

    private Diagnostic internalFailure(String stage, Exception cause, CheckCategory category) {
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = cause.getClass().getName();
        }
        return Diagnostic.of(
            category,
            Severity.ERROR,
            SourceLocation.start(),
            "Internal analysis failure during " + stage + ": " + detail,
            ERR_INTERNAL_CHECK_FAILURE
        );
    }

    /**
     * The full set of syntax checkers run by the default orchestrator.
     */
    static List<SyntaxChecker> defaultCheckers() {
        return List.of(
            new DelimiterMatchingChecker(),
            new StringAndCharacterLiteralChecker(),
            new StatementTerminatorAndBlockChecker(),
            new OperatorSyntaxChecker(),
            new ControlStructureHeaderChecker(),
            new IdentifierSyntaxChecker()
        );
    }
}
