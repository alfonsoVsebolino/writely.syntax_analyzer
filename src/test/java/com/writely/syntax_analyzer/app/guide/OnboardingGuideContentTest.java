package com.writely.syntax_analyzer.app.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnboardingGuideContentTest {

    @Test
    @DisplayName("GuideDocument contains exactly five canonical sections in fixed order")
    void testCanonicalSectionsOrderAndTitles() {
        GuideDocument document = OnboardingGuideContent.getDocument();
        assertNotNull(document);
        List<GuideSection> sections = document.sections();
        assertEquals(5, sections.size(), "Document must contain exactly 5 sections");

        assertEquals("What writely Is and Is Not", sections.get(0).title());
        assertEquals("Input Modes", sections.get(1).title());
        assertEquals("Check Categories", sections.get(2).title());
        assertEquals("Reading the Analysis Report", sections.get(3).title());
        assertEquals("Ingestion Limits and Supported Files", sections.get(4).title());
    }

    @Test
    @DisplayName("Section 1 documents static analysis scope, PASSED meaning, and HelloWorld(print) semantic boundary")
    void testSectionOneContent() {
        GuideSection section = OnboardingGuideContent.getDocument().sections().get(0);
        assertEquals("What writely Is and Is Not", section.title());

        boolean mentionsStaticOnly = false;
        boolean mentionsNeverExecutes = false;
        boolean mentionsPassedMeaning = false;
        boolean hasHelloWorldSnippet = false;
        boolean mentionsUndefinedAtRuntime = false;

        for (GuideBlock block : section.blocks()) {
            if (block instanceof ParagraphBlock p) {
                String text = p.text();
                if (text.contains("static syntax analysis only")) mentionsStaticOnly = true;
                if (text.contains("never executes")) mentionsNeverExecutes = true;
                if (text.contains("PASSED") && text.contains("syntactically valid")) mentionsPassedMeaning = true;
                if (text.contains("fails at runtime") && text.contains("undefined")) mentionsUndefinedAtRuntime = true;
            } else if (block instanceof SnippetBlock s) {
                if (s.code().equals("HelloWorld(print)")) hasHelloWorldSnippet = true;
            }
        }

        assertTrue(mentionsStaticOnly, "Must mention static syntax analysis only");
        assertTrue(mentionsNeverExecutes, "Must state it never executes analyzed code");
        assertTrue(mentionsPassedMeaning, "Must explain that PASSED means syntactically valid");
        assertTrue(hasHelloWorldSnippet, "Must include HelloWorld(print) snippet");
        assertTrue(mentionsUndefinedAtRuntime, "Must explain semantic failure at runtime for undefined name");
    }

    @Test
    @DisplayName("Section 2 covers the three input modes from GLOSSARY.md")
    void testSectionTwoInputModes() {
        GuideSection section = OnboardingGuideContent.getDocument().sections().get(1);
        assertEquals("Input Modes", section.title());

        boolean hasSingleLine = false;
        boolean hasCodeSnippet = false;
        boolean hasFileUpload = false;

        for (GuideBlock block : section.blocks()) {
            if (block instanceof BulletListBlock bulletList) {
                for (String item : bulletList.items()) {
                    if (item.contains("Single Line")) hasSingleLine = true;
                    if (item.contains("Code Snippet")) hasCodeSnippet = true;
                    if (item.contains("File Upload")) hasFileUpload = true;
                }
            }
        }

        assertTrue(hasSingleLine, "Must explain Single Line input mode");
        assertTrue(hasCodeSnippet, "Must explain Code Snippet input mode");
        assertTrue(hasFileUpload, "Must explain File Upload input mode");
    }

    @Test
    @DisplayName("Section 3 covers the six check categories and language-specificity caveat")
    void testSectionThreeCheckCategories() {
        GuideSection section = OnboardingGuideContent.getDocument().sections().get(2);
        assertEquals("Check Categories", section.title());

        boolean hasDelimiter = false;
        boolean hasLiteral = false;
        boolean hasTerminator = false;
        boolean hasOperator = false;
        boolean hasControlHeader = false;
        boolean hasIdentifier = false;
        boolean mentionsLanguageCaveat = false;

        for (GuideBlock block : section.blocks()) {
            if (block instanceof BulletListBlock bulletList) {
                for (String item : bulletList.items()) {
                    if (item.contains("DELIMITER_MATCH")) hasDelimiter = true;
                    if (item.contains("LITERAL_SYNTAX")) hasLiteral = true;
                    if (item.contains("STATEMENT_TERMINATOR")) hasTerminator = true;
                    if (item.contains("OPERATOR_SYNTAX")) hasOperator = true;
                    if (item.contains("CONTROL_HEADER")) hasControlHeader = true;
                    if (item.contains("IDENTIFIER_NAMING")) hasIdentifier = true;
                }
            } else if (block instanceof ParagraphBlock p) {
                if (p.text().toLowerCase().contains("caveat") || p.text().contains("language differences")) {
                    mentionsLanguageCaveat = true;
                }
            }
        }

        assertTrue(hasDelimiter, "Must list DELIMITER_MATCH");
        assertTrue(hasLiteral, "Must list LITERAL_SYNTAX");
        assertTrue(hasTerminator, "Must list STATEMENT_TERMINATOR");
        assertTrue(hasOperator, "Must list OPERATOR_SYNTAX");
        assertTrue(hasControlHeader, "Must list CONTROL_HEADER");
        assertTrue(hasIdentifier, "Must list IDENTIFIER_NAMING");
        assertTrue(mentionsLanguageCaveat, "Must mention language-specificity caveat");
    }

    @Test
    @DisplayName("Section 4 covers status pill, metric insights, diagnostics rows, and line jump")
    void testSectionFourReadingReport() {
        GuideSection section = OnboardingGuideContent.getDocument().sections().get(3);
        assertEquals("Reading the Analysis Report", section.title());

        boolean hasStatusPill = false;
        boolean hasMetricInsights = false;
        boolean hasDiagnosticsRows = false;
        boolean hasLineJump = false;

        for (GuideBlock block : section.blocks()) {
            if (block instanceof BulletListBlock bulletList) {
                for (String item : bulletList.items()) {
                    if (item.contains("Status Pill")) hasStatusPill = true;
                    if (item.contains("Metric Insights")) hasMetricInsights = true;
                    if (item.contains("Diagnostics Rows")) hasDiagnosticsRows = true;
                    if (item.contains("Line Jump")) hasLineJump = true;
                }
            }
        }

        assertTrue(hasStatusPill, "Must explain Status Pill");
        assertTrue(hasMetricInsights, "Must explain Metric Insights");
        assertTrue(hasDiagnosticsRows, "Must explain Diagnostics Rows");
        assertTrue(hasLineJump, "Must explain Line Jump");
    }

    @Test
    @DisplayName("Section 5 covers supported files (.py, .java, .cpp, .h, etc.), 15 MB cap, and binary rejection")
    void testSectionFiveIngestionLimits() {
        GuideSection section = OnboardingGuideContent.getDocument().sections().get(4);
        assertEquals("Ingestion Limits and Supported Files", section.title());

        boolean hasSupportedFiles = false;
        boolean hasSizeLimit = false;
        boolean hasBinaryRejection = false;

        for (GuideBlock block : section.blocks()) {
            if (block instanceof BulletListBlock bulletList) {
                for (String item : bulletList.items()) {
                    if (item.contains("Supported Files") && item.contains(".py") && item.contains(".java") && item.contains(".cpp")) {
                        hasSupportedFiles = true;
                    }
                    if (item.contains("15 MB")) hasSizeLimit = true;
                    if (item.contains("Binary") && item.contains("rejected")) hasBinaryRejection = true;
                }
            }
        }

        assertTrue(hasSupportedFiles, "Must list supported files");
        assertTrue(hasSizeLimit, "Must mention 15 MB limit cap");
        assertTrue(hasBinaryRejection, "Must mention binary rejection");
    }

    @Test
    @DisplayName("Guide content models enforce immutability and non-null constraints")
    void testContentModelImmutability() {
        assertThrows(NullPointerException.class, () -> new ParagraphBlock(null));
        assertThrows(NullPointerException.class, () -> new SnippetBlock(null));
        assertThrows(NullPointerException.class, () -> new BulletListBlock((List<String>) null));
        assertThrows(NullPointerException.class, () -> new GuideSection(null, List.of()));
        assertThrows(NullPointerException.class, () -> new GuideSection("Title", (List<GuideBlock>) null));
        assertThrows(NullPointerException.class, () -> new GuideDocument((List<GuideSection>) null));

        BulletListBlock bulletList = new BulletListBlock(List.of("A", "B"));
        assertThrows(UnsupportedOperationException.class, () -> bulletList.items().add("C"));

        GuideSection section = new GuideSection("Title", List.of(new ParagraphBlock("Text")));
        assertThrows(UnsupportedOperationException.class, () -> section.blocks().add(new ParagraphBlock("Other")));

        GuideDocument doc = new GuideDocument(List.of(section));
        assertThrows(UnsupportedOperationException.class, () -> doc.sections().add(section));
    }
}
