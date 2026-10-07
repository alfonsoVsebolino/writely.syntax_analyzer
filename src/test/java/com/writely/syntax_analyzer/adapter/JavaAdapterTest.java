package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.adapter.java.ast.*;
import com.writely.syntax_analyzer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaAdapter and JavaParser Tests")
class JavaAdapterTest {

    private JavaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JavaAdapter();
    }

    private String loadFixture(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + path, e);
        }
    }

    @Nested
    @DisplayName("Contract and Registry Integration")
    class ContractAndRegistryTests {

        @Test
        @DisplayName("JavaAdapter exposes correct contract metadata")
        void testContractMetadata() {
            assertEquals(Language.JAVA, adapter.language());
            assertEquals("Java", adapter.displayName());
            assertTrue(adapter.supportedExtensions().contains(".java"));
            assertTrue(adapter.supportsExtension(".java"));
            assertTrue(adapter.supportsExtension("java"));
            assertFalse(adapter.supportsExtension(".py"));
            assertFalse(adapter.supportsExtension(".cpp"));

            RuleCapabilities caps = adapter.ruleCapabilities();
            assertTrue(caps.requiresStatementTerminators());
            assertFalse(caps.supportsIndentationBlocks());
            assertTrue(caps.isKeyword("record"));
            assertTrue(caps.isControlKeyword("switch"));
        }

        @Test
        @DisplayName("JavaAdapter plugs into ParserAdapterRegistry seamlessly")
        void testRegistryIntegration() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.createDefault();
            Optional<ParserAdapter> resolved = registry.resolveAdapter(Language.JAVA, "App.java");
            assertTrue(resolved.isPresent());
            assertInstanceOf(JavaAdapter.class, resolved.get());

            Optional<ParserAdapter> resolvedByExt = registry.getAdapterForExtension(".java");
            assertTrue(resolvedByExt.isPresent());
            assertEquals(Language.JAVA, resolvedByExt.get().language());
        }

        @Test
        @DisplayName("Empty source produces empty compilation unit")
        void testEmptySource() {
            SourcePayload payload = SourcePayload.snippet("", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            assertFalse(result.hasErrors());
            assertTrue(result.hasSyntaxTree());
            assertEquals("CompilationUnit", result.syntaxTree().get().kind());
            assertEquals(0, result.syntaxTree().get().childCount());

            assertTrue(result.hasNativeSyntaxTree());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            assertTrue(cu.declarations().isEmpty());
        }
    }

    @Nested
    @DisplayName("Valid Java Programs - Multi-Modal Ingestion")
    class ValidProgramTests {

        @Test
        @DisplayName("Input Mode 1: Single command statement")
        void testMode1SingleCommand() {
            SourcePayload payload = SourcePayload.singleLine("System.out.println(\"Hello, World!\");", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            assertFalse(result.hasErrors());
            assertTrue(result.hasSyntaxTree());
            SyntaxNode root = result.syntaxTree().get();
            assertEquals("CompilationUnit", root.kind());
            assertEquals(1, root.childCount());

            SyntaxNode stmtNode = root.children().get(0);
            assertEquals("ExpressionStatement", stmtNode.kind());
            assertEquals("expr", stmtNode.label());
        }

        @Test
        @DisplayName("Input Mode 2: Multi-line code snippet")
        void testMode2Snippet() {
            String snippet = """
                int x = 10;
                int y = 20;
                if (x < y) {
                    x += 5;
                }
                """;
            SourcePayload payload = SourcePayload.snippet(snippet, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            assertFalse(result.hasErrors());
            SyntaxNode root = result.syntaxTree().get();
            assertEquals(3, root.childCount());
            assertEquals("VariableDeclaration", root.children().get(0).kind());
            assertEquals("VariableDeclaration", root.children().get(1).kind());
            assertEquals("IfStatement", root.children().get(2).kind());
        }

        @Test
        @DisplayName("Input Mode 3: Complete compilation unit from fixture file")
        void testMode3CompleteProgramFixture() {
            String code = loadFixture("/fixtures/java/valid/CompleteProgram.java");
            SourcePayload payload = SourcePayload.file(code, "CompleteProgram.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful(), "Result should be successful: " + result.diagnostics());
            assertFalse(result.hasErrors());
            assertTrue(result.hasNativeSyntaxTree());

            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            assertTrue(cu.packageDeclaration().isPresent());
            assertEquals("com.example.service", cu.packageDeclaration().get().packageName());
            assertEquals(4, cu.imports().size());
            assertEquals(1, cu.declarations().size());

            JavaClassDeclaration cls = assertInstanceOf(JavaClassDeclaration.class, cu.declarations().get(0));
            assertEquals("CompleteProgram", cls.name());
            assertTrue(cls.modifiers().contains("public"));
            assertEquals(5, cls.members().size()); // 2 fields, 1 constructor, 2 methods
        }

        @Test
        @DisplayName("Control flow and expressions fixture")
        void testControlFlowFixture() {
            String code = loadFixture("/fixtures/java/valid/ControlFlowAndExpressions.java");
            SourcePayload payload = SourcePayload.file(code, "ControlFlowAndExpressions.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful(), "Failed with diagnostics: " + result.diagnostics());
            assertFalse(result.hasErrors());

            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaClassDeclaration cls = assertInstanceOf(JavaClassDeclaration.class, cu.declarations().get(0));
            assertEquals("ControlFlowAndExpressions", cls.name());
        }

        @Test
        @DisplayName("Modern Java constructs fixture (records, interfaces, switch rules, lambdas)")
        void testModernJavaConstructsFixture() {
            String code = loadFixture("/fixtures/java/valid/ModernJavaConstructs.java");
            SourcePayload payload = SourcePayload.file(code, "ModernJavaConstructs.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful(), "Failed with diagnostics: " + result.diagnostics());
            assertFalse(result.hasErrors());

            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaClassDeclaration cls = assertInstanceOf(JavaClassDeclaration.class, cu.declarations().get(0));
            assertEquals("ModernJavaConstructs", cls.name());

            // Check record member
            JavaRecordDeclaration rec = cls.members().stream()
                .filter(m -> m instanceof JavaRecordDeclaration)
                .map(m -> (JavaRecordDeclaration) m)
                .findFirst().orElseThrow();
            assertEquals("UserRecord", rec.name());
            assertEquals(3, rec.components().size());

            // Check interface member
            JavaInterfaceDeclaration iface = cls.members().stream()
                .filter(m -> m instanceof JavaInterfaceDeclaration)
                .map(m -> (JavaInterfaceDeclaration) m)
                .findFirst().orElseThrow();
            assertEquals("Formatter", iface.name());
            assertEquals(1, iface.typeParameters().size());
            assertEquals("T", iface.typeParameters().get(0).name());
        }
    }

    @Nested
    @DisplayName("Language Construct Coverage Tests")
    class LanguageConstructCoverageTests {

        @Test
        @DisplayName("Parses classes with generics, extends, implements, and permits")
        void testClassHierarchyAndGenerics() {
            String src = """
                public sealed class Container<T extends Comparable<T>> extends BaseContainer implements Iterable<T>, Serializable permits SubContainer {
                    private T data;
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaClassDeclaration cls = assertInstanceOf(JavaClassDeclaration.class, cu.declarations().get(0));
            assertEquals("Container", cls.name());
            assertTrue(cls.modifiers().containsAll(List.of("public", "sealed")));
            assertEquals(1, cls.typeParameters().size());
            assertEquals("T", cls.typeParameters().get(0).name());
            assertTrue(cls.superclass().isPresent());
            assertEquals("BaseContainer", cls.superclass().get().name());
            assertEquals(2, cls.interfaces().size());
            assertEquals(1, cls.permittedSubclasses().size());
            assertEquals("SubContainer", cls.permittedSubclasses().get(0).name());
        }

        @Test
        @DisplayName("Parses enum with constants, constructor, and method")
        void testEnumDeclaration() {
            String src = """
                public enum Priority {
                    LOW(1), MEDIUM(2), HIGH(3);

                    private final int level;

                    Priority(int level) {
                        this.level = level;
                    }

                    public int getLevel() {
                        return this.level;
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaEnumDeclaration enm = assertInstanceOf(JavaEnumDeclaration.class, cu.declarations().get(0));
            assertEquals("Priority", enm.name());
            assertEquals(3, enm.constants().size());
            assertEquals("LOW", enm.constants().get(0).name());
            assertEquals(1, enm.constants().get(0).arguments().size());
        }

        @Test
        @DisplayName("Parses initializer blocks (static and instance)")
        void testInitializerBlocks() {
            String src = """
                class Initializers {
                    static {
                        System.out.println("static init");
                    }
                    {
                        System.out.println("instance init");
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaClassDeclaration cls = assertInstanceOf(JavaClassDeclaration.class, cu.declarations().get(0));
            assertEquals(2, cls.members().size());

            JavaInitializerBlock staticInit = assertInstanceOf(JavaInitializerBlock.class, cls.members().get(0));
            assertTrue(staticInit.isStatic());

            JavaInitializerBlock instanceInit = assertInstanceOf(JavaInitializerBlock.class, cls.members().get(1));
            assertFalse(instanceInit.isStatic());
        }

        @Test
        @DisplayName("Parses methods with varargs, throws clause, and annotations")
        void testMethodsWithSignatures() {
            String src = """
                class Service {
                    @Override
                    public final <R> R execute(String format, Object... args) throws IOException, SQLException {
                        return null;
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaClassDeclaration cls = assertInstanceOf(JavaClassDeclaration.class, cu.declarations().get(0));
            JavaMethodDeclaration method = assertInstanceOf(JavaMethodDeclaration.class, cls.members().get(0));

            assertEquals("execute", method.name());
            assertTrue(method.modifiers().containsAll(List.of("public", "final")));
            assertEquals(1, method.annotations().size());
            assertEquals("Override", method.annotations().get(0).name());
            assertEquals(1, method.typeParameters().size());
            assertEquals(2, method.parameters().size());
            assertTrue(method.parameters().get(1).isVarargs());
            assertEquals(2, method.thrownExceptions().size());
            assertTrue(method.body().isPresent());
        }

        @Test
        @DisplayName("Parses try-with-resources, multiple catch clauses, and finally")
        void testExceptionHandlingConstructs() {
            String src = """
                try (BufferedReader reader = new BufferedReader(new FileReader("file.txt"))) {
                    String line = reader.readLine();
                } catch (FileNotFoundException e) {
                    System.err.println("File not found");
                } catch (IOException e) {
                    System.err.println("IO error");
                } finally {
                    System.out.println("done");
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            JavaTryStatement tryStmt = assertInstanceOf(JavaTryStatement.class, cu.declarations().get(0));

            assertEquals(1, tryStmt.resources().size());
            assertEquals(2, tryStmt.catchClauses().size());
            assertEquals("FileNotFoundException", tryStmt.catchClauses().get(0).parameter().type().name());
            assertEquals("IOException", tryStmt.catchClauses().get(1).parameter().type().name());
            assertTrue(tryStmt.finallyBlock().isPresent());
        }

        @Test
        @DisplayName("Parses loops (classic for, enhanced for, while, do-while)")
        void testLoopConstructs() {
            String src = """
                for (int i = 0; i < 10; i++) {
                    if (i == 5) continue;
                }
                for (String s : list) {
                    if (s.isEmpty()) break;
                }
                while (active) {
                    run();
                }
                do {
                    retry();
                } while (count > 0);
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            assertEquals(4, cu.declarations().size());

            assertInstanceOf(JavaForStatement.class, cu.declarations().get(0));
            assertInstanceOf(JavaEnhancedForStatement.class, cu.declarations().get(1));
            assertInstanceOf(JavaWhileStatement.class, cu.declarations().get(2));
            assertInstanceOf(JavaDoWhileStatement.class, cu.declarations().get(3));
        }

        @Test
        @DisplayName("Parses full expression variety: binary, unary, ternary, lambdas, method refs, casts, new")
        void testExpressionVariety() {
            String src = """
                int a = (1 + 2) * 3 / 4 % 5;
                boolean b = !(a > 0 && a <= 10 || a != 20);
                int c = a > 5 ? a : 0;
                String d = (String) obj;
                boolean e = obj instanceof String s;
                int[] arr = new int[10];
                int[] arr2 = new int[] { 1, 2, 3 };
                List<String> list = new ArrayList<>();
                Runnable r = () -> System.out.println("run");
                Function<Integer, Integer> f = x -> x * 2;
                Consumer<String> printer = System.out::println;
                int x = a++;
                int y = --b;
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful(), "Expressions failed with: " + result.diagnostics());
            JavaCompilationUnit cu = result.nativeSyntaxTree(JavaCompilationUnit.class).orElseThrow();
            assertEquals(13, cu.declarations().size());
        }

        @Test
        @DisplayName("SyntaxNode preorder walking traverses full hierarchy")
        void testSyntaxNodeTraversal() {
            String src = """
                public class Demo {
                    public void greet(String name) {
                        System.out.println("Hello " + name);
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.isSuccessful());
            SyntaxNode root = result.syntaxTree().orElseThrow();

            AtomicInteger nodeCount = new AtomicInteger(0);
            root.walkPreorder(node -> {
                assertNotNull(node.kind());
                assertNotNull(node.label());
                assertNotNull(node.span());
                nodeCount.incrementAndGet();
            });

            assertTrue(nodeCount.get() >= 5, "Tree should have at least 5 nodes, got: " + nodeCount.get());
        }
    }

    @Nested
    @DisplayName("Invalid Java Programs - Error Detection & Diagnostics")
    class InvalidProgramTests {

        @Test
        @DisplayName("Flags missing semicolon on variable declaration")
        void testMissingSemicolonVariable() {
            String src = """
                public class Test {
                    public void run() {
                        int x = 10
                        int y = 20;
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            assertFalse(result.isSuccessful());
            assertEquals(1, result.errorCount());

            Diagnostic diag = result.diagnostics().get(0);
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, diag.category());
            assertEquals("ERR_JAVA_MISSING_SEMICOLON", diag.code());
            assertEquals(3, diag.location().line());
            assertTrue(diag.suggestedFix().isPresent());
        }

        @Test
        @DisplayName("Missing semicolon fixture")
        void testMissingSemicolonFixture() {
            String code = loadFixture("/fixtures/java/invalid/MissingSemicolon.java");
            SourcePayload payload = SourcePayload.file(code, "MissingSemicolon.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            assertEquals(1, result.errorCount());
            Diagnostic diag = result.diagnostics().get(0);
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, diag.category());
            assertEquals("ERR_JAVA_MISSING_SEMICOLON", diag.code());
        }

        @Test
        @DisplayName("Flags unclosed parenthesis in if statement")
        void testUnclosedParenthesis() {
            String src = """
                public class Test {
                    public void run(int x) {
                        if (x > 0 {
                            System.out.println("err");
                        }
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            Diagnostic diag = result.diagnostics().stream()
                .filter(d -> d.category() == CheckCategory.DELIMITER_MATCH)
                .findFirst().orElseThrow();
            assertEquals("ERR_JAVA_UNCLOSED_PARENTHESIS", diag.code());
            assertEquals(3, diag.location().line());
        }

        @Test
        @DisplayName("Unclosed delimiters fixture")
        void testUnclosedDelimitersFixture() {
            String code = loadFixture("/fixtures/java/invalid/UnclosedDelimiters.java");
            SourcePayload payload = SourcePayload.file(code, "UnclosedDelimiters.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.category() == CheckCategory.DELIMITER_MATCH));
        }

        @Test
        @DisplayName("Flags malformed control structure header missing open parenthesis")
        void testMalformedControlHeader() {
            String src = """
                public class Test {
                    public void run(int x) {
                        while x > 0) {
                            x--;
                        }
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            Diagnostic diag = result.diagnostics().stream()
                .filter(d -> d.category() == CheckCategory.CONTROL_HEADER)
                .findFirst().orElseThrow();
            assertEquals("ERR_JAVA_INVALID_CONTROL_HEADER", diag.code());
            assertEquals(3, diag.location().line());
        }

        @Test
        @DisplayName("Malformed control header fixture")
        void testMalformedControlHeaderFixture() {
            String code = loadFixture("/fixtures/java/invalid/MalformedControlHeader.java");
            SourcePayload payload = SourcePayload.file(code, "MalformedControlHeader.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.category() == CheckCategory.CONTROL_HEADER));
        }

        @Test
        @DisplayName("Flags invalid consecutive operator sequence")
        void testInvalidOperatorSequence() {
            String src = """
                public class Test {
                    public void run() {
                        int x = 10 + * 5;
                    }
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            Diagnostic diag = result.diagnostics().stream()
                .filter(d -> d.category() == CheckCategory.OPERATOR_SYNTAX)
                .findFirst().orElseThrow();
            assertEquals("ERR_JAVA_OPERATOR_SYNTAX", diag.code());
            assertEquals(3, diag.location().line());
        }

        @Test
        @DisplayName("Invalid operator sequence fixture")
        void testInvalidOperatorSequenceFixture() {
            String code = loadFixture("/fixtures/java/invalid/InvalidOperatorSequence.java");
            SourcePayload payload = SourcePayload.file(code, "InvalidOperatorSequence.java", Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.category() == CheckCategory.OPERATOR_SYNTAX));
        }

        @Test
        @DisplayName("Flags missing class name identifier")
        void testMissingClassIdentifier() {
            String src = """
                public class {
                    int x;
                }
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            Diagnostic diag = result.diagnostics().stream()
                .filter(d -> d.category() == CheckCategory.IDENTIFIER_NAMING)
                .findFirst().orElseThrow();
            assertEquals("ERR_JAVA_EXPECTED_IDENTIFIER", diag.code());
        }

        @Test
        @DisplayName("Parser recovers after syntax error and continues parsing subsequent statements")
        void testErrorRecovery() {
            String src = """
                int a = 10
                int b = 20;
                int c = 30;
                """;
            SourcePayload payload = SourcePayload.snippet(src, Language.JAVA);
            ParseResult result = adapter.parse(payload);

            assertTrue(result.hasErrors());
            // Exactly 1 error for missing semicolon on line 1
            assertEquals(1, result.errorCount());

            // Tree still contains the parsed declarations
            SyntaxNode root = result.syntaxTree().orElseThrow();
            assertEquals(3, root.childCount());
        }
    }
}
