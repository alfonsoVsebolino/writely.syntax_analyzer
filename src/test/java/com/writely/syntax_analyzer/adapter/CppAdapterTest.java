package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.adapter.cpp.CppClass;
import com.writely.syntax_analyzer.adapter.cpp.CppFunction;
import com.writely.syntax_analyzer.adapter.cpp.CppInclude;
import com.writely.syntax_analyzer.adapter.cpp.CppNamespace;
import com.writely.syntax_analyzer.adapter.cpp.CppParser;
import com.writely.syntax_analyzer.adapter.cpp.CppTemplate;
import com.writely.syntax_analyzer.adapter.cpp.CppTranslationUnit;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CppAdapter and CppParser Tests")
class CppAdapterTest {

    private CppAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CppAdapter();
    }

    @Nested
    @DisplayName("Valid C++ Translation Units")
    class ValidTranslationUnits {

        @Test
        @DisplayName("Parses preprocessor directives and include statements")
        void testIncludesAndPreprocessor() {
            String code = """
                #include <iostream>
                #include "custom/header.hpp"
                #define BUFFER_SIZE 1024
                int main() {
                    return 0;
                }
                """;
            ParseResult result = adapter.parse(SourcePayload.file(code, "main.cpp", Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            assertTrue(result.hasSyntaxTree());
            SyntaxNode root = result.syntaxTree().get();
            assertEquals("TranslationUnit", root.kind());

            List<SyntaxNode> includes = root.children().stream()
                .filter(c -> "IncludeDirective".equals(c.kind()))
                .toList();
            assertEquals(2, includes.size());
            assertEquals("#include <iostream>", includes.get(0).label());
            assertEquals("system", includes.get(0).attribute("type").orElseThrow());
            assertEquals("#include \"custom/header.hpp\"", includes.get(1).label());
            assertEquals("local", includes.get(1).attribute("type").orElseThrow());

            assertTrue(root.children().stream().anyMatch(c -> "PreprocessorDirective".equals(c.kind())));
            assertTrue(root.children().stream().anyMatch(c -> "FunctionDefinition".equals(c.kind())));
        }

        @Test
        @DisplayName("Parses named, nested, and anonymous namespaces with using directives")
        void testNamespaces() {
            String code = """
                namespace math {
                    int add(int a, int b) { return a + b; }
                }
                namespace physics::mechanics {
                    double speed = 9.8;
                }
                namespace {
                    int internal_key = 42;
                }
                using namespace math;
                using RealNumber = double;
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            List<SyntaxNode> namespaces = root.children().stream()
                .filter(c -> "NamespaceDeclaration".equals(c.kind()))
                .toList();
            assertEquals(3, namespaces.size());
            assertEquals("math", namespaces.get(0).label());
            assertEquals("physics::mechanics", namespaces.get(1).label());
            assertEquals("(anonymous)", namespaces.get(2).label());

            assertTrue(root.children().stream().anyMatch(c -> "UsingDeclaration".equals(c.kind())));
            assertTrue(root.children().stream().anyMatch(c -> "TypeAliasDeclaration".equals(c.kind())));
        }

        @Test
        @DisplayName("Parses classes, structs, inheritance, access specifiers, constructors, and destructors")
        void testClassesAndStructs() {
            String code = """
                struct Point {
                    int x;
                    int y;
                };

                class Entity : public BaseComponent {
                private:
                    int id;
                public:
                    Entity() : id(0) {}
                    ~Entity() {}
                    int getId() const { return id; }
                    virtual void render() const override;
                };
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            SyntaxNode structNode = root.children().stream()
                .filter(c -> "StructDeclaration".equals(c.kind()))
                .findFirst().orElseThrow();
            assertEquals("Point", structNode.label());
            assertEquals(2, structNode.children().size());

            SyntaxNode classNode = root.children().stream()
                .filter(c -> "ClassDeclaration".equals(c.kind()))
                .findFirst().orElseThrow();
            assertEquals("Entity", classNode.label());
            assertEquals("public BaseComponent", classNode.attribute("bases").orElseThrow());

            assertTrue(classNode.children().stream().anyMatch(c -> "AccessSpecifier".equals(c.kind()) && "private:".equals(c.label())));
            assertTrue(classNode.children().stream().anyMatch(c -> "AccessSpecifier".equals(c.kind()) && "public:".equals(c.label())));

            List<SyntaxNode> functions = classNode.children().stream()
                .filter(c -> "FunctionDefinition".equals(c.kind()) || "FunctionDeclaration".equals(c.kind()))
                .toList();
            assertTrue(functions.stream().anyMatch(f -> "Entity".equals(f.label()) && "true".equals(f.attribute("isConstructor").orElse(""))));
            assertTrue(functions.stream().anyMatch(f -> "~Entity".equals(f.label()) && "true".equals(f.attribute("isDestructor").orElse(""))));
            assertTrue(functions.stream().anyMatch(f -> "getId".equals(f.label())));
            assertTrue(functions.stream().anyMatch(f -> "render".equals(f.label())));
        }

        @Test
        @DisplayName("Parses function prototypes and definitions with qualifiers and parameters")
        void testFunctions() {
            String code = """
                void logMessage(const std::string& msg);
                int calculate(int a, double b) noexcept {
                    return a + static_cast<int>(b);
                }
                virtual void reset() = 0;
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            SyntaxNode proto = root.children().get(0);
            assertEquals("FunctionDeclaration", proto.kind());
            assertEquals("logMessage", proto.label());
            assertEquals("void", proto.attribute("returnType").orElseThrow());

            SyntaxNode def = root.children().get(1);
            assertEquals("FunctionDefinition", def.kind());
            assertEquals("calculate", def.label());
            assertEquals("int", def.attribute("returnType").orElseThrow());

            SyntaxNode pureVirt = root.children().get(2);
            assertEquals("FunctionDeclaration", pureVirt.kind());
            assertEquals("reset", pureVirt.label());
            assertTrue(pureVirt.attribute("qualifiers").orElse("").contains("= 0"));
        }

        @Test
        @DisplayName("Parses basic templates (template classes, template structs, template functions)")
        void testTemplates() {
            String code = """
                template <typename T>
                class Box {
                private:
                    T value;
                public:
                    Box(T val) : value(val) {}
                    T get() const { return value; }
                };

                template <class K, typename V = int>
                struct KeyValue {
                    K key;
                    V val;
                };

                template <typename T>
                T maximum(T a, T b) {
                    return a > b ? a : b;
                }
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            List<SyntaxNode> templates = root.children().stream()
                .filter(c -> "TemplateDeclaration".equals(c.kind()))
                .toList();
            assertEquals(3, templates.size());

            assertEquals("template<typename T>", templates.get(0).label());
            assertEquals("ClassDeclaration", templates.get(0).children().get(1).kind());

            assertEquals("template<class K, typename V>", templates.get(1).label());
            assertEquals("StructDeclaration", templates.get(1).children().get(1).kind());

            assertEquals("template<typename T>", templates.get(2).label());
            assertEquals("FunctionDefinition", templates.get(2).children().get(1).kind());
        }

        @Test
        @DisplayName("Parses variable declarations with modifiers, pointers, references, and initializers")
        void testVariableDeclarations() {
            String code = """
                int a = 10;
                const double pi = 3.14159;
                auto count = 0;
                int* ptr = nullptr;
                const std::string& ref = name;
                std::vector<int> numbers = {1, 2, 3, 4};
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            List<SyntaxNode> vars = root.children().stream()
                .filter(c -> "VariableDeclaration".equals(c.kind()))
                .toList();
            assertEquals(6, vars.size());
            assertEquals("int", vars.get(0).attribute("type").orElseThrow());
            assertEquals("const double", vars.get(1).attribute("type").orElseThrow());
            assertEquals("auto", vars.get(2).attribute("type").orElseThrow());
            assertEquals("int*", vars.get(3).attribute("type").orElseThrow());
            assertEquals("const std::string&", vars.get(4).attribute("type").orElseThrow());
        }

        @Test
        @DisplayName("Parses control structures (if, else if, else, switch, case, default)")
        void testConditionals() {
            String code = """
                void testFlow(int x, int mode) {
                    if (x > 0) {
                        x = 1;
                    } else if (x < 0) {
                        x = -1;
                    } else {
                        x = 0;
                    }

                    switch (mode) {
                        case 1:
                            x += 10;
                            break;
                        case 2:
                            x += 20;
                            break;
                        default:
                            x = 0;
                            break;
                    }
                }
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            SyntaxNode fn = root.children().get(0);
            SyntaxNode body = fn.children().stream()
                .filter(c -> "CompoundStatement".equals(c.kind()))
                .findFirst().orElseThrow();

            assertTrue(body.children().stream().anyMatch(c -> "IfStatement".equals(c.kind())));
            assertTrue(body.children().stream().anyMatch(c -> "SwitchStatement".equals(c.kind())));
        }

        @Test
        @DisplayName("Parses all loop forms (classic for, range-based for, while, do-while)")
        void testLoops() {
            String code = """
                void runLoops() {
                    for (int i = 0; i < 10; ++i) {
                        total += i;
                    }
                    for (const auto& item : items) {
                        process(item);
                    }
                    while (count > 0) {
                        count--;
                    }
                    do {
                        tick();
                    } while (running);
                }
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();

            SyntaxNode fn = root.children().get(0);
            SyntaxNode body = fn.children().stream()
                .filter(c -> "CompoundStatement".equals(c.kind()))
                .findFirst().orElseThrow();

            assertTrue(body.children().stream().anyMatch(c -> "ForStatement".equals(c.kind())));
            assertTrue(body.children().stream().anyMatch(c -> "RangeForStatement".equals(c.kind())));
            assertTrue(body.children().stream().anyMatch(c -> "WhileStatement".equals(c.kind())));
            assertTrue(body.children().stream().anyMatch(c -> "DoWhileStatement".equals(c.kind())));
        }

        @Test
        @DisplayName("Parses expressions: binary, unary, ternary, call, subscript, member access, scope")
        void testExpressions() {
            String code = """
                void evaluate() {
                    int r = (a + b) * c / d % 2;
                    bool flag = (x >= y) && (z != 0) || !valid;
                    int bit = (m << 2) | (n & 0xFF) ^ ~mask;
                    obj.field = ptr->method(arg1, arg2);
                    matrix[row][col] = true ? 1 : 0;
                    std::cout << "Value: " << r;
                }
                """;
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertFalse(result.hasErrors(), "Should have no errors: " + result.diagnostics());
            SyntaxNode root = result.syntaxTree().get();
            assertNotNull(root);
        }
    }

    @Nested
    @DisplayName("Invalid and Malformed C++ Inputs Producing Diagnostics")
    class InvalidInputsAndDiagnostics {

        @Test
        @DisplayName("Reports missing semicolon on variable declarations")
        void testMissingSemicolonVariable() {
            String code = "int x = 10\nint y = 20;";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            assertEquals(1, result.errorCount());

            Diagnostic diag = result.diagnostics().get(0);
            assertEquals(CheckCategory.STATEMENT_TERMINATOR, diag.category());
            assertEquals(CppParser.ERR_CPP_MISSING_SEMICOLON, diag.code());
            assertEquals(1, diag.location().line());
            assertTrue(diag.suggestedFix().isPresent());
        }

        @Test
        @DisplayName("Reports missing semicolon after class declaration")
        void testMissingSemicolonClass() {
            String code = "class Widget {\n    int w;\n}\nint main() { return 0; }";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.STATEMENT_TERMINATOR
                && CppParser.ERR_CPP_MISSING_SEMICOLON.equals(d.code())
            ));
        }

        @Test
        @DisplayName("Reports missing semicolon after return statement")
        void testMissingSemicolonReturn() {
            String code = "int test() {\n    return 42\n}";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            Diagnostic diag = result.diagnostics().stream()
                .filter(d -> CppParser.ERR_CPP_MISSING_SEMICOLON.equals(d.code()))
                .findFirst().orElseThrow();
            assertEquals(2, diag.location().line());
        }

        @Test
        @DisplayName("Reports unclosed brace in block")
        void testUnclosedBrace() {
            String code = "void func() {\n    int a = 1;";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.DELIMITER_MATCH
                && CppParser.ERR_CPP_UNCLOSED_DELIMITER.equals(d.code())
                && d.message().contains("}")
            ));
        }

        @Test
        @DisplayName("Reports unclosed parenthesis in if condition")
        void testUnclosedParenthesisInIf() {
            String code = "void test() {\n    if (x > 0 {\n        y = 1;\n    }\n}";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.DELIMITER_MATCH
                && CppParser.ERR_CPP_UNCLOSED_DELIMITER.equals(d.code())
                && d.message().contains(")")
            ));
        }

        @Test
        @DisplayName("Reports malformed control header missing opening parenthesis")
        void testMissingOpeningParenInWhile() {
            String code = "void test() {\n    while count > 0) {\n        count--;\n    }\n}";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && CppParser.ERR_CPP_CONTROL_HEADER.equals(d.code())
            ));
        }

        @Test
        @DisplayName("Reports malformed do-while missing while keyword")
        void testMalformedDoWhile() {
            String code = "void test() {\n    do {\n        step();\n    } count > 0;\n}";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertTrue(result.hasErrors());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.category() == CheckCategory.CONTROL_HEADER
                && d.message().contains("while")
            ));
        }

        @Test
        @DisplayName("Diagnostics contain 1-based line and column coordinates")
        void testOneBasedCoordinates() {
            String code = "int a = 10;\nint b = 20\nint c = 30;";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));

            assertEquals(1, result.errorCount());
            Diagnostic diag = result.diagnostics().get(0);
            assertEquals(2, diag.location().line());
            assertTrue(diag.location().column() >= 1);
            assertTrue(diag.location().charOffset() > 0);
        }
    }

    @Nested
    @DisplayName("Contract, Registry, and Edge Cases")
    class ContractAndRegistryIntegration {

        @Test
        @DisplayName("Integrates into ParserAdapterRegistry seamlessly")
        void testRegistryIntegration() {
            ParserAdapterRegistry registry = ParserAdapterRegistry.createDefault();
            ParserAdapter regAdapter = registry.getRequiredAdapter(Language.CPP);
            assertNotNull(regAdapter);
            assertEquals(Language.CPP, regAdapter.language());

            ParseResult result = regAdapter.parse(SourcePayload.snippet("int val = 99;", Language.CPP));
            assertFalse(result.hasErrors());
            assertTrue(result.hasSyntaxTree());
            assertEquals("TranslationUnit", result.syntaxTree().get().kind());
        }

        @Test
        @DisplayName("Preserves native syntax tree as CppTranslationUnit")
        void testNativeSyntaxTreePreservation() {
            String code = "struct Vector2D { float x; float y; };";
            ParseResult result = adapter.parse(SourcePayload.file(code, "vector.hpp", Language.CPP));

            assertTrue(result.hasNativeSyntaxTree());
            Optional<CppTranslationUnit> nativeTree = result.nativeSyntaxTree(CppTranslationUnit.class);
            assertTrue(nativeTree.isPresent());
            CppTranslationUnit unit = nativeTree.get();
            assertEquals("vector.hpp", unit.sourceName());
            assertEquals(1, unit.declarations().size());
            assertInstanceOf(CppClass.class, unit.declarations().get(0));
            CppClass cls = (CppClass) unit.declarations().get(0);
            assertEquals("Vector2D", cls.name());
            assertEquals("struct", cls.kind());
        }

        @Test
        @DisplayName("Handles empty source payload cleanly")
        void testEmptySourcePayload() {
            ParseResult result = adapter.parse(SourcePayload.snippet("", Language.CPP));
            assertFalse(result.hasErrors());
            assertTrue(result.hasSyntaxTree());
            assertEquals("TranslationUnit", result.syntaxTree().get().kind());
            assertEquals(0, result.syntaxTree().get().childCount());
        }

        @Test
        @DisplayName("Handles trivia-only source payload (comments and whitespace)")
        void testTriviaOnlyPayload() {
            String code = "// Line comment\n/* Block comment */\n\n";
            ParseResult result = adapter.parse(SourcePayload.snippet(code, Language.CPP));
            assertFalse(result.hasErrors());
            assertTrue(result.hasSyntaxTree());
            assertEquals("TranslationUnit", result.syntaxTree().get().kind());
            assertEquals(0, result.syntaxTree().get().childCount());
        }

        @Test
        @DisplayName("Rejects null payload or non-Cpp language")
        void testInvariants() {
            assertThrows(NullPointerException.class, () -> adapter.parse(null));
            assertThrows(IllegalArgumentException.class, () ->
                adapter.parse(SourcePayload.snippet("print('hello')", Language.PYTHON))
            );
        }

        @Test
        @DisplayName("Custom parser delegate override works as contract specified")
        void testCustomParserDelegate() {
            ParseResult customResult = ParseResult.of(
                SyntaxNode.leaf("CustomNode", "Custom", com.writely.syntax_analyzer.domain.SourceSpan.point(SourceLocation.start()))
            );
            CppAdapter customAdapter = new CppAdapter((p, t) -> customResult);
            ParseResult result = customAdapter.parse(SourcePayload.snippet("int x = 1;", Language.CPP));
            assertSame(customResult, result);
        }
    }
}
