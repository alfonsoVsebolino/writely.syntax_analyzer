package com.writely.syntax_analyzer.core.analysis;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;

import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure diagnostic aggregation: merges findings, removes exact duplicates, and orders
 * the surviving findings by {@code (line asc, column asc, CheckCategory ordinal, code asc)}.
 *
 * <p>A duplicate is defined strictly as a diagnostic equal on
 * {@code (category, code, line, column, message)}; findings that differ in any of those
 * fields — including findings from different categories at the same location — are
 * independent and all survive. The function is stateless and idempotent:
 * {@code aggregate(aggregate(x)) == aggregate(x)}.</p>
 */
public final class DiagnosticAggregator {

    private DiagnosticAggregator() {
    }

    /**
     * Stable output order consumed by report export and GUI display.
     */
    public static Comparator<Diagnostic> order() {
        return ORDER;
    }

    /**
     * De-duplicates exact duplicates and sorts the findings into the stable output order.
     *
     * @param diagnostics findings to aggregate; {@code null} entries are ignored
     * @return immutable, de-duplicated, ordered findings
     */
    public static List<Diagnostic> aggregate(Collection<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return List.of();
        }
        List<Diagnostic> deduplicated = new ArrayList<>(diagnostics.size());
        Set<DuplicateKey> seen = new HashSet<>();
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic == null) {
                continue;
            }
            if (seen.add(new DuplicateKey(diagnostic))) {
                deduplicated.add(diagnostic);
            }
        }
        deduplicated.sort(ORDER);
        return List.copyOf(deduplicated);
    }

    private static final Comparator<Diagnostic> ORDER =
        Comparator.comparingInt((Diagnostic diagnostic) -> diagnostic.location().line())
            .thenComparingInt(diagnostic -> diagnostic.location().column())
            .thenComparingInt(diagnostic -> diagnostic.category().ordinal())
            .thenComparing(Comparator.comparing(Diagnostic::code));

    private record DuplicateKey(CheckCategory category, String code, int line, int column, String message) {
        private DuplicateKey(Diagnostic diagnostic) {
            this(
                diagnostic.category(),
                diagnostic.code(),
                diagnostic.location().line(),
                diagnostic.location().column(),
                diagnostic.message()
            );
        }
    }
}
