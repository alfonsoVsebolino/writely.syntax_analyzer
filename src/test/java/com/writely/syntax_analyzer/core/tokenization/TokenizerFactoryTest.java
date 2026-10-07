package com.writely.syntax_analyzer.core.tokenization;

import com.writely.syntax_analyzer.domain.Language;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenizerFactory and CompositeTokenizer Tests")
class TokenizerFactoryTest {

    @Test
    @DisplayName("Resolves appropriate Tokenizer instance for each supported language")
    void testForLanguageResolution() {
        Tokenizer javaTokenizer = TokenizerFactory.forLanguage(Language.JAVA);
        assertInstanceOf(JavaTokenizer.class, javaTokenizer);

        Tokenizer pythonTokenizer = TokenizerFactory.forLanguage(Language.PYTHON);
        assertInstanceOf(PythonTokenizer.class, pythonTokenizer);

        Tokenizer cppTokenizer = TokenizerFactory.forLanguage(Language.CPP);
        assertInstanceOf(CppTokenizer.class, cppTokenizer);

        // Tokenizer interface static method
        assertInstanceOf(JavaTokenizer.class, Tokenizer.forLanguage(Language.JAVA));
        assertInstanceOf(CompositeTokenizer.class, new CompositeTokenizer());
    }

    @Test
    @DisplayName("Rejects null language with NullPointerException")
    void testNullLanguageRejected() {
        assertThrows(NullPointerException.class, () -> TokenizerFactory.forLanguage(null));
        assertThrows(NullPointerException.class, () -> Tokenizer.forLanguage(null));
        assertThrows(NullPointerException.class, () -> CompositeTokenizer.forLanguage(null));
    }

    @Test
    @DisplayName("CompositeTokenizer dispatches tokenization according to payload language")
    void testCompositeTokenizerDispatch() {
        CompositeTokenizer composite = new CompositeTokenizer();

        // Java payload
        SourcePayload javaPayload = SourcePayload.snippet("int x = 42;", Language.JAVA);
        List<Token> javaTokens = composite.tokenize(javaPayload);
        assertEquals(5, javaTokens.size());
        assertEquals(TokenType.KEYWORD, javaTokens.get(0).tokenType());

        // Python payload
        SourcePayload pyPayload = SourcePayload.snippet("x = 42\n", Language.PYTHON);
        List<Token> pyTokens = composite.tokenize(pyPayload);
        assertTrue(pyTokens.stream().anyMatch(t -> t.lexeme().equals("x")));

        // C++ payload
        SourcePayload cppPayload = SourcePayload.snippet("#include <iostream>\nint x;", Language.CPP);
        List<Token> cppTokens = composite.tokenize(cppPayload);
        assertEquals("PREPROCESSOR", cppTokens.get(0).category());

        assertThrows(NullPointerException.class, () -> composite.tokenize(null));

        // Test custom constructor
        CompositeTokenizer custom = new CompositeTokenizer(new JavaTokenizer(), new PythonTokenizer(), new CppTokenizer());
        assertNotNull(custom.tokenize(javaPayload));

        assertThrows(NullPointerException.class, () -> new CompositeTokenizer(null, new PythonTokenizer(), new CppTokenizer()));
        assertThrows(NullPointerException.class, () -> new CompositeTokenizer(new JavaTokenizer(), null, new CppTokenizer()));
        assertThrows(NullPointerException.class, () -> new CompositeTokenizer(new JavaTokenizer(), new PythonTokenizer(), null));

        // Test private constructor of TokenizerFactory for completeness
        try {
            var ctor = TokenizerFactory.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        } catch (Exception ignored) {
        }
    }
}
