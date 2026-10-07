package com.writely.syntax_analyzer.adapter.cpp;

import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.domain.CheckCategory;
import com.writely.syntax_analyzer.domain.Diagnostic;
import com.writely.syntax_analyzer.domain.SourceLocation;
import com.writely.syntax_analyzer.domain.SourcePayload;
import com.writely.syntax_analyzer.domain.SourceSpan;
import com.writely.syntax_analyzer.domain.SyntaxNode;
import com.writely.syntax_analyzer.domain.Token;
import com.writely.syntax_analyzer.domain.TokenType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Recursive descent parser for the C++ baseline language subset adhering to ADR 0011.
 * Supports includes, preprocessor directives, namespaces, templates, classes/structs,
 * functions, variables, control flow, loops, and expressions.
 */
public class CppParser {

    public static final String ERR_CPP_MISSING_SEMICOLON = "CPP_MISSING_SEMICOLON";
    public static final String ERR_CPP_UNCLOSED_DELIMITER = "CPP_UNCLOSED_DELIMITER";
    public static final String ERR_CPP_CONTROL_HEADER = "CPP_CONTROL_HEADER";
    public static final String ERR_CPP_UNEXPECTED_TOKEN = "CPP_UNEXPECTED_TOKEN";
    public static final String ERR_CPP_MALFORMED_DECLARATION = "CPP_MALFORMED_DECLARATION";

    private static final Set<String> TYPE_KEYWORDS = Set.of(
        "int", "bool", "char", "float", "double", "long", "short",
        "void", "auto", "signed", "unsigned", "wchar_t"
    );

    private static final Set<String> MODIFIER_KEYWORDS = Set.of(
        "const", "constexpr", "static", "virtual", "inline", "explicit",
        "friend", "mutable", "thread_local", "extern", "register", "volatile"
    );

    private static final Set<String> DECLARATION_KEYWORDS = Set.of(
        "class", "struct", "enum", "union", "namespace", "template", "using", "typedef"
    );

    private CppTokenStream stream;
    private List<Diagnostic> diagnostics;

    public CppParser() {
    }

    public ParseResult parse(SourcePayload payload, List<Token> rawTokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(rawTokens, "rawTokens must not be null");

        if (rawTokens.isEmpty()) {
            SourceSpan emptySpan = SourceSpan.point(SourceLocation.start());
            SyntaxNode emptyRoot = SyntaxNode.of("TranslationUnit", payload.sourceName(), emptySpan);
            CppTranslationUnit nativeUnit = new CppTranslationUnit(payload.sourceName(), List.of(), emptySpan);
            return ParseResult.of(emptyRoot, List.of(), nativeUnit, rawTokens);
        }

        this.stream = new CppTokenStream(rawTokens);
        this.diagnostics = new ArrayList<>();

        SourceSpan totalSpan = SourceSpan.of(
            rawTokens.get(0).startLocation(),
            rawTokens.get(rawTokens.size() - 1).endLocation()
        );

        List<CppAstNode> declarations = new ArrayList<>();
        while (!stream.isAtEnd()) {
            try {
                CppAstNode decl = parseTopLevelDeclaration();
                if (decl != null) {
                    declarations.add(decl);
                }
            } catch (Exception e) {
                // Safeguard against unhandled exceptions during recovery
                synchronize();
            }
        }

        CppTranslationUnit nativeUnit = new CppTranslationUnit(payload.sourceName(), declarations, totalSpan);
        SyntaxNode root = nativeUnit.toSyntaxNode();

        diagnostics.sort(Comparator.comparing(Diagnostic::location));
        return ParseResult.of(root, Collections.unmodifiableList(diagnostics), nativeUnit, rawTokens);
    }

    // =========================================================================
    // Top-Level Declarations
    // =========================================================================

    private CppAstNode parseTopLevelDeclaration() {
        if (stream.isAtEnd()) {
            return null;
        }

        // 1. Preprocessor directives (#include, #define, etc.)
        if (stream.peek().tokenType() == TokenType.COMMENT && "PREPROCESSOR".equals(stream.peek().category())) {
            Token prep = stream.consume();
            String text = prep.lexeme().trim();
            if (text.startsWith("#include")) {
                String headerPart = text.substring("#include".length()).trim();
                boolean isSystem = headerPart.startsWith("<") && headerPart.endsWith(">");
                return new CppInclude(headerPart, isSystem, prep.span());
            }
            return new CppPreprocessorDirective(text, prep.span());
        }

        // 2. Namespaces
        if (stream.check("namespace")) {
            return parseNamespace();
        }

        // 3. Using directives and aliases
        if (stream.check("using")) {
            return parseUsing();
        }

        // 4. Templates
        if (stream.check("template")) {
            return parseTemplate();
        }

        // 5. Classes, Structs, and Enums
        if (stream.check("class") || stream.check("struct") || stream.check("enum")) {
            return parseClassOrStruct();
        }

        // 6. Typedef
        if (stream.check("typedef")) {
            return parseTypedef();
        }

        // 7. Semicolons
        if (stream.match(";")) {
            return null;
        }

        // 8. Functions or Variable Declarations
        return parseFunctionOrVariable(false, null);
    }

    private CppAstNode parseNamespace() {
        SourceLocation start = stream.location();
        stream.consume(); // "namespace"

        String name = "(anonymous)";
        if (stream.check(TokenType.IDENTIFIER)) {
            name = parseQualifiedName();
        }

        if (!stream.match("{")) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected '{' after namespace declaration", "Insert '{'");
            synchronize();
            return new CppNamespace(name, List.of(), SourceSpan.of(start, stream.lastLocation()));
        }

