package com.writely.syntax_analyzer.adapter;

import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Parser Adapter Registry Tests")
class ParserAdapterRegistryTest {

    private ParserAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ParserAdapterRegistry.createDefault();
    }

    @Nested
    @DisplayName("Default Registry Setup")
    class DefaultRegistryTests {

        @Test
        @DisplayName("Default registry has all three standard adapters registered")
        void testDefaultRegistryHasStandardAdapters() {
            assertEquals(3, registry.size());
            assertTrue(registry.hasAdapter(Language.JAVA));
            assertTrue(registry.hasAdapter(Language.PYTHON));
            assertTrue(registry.hasAdapter(Language.CPP));

            assertTrue(registry.getAdapter(Language.JAVA).isPresent());
            assertTrue(registry.getAdapter(Language.PYTHON).isPresent());
            assertTrue(registry.getAdapter(Language.CPP).isPresent());

            assertEquals(Set.of(Language.JAVA, Language.PYTHON, Language.CPP), registry.registeredLanguages());
            assertEquals(3, registry.allAdapters().size());
        }

        @Test
        @DisplayName("Empty registry has no adapters")
        void testEmptyRegistry() {
            ParserAdapterRegistry empty = ParserAdapterRegistry.empty();
            assertEquals(0, empty.size());
            assertFalse(empty.hasAdapter(Language.JAVA));
            assertTrue(empty.getAdapter(Language.JAVA).isEmpty());
        }

        @Test
        @DisplayName("Register, unregister, and clear lifecycle")
        void testLifecycle() {
            ParserAdapterRegistry reg = ParserAdapterRegistry.empty();
            JavaAdapter java = new JavaAdapter();
            reg.register(java);
            assertEquals(1, reg.size());
            assertTrue(reg.hasAdapter(Language.JAVA));

            assertTrue(reg.unregister(Language.JAVA));
            assertFalse(reg.hasAdapter(Language.JAVA));
            assertFalse(reg.unregister(Language.JAVA));

            reg.register(java);
            reg.clear();
            assertEquals(0, reg.size());
        }
    }

    @Nested
    @DisplayName("Extension Inference Tests")
    class ExtensionInferenceTests {

        @Test
        @DisplayName("Lookup adapter by extension")
        void testGetAdapterByExtension() {
            assertTrue(registry.getAdapterForExtension(".java").isPresent());
            assertEquals(Language.JAVA, registry.getAdapterForExtension(".java").get().language());

            assertTrue(registry.getAdapterForExtension("java").isPresent());
            assertEquals(Language.JAVA, registry.getAdapterForExtension("java").get().language());

            assertTrue(registry.getAdapterForExtension(".py").isPresent());
            assertEquals(Language.PYTHON, registry.getAdapterForExtension(".py").get().language());

            assertTrue(registry.getAdapterForExtension(".cpp").isPresent());
            assertEquals(Language.CPP, registry.getAdapterForExtension(".cpp").get().language());

            assertTrue(registry.getAdapterForExtension(".hpp").isPresent());
            assertEquals(Language.CPP, registry.getAdapterForExtension(".hpp").get().language());

            assertTrue(registry.getAdapterForExtension(".unknown").isEmpty());
            assertTrue(registry.getAdapterForExtension(null).isEmpty());
            assertTrue(registry.getAdapterForExtension("").isEmpty());
        }

        @Test
        @DisplayName("Lookup adapter by file name")
        void testGetAdapterByFileName() {
            assertTrue(registry.getAdapterForFileName("Main.java").isPresent());
            assertEquals(Language.JAVA, registry.getAdapterForFileName("Main.java").get().language());

            assertTrue(registry.getAdapterForFileName("src/app/script.py").isPresent());
            assertEquals(Language.PYTHON, registry.getAdapterForFileName("src/app/script.py").get().language());

            assertTrue(registry.getAdapterForFileName("native/core.cpp").isPresent());
            assertEquals(Language.CPP, registry.getAdapterForFileName("native/core.cpp").get().language());

            assertTrue(registry.getAdapterForFileName("include/header.h").isPresent());
            assertEquals(Language.CPP, registry.getAdapterForFileName("include/header.h").get().language());

            assertTrue(registry.getAdapterForFileName("Makefile").isEmpty());
            assertTrue(registry.getAdapterForFileName("image.png").isEmpty());
            assertTrue(registry.getAdapterForFileName(null).isEmpty());
            assertTrue(registry.getAdapterForFileName("   ").isEmpty());
        }
    }

    @Nested
    @DisplayName("Explicit Selection Overriding Extension Inference")
    class ExplicitSelectionTests {

        @Test
        @DisplayName("Explicit language selection strictly overrides conflicting file extension")
        void testExplicitLanguageOverridesExtension() {
            // File named script.py, but explicit language is Java
            Optional<ParserAdapter> resolved = registry.resolveAdapter(Language.JAVA, "script.py");
            assertTrue(resolved.isPresent());
            assertEquals(Language.JAVA, resolved.get().language());

            // File named Main.java, but explicit language is Python
            Optional<ParserAdapter> resolvedPy = registry.resolveAdapter(Language.PYTHON, "Main.java");
            assertTrue(resolvedPy.isPresent());
            assertEquals(Language.PYTHON, resolvedPy.get().language());

            // File named test.cpp, but explicit language is Java
            Optional<ParserAdapter> resolvedJavaFromCpp = registry.resolveAdapter(Language.JAVA, "test.cpp");
            assertTrue(resolvedJavaFromCpp.isPresent());
            assertEquals(Language.JAVA, resolvedJavaFromCpp.get().language());
        }

        @Test
        @DisplayName("Inference is applied when explicit language is null")
        void testInferenceWhenExplicitIsNull() {
            Optional<ParserAdapter> resolvedJava = registry.resolveAdapter(null, "Test.java");
            assertTrue(resolvedJava.isPresent());
            assertEquals(Language.JAVA, resolvedJava.get().language());

            Optional<ParserAdapter> resolvedPy = registry.resolveAdapter(null, "script.py");
            assertTrue(resolvedPy.isPresent());
            assertEquals(Language.PYTHON, resolvedPy.get().language());

            Optional<ParserAdapter> resolvedCpp = registry.resolveAdapter(null, "program.cxx");
            assertTrue(resolvedCpp.isPresent());
            assertEquals(Language.CPP, resolvedCpp.get().language());

            Optional<ParserAdapter> unresolved = registry.resolveAdapter(null, "notes.txt");
            assertTrue(unresolved.isEmpty());
        }

        @Test
        @DisplayName("Explicit language with no adapter registered does not fall back to extension")
        void testExplicitWithoutAdapterDoesNotFallback() {
            ParserAdapterRegistry customReg = ParserAdapterRegistry.empty();
            customReg.register(new PythonAdapter());

            // Explicit language is Java, but only Python is registered; file name is script.py
            Optional<ParserAdapter> result = customReg.resolveAdapter(Language.JAVA, "script.py");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Resolve adapter from SourcePayload")
        void testResolveFromSourcePayload() {
            SourcePayload payload = SourcePayload.file("class A {}", "A.py", Language.JAVA);
            // Even though sourceName is A.py, payload language is JAVA
            Optional<ParserAdapter> adapter = registry.resolveAdapter(payload);
            assertTrue(adapter.isPresent());
            assertEquals(Language.JAVA, adapter.get().language());
        }

        @Test
        @DisplayName("Required adapter getters throw when missing")
        void testRequiredAdapterThrows() {
            assertNotNull(registry.getRequiredAdapter(Language.JAVA));

            ParserAdapterRegistry empty = ParserAdapterRegistry.empty();
            assertThrows(IllegalArgumentException.class, () -> empty.getRequiredAdapter(Language.JAVA));
            assertThrows(IllegalArgumentException.class, () -> empty.resolveRequiredAdapter(Language.JAVA, "Main.java"));
            assertThrows(IllegalArgumentException.class, () -> empty.resolveRequiredAdapter(null, "unknown.xyz"));

            SourcePayload payload = SourcePayload.singleLine("int x = 1;", Language.JAVA);
            assertThrows(IllegalArgumentException.class, () -> empty.resolveRequiredAdapter(payload));
        }
    }
}