        List<CppAstNode> members = new ArrayList<>();
        while (!stream.check("}") && !stream.isAtEnd()) {
            CppAstNode member = parseTopLevelDeclaration();
            if (member != null) {
                members.add(member);
            }
        }

        expectCloseBrace();
        stream.match(";"); // optional semicolon after namespace
        return new CppNamespace(name, members, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppAstNode parseUsing() {
        SourceLocation start = stream.location();
        stream.consume(); // "using"

        if (stream.match("namespace")) {
            String target = parseQualifiedName();
            expectSemicolon("Missing ';' after using namespace directive");
            return new CppUsing("namespace " + target, "using", SourceSpan.of(start, stream.lastLocation()));
        }

        if (stream.check(TokenType.IDENTIFIER) && stream.check(1, "=")) {
            Token alias = stream.consume();
            stream.consume(); // "="
            String type = parseTypeString();
            expectSemicolon("Missing ';' after type alias declaration");
            return new CppUsing(alias.lexeme() + " = " + type, "alias", SourceSpan.of(start, stream.lastLocation()));
        }

        String target = parseQualifiedName();
        expectSemicolon("Missing ';' after using declaration");
        return new CppUsing(target, "using", SourceSpan.of(start, stream.lastLocation()));
    }

    private CppAstNode parseTypedef() {
        SourceLocation start = stream.location();
        stream.consume(); // "typedef"
        String type = parseTypeString();
        String name = stream.check(TokenType.IDENTIFIER) ? stream.consume().lexeme() : "";
        expectSemicolon("Missing ';' after typedef declaration");
        return new CppUsing(type + " " + name, "typedef", SourceSpan.of(start, stream.lastLocation()));
    }

    private CppAstNode parseTemplate() {
        SourceLocation start = stream.location();
        stream.consume(); // "template"

        if (!stream.match("<")) {
            reportError(CheckCategory.CONTROL_HEADER, ERR_CPP_CONTROL_HEADER,
                "Expected '<' after 'template'", "Insert '<'");
            synchronize();
            return null;
        }

        List<CppTemplateParam> params = new ArrayList<>();
        while (!stream.check(">") && !stream.isAtEnd()) {
            SourceLocation pStart = stream.location();
            String kind = "typename";
            if (stream.check("typename") || stream.check("class")) {
                kind = stream.consume().lexeme();
            } else if (isStartOfType()) {
                kind = parseTypeString();
            }

            String paramName = "";
            if (stream.check(TokenType.IDENTIFIER)) {
                paramName = stream.consume().lexeme();
            }

            String defaultVal = null;
            if (stream.match("=")) {
                defaultVal = parseTypeString();
            }

            params.add(new CppTemplateParam(kind, paramName, defaultVal, SourceSpan.of(pStart, stream.lastLocation())));

            if (!stream.match(",")) {
                break;
            }
        }

        if (!stream.match(">")) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected '>' to close template parameter list", "Insert '>'");
        }

        CppAstNode decl = parseTopLevelDeclaration();
        return new CppTemplate(params, decl, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppAstNode parseClassOrStruct() {
        SourceLocation start = stream.location();
        Token kindTok = stream.consume(); // "class", "struct", or "enum"
        String kind = kindTok.lexeme();

        if ("enum".equals(kind) && (stream.check("class") || stream.check("struct"))) {
            kind = "enum " + stream.consume().lexeme();
        }

        String name = "";
        if (stream.check(TokenType.IDENTIFIER)) {
            name = stream.consume().lexeme();
        }

        List<String> baseClasses = new ArrayList<>();
        if (stream.match(":")) {
            baseClasses = parseBaseClause();
        }

        if (stream.match(";")) {
            return new CppClass(name, kind, baseClasses, List.of(), true, SourceSpan.of(start, stream.lastLocation()));
        }

        if (!stream.match("{")) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected '{' or ';' in " + kind + " declaration", "Insert '{'");
            synchronize();
            return new CppClass(name, kind, baseClasses, List.of(), false, SourceSpan.of(start, stream.lastLocation()));
        }

        List<CppAstNode> members;
        if (kind.startsWith("enum")) {
            members = parseEnumMembers();
        } else {
            members = parseClassMembers(name);
        }
        expectCloseBrace();
        expectSemicolon("Missing ';' after " + kind + " declaration");

        return new CppClass(name, kind, baseClasses, members, false, SourceSpan.of(start, stream.lastLocation()));
    }

    private List<CppAstNode> parseEnumMembers() {
        while (!stream.check("}") && !stream.isAtEnd()) {
            stream.consume();
        }
        return List.of();
    }

    private List<String> parseBaseClause() {
        List<String> bases = new ArrayList<>();
        while (!stream.check("{") && !stream.check(";") && !stream.isAtEnd()) {
            StringBuilder base = new StringBuilder();
            if (stream.check("public") || stream.check("protected") || stream.check("private") || stream.check("virtual")) {
                base.append(stream.consume().lexeme()).append(" ");
            }
            if (stream.check("virtual")) {
                base.append(stream.consume().lexeme()).append(" ");
            }
            base.append(parseQualifiedName());
            if (stream.check("<")) {
                base.append(parseTemplateArgumentListString());
            }
            bases.add(base.toString().trim());

            if (!stream.match(",")) {
                break;
            }
        }
        return bases;
    }

    private List<CppAstNode> parseClassMembers(String className) {
        List<CppAstNode> members = new ArrayList<>();
        while (!stream.check("}") && !stream.isAtEnd()) {
            // Access specifiers: public:, private:, protected:
            if ((stream.check("public") || stream.check("protected") || stream.check("private")) && stream.check(1, ":")) {
                SourceLocation aStart = stream.location();
                Token acc = stream.consume();
                stream.consume(); // ":"
                members.add(new CppAccessSpecifier(acc.lexeme(), SourceSpan.of(aStart, stream.lastLocation())));
                continue;
            }

            // Destructor ~ClassName()
            if (stream.check("~") && stream.check(1, TokenType.IDENTIFIER)) {
                members.add(parseDestructor(className));
                continue;
            }

            // Constructor ClassName(...)
            if (className != null && !className.isBlank() && stream.check(className) && stream.check(1, "(")) {
                members.add(parseConstructor(className));
                continue;
            }

            // Template member
            if (stream.check("template")) {
                CppAstNode templ = parseTemplate();
                if (templ != null) members.add(templ);
                continue;
            }

            // Nested class or struct
            if (stream.check("class") || stream.check("struct")) {
                members.add(parseClassOrStruct());
                continue;
            }

            // Using
            if (stream.check("using")) {
                members.add(parseUsing());
                continue;
            }

            if (stream.match(";")) {
                continue;
            }

            CppAstNode member = parseFunctionOrVariable(true, className);
            if (member != null) {
                members.add(member);
            }
        }
        return members;
    }

    private CppAstNode parseConstructor(String className) {
        SourceLocation start = stream.location();
        stream.consume(); // className
        List<CppParam> params = parseParameterList();
        List<String> qualifiers = parseFunctionQualifiers();
        List<String> initializers = parseMemberInitializers();

        if (stream.check("{")) {
            CppStatement body = parseStatement();
            return new CppFunction("", className, params, qualifiers, body, true, true, false, initializers,
                SourceSpan.of(start, stream.lastLocation()));
        }

        expectSemicolon("Missing ';' after constructor declaration");
        return new CppFunction("", className, params, qualifiers, null, false, true, false, initializers,
            SourceSpan.of(start, stream.lastLocation()));
    }

    private CppAstNode parseDestructor(String className) {
        SourceLocation start = stream.location();
        stream.consume(); // "~"
        String dtorName = "~" + stream.consume().lexeme();
        List<CppParam> params = parseParameterList();
        List<String> qualifiers = parseFunctionQualifiers();

        if (stream.check("{")) {
            CppStatement body = parseStatement();
            return new CppFunction("", dtorName, params, qualifiers, body, true, false, true, List.of(),
                SourceSpan.of(start, stream.lastLocation()));
        }

        expectSemicolon("Missing ';' after destructor declaration");
        return new CppFunction("", dtorName, params, qualifiers, null, false, false, true, List.of(),
            SourceSpan.of(start, stream.lastLocation()));
    }

    private List<String> parseMemberInitializers() {
        if (!stream.match(":")) {
            return List.of();
        }
        List<String> inits = new ArrayList<>();
        while (!stream.check("{") && !stream.isAtEnd()) {
            StringBuilder sb = new StringBuilder();
            if (stream.check(TokenType.IDENTIFIER)) {
                sb.append(stream.consume().lexeme());
            }
            if (stream.match("(")) {
                sb.append("(");
                if (!stream.check(")")) {
                    sb.append(parseExpressionString());
                }
                expectCloseParen();
                sb.append(")");
            } else if (stream.match("{")) {
                sb.append("{");
                if (!stream.check("}")) {
                    sb.append(parseExpressionString());
                }
                expectCloseBrace();
                sb.append("}");
            }
            inits.add(sb.toString());
            if (!stream.match(",")) {
                break;
            }
        }
        return inits;
    }

    private CppAstNode parseFunctionOrVariable(boolean inClass, String enclosingClassName) {
        SourceLocation start = stream.location();
        List<String> modifiers = parseModifiers();

        if (!isStartOfType()) {
            // Cannot recognize declaration; report error and recover
            Token unexpected = stream.consume();
            reportError(CheckCategory.STATEMENT_TERMINATOR, ERR_CPP_UNEXPECTED_TOKEN,
                "Unexpected token '" + unexpected.lexeme() + "' in C++ declaration",
                "Remove or replace unexpected token");
            synchronize();
            return null;
        }

        String type = parseTypeString();

        // Check if declarator name follows
        String name = "";
        if (stream.check("operator") || stream.check(TokenType.OPERATOR)) {
            name = parseOperatorName();
        } else if (stream.check(TokenType.IDENTIFIER)) {
            name = parseQualifiedName();
        }

        // Check if pointer or reference was attached to name
        while (stream.match("*") || stream.match("&")) {
            type += stream.lastConsumed().lexeme();
        }

        // If name wasn't read earlier due to pointer/ref attached to name
        if (name.isEmpty() && stream.check(TokenType.IDENTIFIER)) {
            name = parseQualifiedName();
        }

        List<String> fnQualifiers = new ArrayList<>();
        List<String> typePrefixes = new ArrayList<>();
        for (String m : modifiers) {
            if (m.equals("virtual") || m.equals("inline") || m.equals("explicit") || m.equals("friend")) {
                fnQualifiers.add(m);
            } else {
                typePrefixes.add(m);
            }
        }
        String fullType = typePrefixes.isEmpty() ? type : String.join(" ", typePrefixes) + " " + type;

        // Disambiguate Function vs Variable
        if (stream.check("(")) {
            return parseFunctionRest(start, fullType, name, fnQualifiers);
        }

        // Variable declaration
        return parseVariableRest(start, fullType, name);
    }

    private CppAstNode parseFunctionRest(SourceLocation start, String returnType, String name, List<String> modifiers) {
        List<CppParam> params = parseParameterList();
        List<String> qualifiers = new ArrayList<>(modifiers);
        qualifiers.addAll(parseFunctionQualifiers());

        // Constructor initializers if out-of-line constructor definition ClassName::ClassName(...) : ...
        List<String> initializers = parseMemberInitializers();

        // Check for pure virtual "= 0" or default/delete
        if (stream.match("=")) {
            if (stream.match("0")) {
                qualifiers.add("= 0");
            } else if (stream.match("default")) {
                qualifiers.add("= default");
            } else if (stream.match("delete")) {
                qualifiers.add("= delete");
            }
        }

        if (stream.check("{")) {
            CppStatement body = parseStatement();
            return new CppFunction(returnType, name, params, qualifiers, body, true, false, false, initializers,
                SourceSpan.of(start, stream.lastLocation()));
        }

        expectSemicolon("Missing ';' after function declaration");
        return new CppFunction(returnType, name, params, qualifiers, null, false, false, false, initializers,
            SourceSpan.of(start, stream.lastLocation()));
    }

    private CppAstNode parseVariableRest(SourceLocation start, String type, String firstName) {
        List<CppVarDeclarator> declarators = new ArrayList<>();

        if (!firstName.isEmpty()) {
            declarators.add(parseDeclarator(firstName));
        }

        while (stream.match(",")) {
            String nextTypeModifier = "";
            while (stream.match("*") || stream.match("&")) {
                nextTypeModifier += stream.lastConsumed().lexeme();
            }
            if (stream.check(TokenType.IDENTIFIER)) {
                String varName = nextTypeModifier + stream.consume().lexeme();
                declarators.add(parseDeclarator(varName));
            }
        }

        expectSemicolon("Missing ';' after variable declaration");
        return new CppVariable(type, declarators, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppVarDeclarator parseDeclarator(String name) {
        SourceLocation start = stream.lastConsumed() != null ? stream.lastConsumed().startLocation() : stream.location();
        CppExpr init = null;

        // Array brackets [10]
        while (stream.match("[")) {
            if (!stream.check("]")) {
                parseExpression();
            }
            expectCloseBracket();
            name += "[]";
        }

        if (stream.match("=")) {
            init = parseExpression();
        } else if (stream.match("{")) {
            init = parseInitializerListRest(stream.lastConsumed().startLocation());
        } else if (stream.match("(")) {
            init = parseParenthesizedExpressionOrArgList();
        }

        return new CppVarDeclarator(name, init, SourceSpan.of(start, stream.lastLocation()));
    }

    // =========================================================================
    // Statements
    // =========================================================================

    private CppStatement parseStatement() {
        if (stream.isAtEnd()) {
            return new CppEmptyStmt(SourceSpan.point(stream.location()));
        }

        // 1. Compound statement { ... }
        if (stream.match("{")) {
            return parseCompoundStatementRest(stream.lastConsumed().startLocation());
        }

        // 2. Control flow: if
        if (stream.match("if")) {
            return parseIfStatement();
        }

        // 3. Control flow: switch
        if (stream.match("switch")) {
            return parseSwitchStatement();
        }

        // 4. Loops: for
        if (stream.match("for")) {
            return parseForStatement();
        }

        // 5. Loops: while
        if (stream.match("while")) {
            return parseWhileStatement();
        }

        // 6. Loops: do-while
        if (stream.match("do")) {
            return parseDoWhileStatement();
        }

        // 7. Jumps: return, break, continue
        if (stream.match("return")) {
            return parseReturnStatement();
        }
        if (stream.match("break")) {
            SourceLocation start = stream.lastConsumed().startLocation();
            expectSemicolon("Missing ';' after break");
            return new CppBreakStmt(SourceSpan.of(start, stream.lastLocation()));
        }
        if (stream.match("continue")) {
            SourceLocation start = stream.lastConsumed().startLocation();
            expectSemicolon("Missing ';' after continue");
            return new CppContinueStmt(SourceSpan.of(start, stream.lastLocation()));
        }

        // 8. Empty statement ;
        if (stream.match(";")) {
            return new CppEmptyStmt(SourceSpan.point(stream.lastLocation()));
        }

        // 9. Declaration statement vs Expression statement
        SourceLocation stmtStart = stream.location();
        if (isStartOfDeclaration()) {
            CppAstNode decl = parseFunctionOrVariable(false, null);
            if (decl != null) {
                return new CppDeclarationStmt(decl, SourceSpan.of(stmtStart, stream.lastLocation()));
            }
        }

        // Expression statement
        CppExpr expr = parseExpression();
        expectSemicolon("Missing ';' after statement");
        return new CppExprStmt(expr, SourceSpan.of(stmtStart, stream.lastLocation()));
    }

    private CppStatement parseCompoundStatementRest(SourceLocation start) {
        List<CppStatement> stmts = new ArrayList<>();
        while (!stream.check("}") && !stream.isAtEnd()) {
            if (stream.match("case")) {
                stmts.add(parseCaseStatementRest(stream.lastConsumed().startLocation()));
                continue;
            }
            if (stream.match("default")) {
                stmts.add(parseDefaultStatementRest(stream.lastConsumed().startLocation()));
                continue;
            }
            stmts.add(parseStatement());
        }
        expectCloseBrace();
        return new CppCompoundStmt(stmts, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseIfStatement() {
        SourceLocation start = stream.lastConsumed().startLocation();
        expectOpenParen();
        CppExpr cond = parseExpression();
        expectCloseParen();

        CppStatement thenBranch = parseStatement();
        CppStatement elseBranch = null;
        if (stream.match("else")) {
            elseBranch = parseStatement();
        }

        return new CppIfStmt(cond, thenBranch, elseBranch, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseSwitchStatement() {
        SourceLocation start = stream.lastConsumed().startLocation();
        expectOpenParen();
        CppExpr cond = parseExpression();
        expectCloseParen();

        CppStatement body = parseStatement();
        return new CppSwitchStmt(cond, body, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseCaseStatementRest(SourceLocation start) {
        CppExpr val = parseExpression();
        expectColon();
        List<CppStatement> body = new ArrayList<>();
        while (!stream.check("case") && !stream.check("default") && !stream.check("}") && !stream.isAtEnd()) {
            body.add(parseStatement());
        }
        return new CppCaseStmt(val, body, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseDefaultStatementRest(SourceLocation start) {
        expectColon();
        List<CppStatement> body = new ArrayList<>();
        while (!stream.check("case") && !stream.check("default") && !stream.check("}") && !stream.isAtEnd()) {
            body.add(parseStatement());
        }
        return new CppDefaultStmt(body, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseForStatement() {
        SourceLocation start = stream.lastConsumed().startLocation();
        expectOpenParen();

        // Check if range-based for: e.g. for (auto x : vec) or for (int i : arr)
        if (isRangeForHeader()) {
            String type = parseTypeString();
            String varName = stream.check(TokenType.IDENTIFIER) ? stream.consume().lexeme() : "";
            expectColon();
            CppExpr rangeExpr = parseExpression();
            expectCloseParen();
            CppStatement body = parseStatement();
            return new CppRangeForStmt(type, varName, rangeExpr, body, SourceSpan.of(start, stream.lastLocation()));
        }

        // Classic for (init; cond; step)
        CppAstNode init = null;
        if (!stream.check(";")) {
            if (isStartOfDeclaration()) {
                init = parseFunctionOrVariable(false, null);
            } else {
                CppExpr initExpr = parseExpression();
                expectSemicolon("Missing ';' after for init");
                init = new CppExprStmt(initExpr, initExpr.span());
            }
        } else {
            stream.consume(); // ";"
        }

        CppExpr cond = null;
        if (!stream.check(";")) {
            cond = parseExpression();
        }
        expectSemicolon("Missing ';' after for condition");

        CppExpr step = null;
        if (!stream.check(")")) {
            step = parseExpression();
        }
        expectCloseParen();

        CppStatement body = parseStatement();
        return new CppForStmt(init, cond, step, body, SourceSpan.of(start, stream.lastLocation()));
    }

    private boolean isRangeForHeader() {
        int mark = stream.mark();
        int parenDepth = 0;
        boolean hasColon = false;
        for (int i = 0; !stream.isAtEnd(i); i++) {
            Token t = stream.peek(i);
            if (t.lexeme().equals("(")) {
                parenDepth++;
            } else if (t.lexeme().equals(")")) {
                if (parenDepth == 0) break;
                parenDepth--;
            } else if (t.lexeme().equals(";") && parenDepth == 0) {
                hasColon = false;
                break;
            } else if (t.lexeme().equals(":") && parenDepth == 0) {
                hasColon = true;
                break;
            }
        }
        stream.restore(mark);
        return hasColon;
    }

    private CppStatement parseWhileStatement() {
        SourceLocation start = stream.lastConsumed().startLocation();
        expectOpenParen();
        CppExpr cond = parseExpression();
        expectCloseParen();
        CppStatement body = parseStatement();
        return new CppWhileStmt(cond, body, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseDoWhileStatement() {
        SourceLocation start = stream.lastConsumed().startLocation();
        CppStatement body = parseStatement();
        if (!stream.match("while")) {
            reportError(CheckCategory.CONTROL_HEADER, ERR_CPP_CONTROL_HEADER,
                "Expected 'while' after 'do' body", "Insert 'while (condition);'");
            return new CppDoWhileStmt(body, new CppErrorExpr(SourceSpan.point(stream.location())),
                SourceSpan.of(start, stream.lastLocation()));
        }
        expectOpenParen();
        CppExpr cond = parseExpression();
        expectCloseParen();
        expectSemicolon("Missing ';' after do-while statement");
        return new CppDoWhileStmt(body, cond, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppStatement parseReturnStatement() {
        SourceLocation start = stream.lastConsumed().startLocation();
        CppExpr expr = null;
        if (!stream.check(";")) {
            expr = parseExpression();
        }
        expectSemicolon("Missing ';' after return statement");
        return new CppReturnStmt(expr, SourceSpan.of(start, stream.lastLocation()));
    }

    // =========================================================================
    // Expressions (Pratt Parser / Precedence Climbing)
    // =========================================================================

    public CppExpr parseExpression() {
        return parsePrecedence(0);
    }

    private CppExpr parsePrecedence(int minPrecedence) {
        CppExpr left = parsePrefix();

        while (!stream.isAtEnd()) {
            // Postfix operators
            if (stream.check("(")) {
                left = parseCallRest(left);
                continue;
            }
            if (stream.check("[")) {
                left = parseSubscriptRest(left);
                continue;
            }
            if (stream.match(".")) {
                String member = parseIdentifierOrOperatorName();
                left = new CppMemberExpr(left, member, false, SourceSpan.of(left.span().start(), stream.lastLocation()));
                continue;
            }
            if (stream.match("->")) {
                String member = parseIdentifierOrOperatorName();
                left = new CppMemberExpr(left, member, true, SourceSpan.of(left.span().start(), stream.lastLocation()));
                continue;
            }
            if (stream.match("::")) {
                String member = parseIdentifierOrOperatorName();
                left = new CppScopeExpr(left, member, SourceSpan.of(left.span().start(), stream.lastLocation()));
                continue;
            }
            if (stream.match("++")) {
                left = new CppUnaryExpr("++", left, false, SourceSpan.of(left.span().start(), stream.lastLocation()));
                continue;
            }
            if (stream.match("--")) {
                left = new CppUnaryExpr("--", left, false, SourceSpan.of(left.span().start(), stream.lastLocation()));
                continue;
            }

            // Infix operators
            String op = stream.peek().lexeme();
            int prec = getInfixPrecedence(op);
            if (prec <= 0 || prec < minPrecedence) {
                break;
            }

            // Ternary conditional ? :
            if (op.equals("?")) {
                stream.consume();
                CppExpr thenExpr = parseExpression();
                expectColon();
                CppExpr elseExpr = parsePrecedence(2);
                left = new CppTernaryExpr(left, thenExpr, elseExpr, SourceSpan.of(left.span().start(), elseExpr.span().end()));
                continue;
            }

            Token opTok = stream.consume();
            int nextPrec = isRightAssociative(opTok.lexeme()) ? prec : prec + 1;
            CppExpr right = parsePrecedence(nextPrec);
            left = new CppBinaryExpr(opTok.lexeme(), left, right, SourceSpan.of(left.span().start(), right.span().end()));
        }

        return left;
    }

    private CppExpr parsePrefix() {
        Token tok = stream.peek();

        // 1. Numeric Literals
        if (tok.tokenType() == TokenType.LITERAL_NUMBER) {
            stream.consume();
            return new CppLiteralExpr(tok.lexeme(), "number", tok.span());
        }

        // 2. String Literals
        if (tok.tokenType() == TokenType.LITERAL_STRING) {
            stream.consume();
            return new CppLiteralExpr(tok.lexeme(), "string", tok.span());
        }

        // 3. Character Literals
        if (tok.tokenType() == TokenType.LITERAL_CHAR) {
            stream.consume();
            return new CppLiteralExpr(tok.lexeme(), "char", tok.span());
        }

        // 4. Boolean and null Literals
        if (tok.lexeme().equals("true") || tok.lexeme().equals("false")) {
            stream.consume();
            return new CppLiteralExpr(tok.lexeme(), "bool", tok.span());
        }
        if (tok.lexeme().equals("nullptr")) {
            stream.consume();
            return new CppLiteralExpr(tok.lexeme(), "nullptr", tok.span());
        }

        // 5. 'this'
        if (tok.lexeme().equals("this")) {
            stream.consume();
            return new CppIdentifierExpr("this", tok.span());
        }

        // 6. Parenthesized (expr)
        if (stream.match("(")) {
            CppExpr inner = parseExpression();
            expectCloseParen();
            return inner;
        }

        // 7. Initializer list {1, 2, 3}
        if (stream.match("{")) {
            return parseInitializerListRest(stream.lastConsumed().startLocation());
        }

        // 8. Prefix unary operators (++i, --i, !x, ~x, +x, -x, *p, &x, sizeof)
        if (isPrefixOperator(tok.lexeme())) {
            Token op = stream.consume();
            CppExpr operand = parsePrecedence(14);
            return new CppUnaryExpr(op.lexeme(), operand, true, SourceSpan.of(op.startLocation(), operand.span().end()));
        }

        // 9. Global scope resolution ::id
        if (stream.match("::")) {
            SourceLocation start = stream.lastConsumed().startLocation();
            String member = parseIdentifierOrOperatorName();
            return new CppScopeExpr(null, member, SourceSpan.of(start, stream.lastLocation()));
        }

        // 10. Cast expressions static_cast<Type>(expr)
        if (isCastKeyword(tok.lexeme())) {
            Token castTok = stream.consume();
            if (stream.match("<")) {
                parseTypeString();
                expect(">");
            }
            expectOpenParen();
            CppExpr arg = parseExpression();
            expectCloseParen();
            return new CppCallExpr(new CppIdentifierExpr(castTok.lexeme(), castTok.span()), List.of(arg),
                SourceSpan.of(castTok.startLocation(), stream.lastLocation()));
        }

        // 11. Identifiers
        if (tok.tokenType() == TokenType.IDENTIFIER) {
            stream.consume();
            return new CppIdentifierExpr(tok.lexeme(), tok.span());
        }

        // Fallback / error node
        reportError(CheckCategory.OPERATOR_SYNTAX, ERR_CPP_UNEXPECTED_TOKEN,
            "Unexpected token '" + tok.lexeme() + "' in expression", "Remove or replace token");
        stream.consume();
        return new CppErrorExpr(tok.span());
    }

    private CppExpr parseCallRest(CppExpr callee) {
        stream.consume(); // "("
        List<CppExpr> args = new ArrayList<>();
        while (!stream.check(")") && !stream.isAtEnd()) {
            args.add(parseExpression());
            if (!stream.match(",")) {
                break;
            }
        }
        expectCloseParen();
        return new CppCallExpr(callee, args, SourceSpan.of(callee.span().start(), stream.lastLocation()));
    }

    private CppExpr parseSubscriptRest(CppExpr array) {
        stream.consume(); // "["
        CppExpr index = parseExpression();
        expectCloseBracket();
        return new CppSubscriptExpr(array, index, SourceSpan.of(array.span().start(), stream.lastLocation()));
    }

    private CppExpr parseInitializerListRest(SourceLocation start) {
        List<CppExpr> elems = new ArrayList<>();
        while (!stream.check("}") && !stream.isAtEnd()) {
            elems.add(parseExpression());
            if (!stream.match(",")) {
                break;
            }
        }
        expectCloseBrace();
        return new CppInitializerListExpr(elems, SourceSpan.of(start, stream.lastLocation()));
    }

    private CppExpr parseParenthesizedExpressionOrArgList() {
        CppExpr expr = parseExpression();
        expectCloseParen();
        return expr;
    }

    private static int getInfixPrecedence(String op) {
        return switch (op) {
            case "=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=" -> 1;
            case "?" -> 2;
            case "||" -> 3;
            case "&&" -> 4;
            case "|" -> 5;
            case "^" -> 6;
            case "&" -> 7;
            case "==", "!=" -> 8;
            case "<", "<=", ">", ">=", "<=>" -> 9;
            case "<<", ">>" -> 10;
            case "+", "-" -> 11;
            case "*", "/", "%" -> 12;
            case ".*", "->*" -> 13;
            default -> 0;
        };
    }

    private static boolean isRightAssociative(String op) {
        return op.equals("=") || op.endsWith("=") || op.equals("?");
    }

    private static boolean isPrefixOperator(String op) {
        return op.equals("++") || op.equals("--") || op.equals("+") || op.equals("-")
            || op.equals("!") || op.equals("~") || op.equals("*") || op.equals("&")
            || op.equals("sizeof");
    }

    private static boolean isCastKeyword(String kw) {
        return kw.equals("static_cast") || kw.equals("dynamic_cast")
            || kw.equals("reinterpret_cast") || kw.equals("const_cast");
    }

    // =========================================================================
    // Type and Identifier Helpers
    // =========================================================================

    private boolean isStartOfType() {
        Token t = stream.peek();
        if (t.tokenType() == TokenType.KEYWORD) {
            return TYPE_KEYWORDS.contains(t.lexeme())
                || MODIFIER_KEYWORDS.contains(t.lexeme())
                || t.lexeme().equals("struct") || t.lexeme().equals("class");
        }
        if (t.tokenType() == TokenType.IDENTIFIER) {
            return true;
        }
        return t.lexeme().equals("::");
    }

    private boolean isStartOfDeclaration() {
        Token t0 = stream.peek();
        if (t0.tokenType() == TokenType.KEYWORD) {
            if (TYPE_KEYWORDS.contains(t0.lexeme()) || MODIFIER_KEYWORDS.contains(t0.lexeme())) {
                return true;
            }
            if (DECLARATION_KEYWORDS.contains(t0.lexeme())) {
                return true;
            }
        }
        // Identifier followed by *, &, another identifier, or <...> then identifier
        if (t0.tokenType() == TokenType.IDENTIFIER) {
            Token t1 = stream.peek(1);
            if (t1.lexeme().equals("*") || t1.lexeme().equals("&")) {
                return true;
            }
            if (t1.tokenType() == TokenType.IDENTIFIER) {
                return true;
            }
            if (t1.lexeme().equals("<") || t1.lexeme().equals("::")) {
                // Lookahead to see if a second identifier follows
                int mark = stream.mark();
                parseTypeString();
                boolean isDecl = stream.check(TokenType.IDENTIFIER) || stream.check("*") || stream.check("&");
                stream.restore(mark);
                return isDecl;
            }
        }
        return false;
    }

    private List<String> parseModifiers() {
        List<String> mods = new ArrayList<>();
        while (MODIFIER_KEYWORDS.contains(stream.peek().lexeme())) {
            mods.add(stream.consume().lexeme());
        }
        return mods;
    }

    private List<String> parseFunctionQualifiers() {
        List<String> quals = new ArrayList<>();
        while (true) {
            String lex = stream.peek().lexeme();
            if (lex.equals("const") || lex.equals("noexcept") || lex.equals("override") || lex.equals("final")) {
                quals.add(stream.consume().lexeme());
            } else {
                break;
            }
        }
        return quals;
    }

    private String parseTypeString() {
        StringBuilder sb = new StringBuilder();

        // Modifiers
        while (MODIFIER_KEYWORDS.contains(stream.peek().lexeme())) {
            sb.append(stream.consume().lexeme()).append(" ");
        }

        // Struct/class elaborated type
        if (stream.check("struct") || stream.check("class")) {
            sb.append(stream.consume().lexeme()).append(" ");
        }

        // Primitive types (including compound like unsigned long long)
        if (TYPE_KEYWORDS.contains(stream.peek().lexeme())) {
            while (TYPE_KEYWORDS.contains(stream.peek().lexeme())) {
                sb.append(stream.consume().lexeme()).append(" ");
            }
        } else if (stream.check(TokenType.IDENTIFIER) || stream.check("::")) {
            sb.append(parseQualifiedName());
            if (stream.check("<")) {
                sb.append(parseTemplateArgumentListString());
            }
            // Trailing scope resolution in type e.g. std::vector<int>::iterator
            while (stream.match("::")) {
                sb.append("::").append(stream.consume().lexeme());
                if (stream.check("<")) {
                    sb.append(parseTemplateArgumentListString());
                }
            }
            sb.append(" ");
        }

        // Pointers, references, and trailing const
        while (true) {
            if (stream.match("*")) {
                while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                    sb.setLength(sb.length() - 1);
                }
                sb.append("*");
                if (stream.check("const")) {
                    sb.append(" ").append(stream.consume().lexeme());
                }
            } else if (stream.match("&")) {
                while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                    sb.setLength(sb.length() - 1);
                }
                sb.append("&");
            } else if (stream.check("const")) {
                sb.append(" ").append(stream.consume().lexeme());
            } else {
                break;
            }
        }

        return sb.toString().trim();
    }

    private String parseQualifiedName() {
        StringBuilder sb = new StringBuilder();
        if (stream.match("::")) {
            sb.append("::");
        }
        if (stream.check(TokenType.IDENTIFIER)) {
            sb.append(stream.consume().lexeme());
        }
        while (stream.match("::")) {
            sb.append("::");
            if (stream.check(TokenType.IDENTIFIER)) {
                sb.append(stream.consume().lexeme());
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private String parseOperatorName() {
        stream.consume(); // "operator" or operator token
        StringBuilder sb = new StringBuilder("operator");
        if (stream.peek().tokenType() == TokenType.OPERATOR || stream.peek().tokenType() == TokenType.DELIMITER) {
            sb.append(stream.consume().lexeme());
            if (stream.match("()")) {
                sb.append("()");
            } else if (stream.match("[]")) {
                sb.append("[]");
            }
        }
        return sb.toString();
    }

    private String parseIdentifierOrOperatorName() {
        if (stream.check("operator")) {
            return parseOperatorName();
        }
        if (stream.check(TokenType.IDENTIFIER)) {
            return stream.consume().lexeme();
        }
        return "";
    }

    private String parseTemplateArgumentListString() {
        StringBuilder sb = new StringBuilder("<");
        stream.consume(); // "<"
        int depth = 1;
        while (!stream.isAtEnd() && depth > 0) {
            Token t = stream.consume();
            if (t.lexeme().equals("<")) {
                depth++;
            } else if (t.lexeme().equals(">")) {
                depth--;
            }
            sb.append(t.lexeme());
        }
        return sb.toString();
    }

    private List<CppParam> parseParameterList() {
        expectOpenParen();
        List<CppParam> params = new ArrayList<>();

        if (stream.check(")")) {
            expectCloseParen();
            return params;
        }

        while (!stream.check(")") && !stream.isAtEnd()) {
            SourceLocation pStart = stream.location();
            String type = parseTypeString();
            String name = "";
            if (stream.check(TokenType.IDENTIFIER)) {
                name = stream.consume().lexeme();
            }
            String defaultVal = null;
            if (stream.match("=")) {
                defaultVal = parseExpressionString();
            }
            params.add(new CppParam(type, name, defaultVal, SourceSpan.of(pStart, stream.lastLocation())));

            if (!stream.match(",")) {
                break;
            }
        }

        expectCloseParen();
        return params;
    }

    private String parseExpressionString() {
        StringBuilder sb = new StringBuilder();
        int paren = 0;
        int brace = 0;
        int bracket = 0;
        while (!stream.isAtEnd()) {
            String lex = stream.peek().lexeme();
            if (paren == 0 && brace == 0 && bracket == 0 && (lex.equals(",") || lex.equals(")") || lex.equals(";") || lex.equals("{"))) {
                break;
            }
            Token t = stream.consume();
            sb.append(t.lexeme()).append(" ");
            if (t.lexeme().equals("(")) paren++;
            else if (t.lexeme().equals(")")) paren--;
            else if (t.lexeme().equals("{")) brace++;
            else if (t.lexeme().equals("}")) brace--;
            else if (t.lexeme().equals("[")) bracket++;
            else if (t.lexeme().equals("]")) bracket--;
        }
        return sb.toString().trim();
    }

    // =========================================================================
    // Delimiter & Token Expectation Helpers
    // =========================================================================

    private void expectOpenParen() {
        if (!stream.match("(")) {
            reportError(CheckCategory.CONTROL_HEADER, ERR_CPP_CONTROL_HEADER,
                "Expected '('", "Insert '('");
        }
    }

    private void expectCloseParen() {
        if (!stream.match(")")) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected ')'", "Insert ')'");
        }
    }

    private void expectCloseBrace() {
        if (!stream.match("}")) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected '}'", "Insert '}'");
        }
    }

    private void expectCloseBracket() {
        if (!stream.match("]")) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected ']'", "Insert ']'");
        }
    }

    private void expectColon() {
        if (!stream.match(":")) {
            reportError(CheckCategory.CONTROL_HEADER, ERR_CPP_CONTROL_HEADER,
                "Expected ':'", "Insert ':'");
        }
    }

    private void expectSemicolon(String message) {
        if (stream.match(";")) {
            return;
        }
        reportError(CheckCategory.STATEMENT_TERMINATOR, ERR_CPP_MISSING_SEMICOLON,
            message, "Insert ';' at the end");
    }

    private void expect(String lexeme) {
        if (!stream.match(lexeme)) {
            reportError(CheckCategory.DELIMITER_MATCH, ERR_CPP_UNCLOSED_DELIMITER,
                "Expected '" + lexeme + "'", "Insert '" + lexeme + "'");
        }
    }

    private void reportError(CheckCategory category, String code, String message, String suggestedFix) {
        SourceLocation loc = stream.lastLocation();
        diagnostics.add(Diagnostic.error(category, loc, message, code, suggestedFix));
    }

    private void synchronize() {
        while (!stream.isAtEnd()) {
            if (stream.check(";")) {
                stream.consume();
                return;
            }
            if (stream.check("}")) {
                return;
            }
            String lex = stream.peek().lexeme();
            if (DECLARATION_KEYWORDS.contains(lex) || TYPE_KEYWORDS.contains(lex) || MODIFIER_KEYWORDS.contains(lex)) {
                return;
            }
            stream.consume();
        }
    }
}
