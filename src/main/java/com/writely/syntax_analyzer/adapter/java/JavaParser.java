package com.writely.syntax_analyzer.adapter.java;

import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.adapter.java.ast.*;
import com.writely.syntax_analyzer.domain.*;

import java.util.*;

/**
 * Recursive-descent and precedence-climbing Java parser adhering to ADR 0009.
 */
public class JavaParser {

    private static final Set<String> MODIFIERS = Set.of(
        "public", "protected", "private", "static", "final", "abstract",
        "sealed", "non-sealed", "strictfp", "default", "native", "synchronized",
        "transient", "volatile"
    );

    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "byte", "short", "int", "long", "float", "double", "boolean", "char", "void", "var"
    );

    private static final Set<String> ASSIGNMENT_OPERATORS = Set.of(
        "=", "+=", "-=", "*=", "/=", "%=", "&=", "^=", "|=", "<<=", ">>=", ">>>="
    );

    public ParseResult parse(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        if (tokens.isEmpty()) {
            SourceSpan emptySpan = SourceSpan.point(SourceLocation.start());
            JavaCompilationUnit emptyCu = new JavaCompilationUnit(
                payload.sourceName(),
                Optional.empty(),
                List.of(),
                List.of(),
                emptySpan
            );
            SyntaxNode root = JavaAstMapper.toSyntaxNode(emptyCu);
            return ParseResult.of(root, List.of(), emptyCu, tokens);
        }

        JavaTokenStream stream = new JavaTokenStream(tokens);
        SourceLocation startLoc = stream.hasMore() ? stream.peek().startLocation() : SourceLocation.start();

        // 1. Optional package declaration
        Optional<JavaPackageDeclaration> packageDecl = Optional.empty();
        if (stream.check("package")) {
            packageDecl = Optional.of(parsePackageDeclaration(stream));
        }

        // 2. Imports
        List<JavaImportDeclaration> imports = new ArrayList<>();
        while (stream.check("import")) {
            imports.add(parseImportDeclaration(stream));
        }

        // 3. Top-level declarations and/or statements
        List<JavaAstNode> declarations = new ArrayList<>();
        while (stream.hasMore()) {
            try {
                if (stream.check(";")) {
                    stream.consume();
                    continue;
                }
                if (isTypeDeclarationLookahead(stream)) {
                    declarations.add(parseTypeDeclaration(stream, null));
                } else {
                    // Top-level statements / snippets / commands
                    JavaStatement stmt = parseStatement(stream);
                    if (stmt != null) {
                        declarations.add(stmt);
                    }
                }
            } catch (Exception e) {
                // Defensive recovery to ensure no parser crash
                SourceLocation errLoc = stream.currentLocation();
                stream.addDiagnostic(Diagnostic.error(
                    CheckCategory.STATEMENT_TERMINATOR,
                    errLoc,
                    "Syntax error during parsing: " + e.getMessage(),
                    "ERR_JAVA_PARSER_ERROR",
                    "Review syntax near location"
                ));
                synchronizeStatement(stream);
            }
        }

        SourceLocation endLoc = stream.previousEndLocation();
        if (endLoc.compareTo(startLoc) < 0) {
            endLoc = startLoc;
        }
        SourceSpan cuSpan = SourceSpan.of(startLoc, endLoc);

        JavaCompilationUnit cu = new JavaCompilationUnit(
            payload.sourceName(),
            packageDecl,
            imports,
            declarations,
            cuSpan
        );

        SyntaxNode rootNode = JavaAstMapper.toSyntaxNode(cu);
        return ParseResult.of(rootNode, stream.diagnostics(), cu, tokens);
    }

    // =========================================================================
    // Declarations: Package, Import, Types
    // =========================================================================

    private JavaPackageDeclaration parsePackageDeclaration(JavaTokenStream stream) {
        Token pkgToken = stream.consume(); // 'package'
        List<JavaAnnotation> annotations = List.of();
        String name = parseQualifiedName(stream);
        stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
            "Missing ';' after package declaration", "Insert ';' at the end of package declaration");
        SourceSpan span = spanBetween(pkgToken.startLocation(), stream.previousEndLocation());
        return new JavaPackageDeclaration(name, annotations, span);
    }

    private JavaImportDeclaration parseImportDeclaration(JavaTokenStream stream) {
        Token impToken = stream.consume(); // 'import'
        boolean isStatic = stream.match("static");
        String name = parseQualifiedName(stream);
        boolean isWildcard = false;
        if (stream.match(".")) {
            if (stream.match("*")) {
                isWildcard = true;
                name = name + ".*";
            }
        }
        stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
            "Missing ';' after import declaration", "Insert ';' at the end of import declaration");
        SourceSpan span = spanBetween(impToken.startLocation(), stream.previousEndLocation());
        return new JavaImportDeclaration(name, isStatic, isWildcard, span);
    }

    private JavaAstNode parseTypeDeclaration(JavaTokenStream stream, String enclosingName) {
        SourceLocation startLoc = stream.currentLocation();
        List<JavaAnnotation> annotations = parseAnnotations(stream);
        List<String> modifiers = parseModifiers(stream);

        if (stream.match("class")) {
            return parseClassDeclarationRest(stream, startLoc, annotations, modifiers);
        } else if (stream.match("interface")) {
            return parseInterfaceDeclarationRest(stream, startLoc, annotations, modifiers);
        } else if (stream.match("record")) {
            return parseRecordDeclarationRest(stream, startLoc, annotations, modifiers);
        } else if (stream.match("enum")) {
            return parseEnumDeclarationRest(stream, startLoc, annotations, modifiers);
        } else {
            // Fallback unexpected
            Token tok = stream.consume();
            stream.addDiagnostic(Diagnostic.error(
                CheckCategory.IDENTIFIER_NAMING,
                tok.startLocation(),
                "Expected class, interface, record, or enum declaration",
                "ERR_JAVA_EXPECTED_TYPE_DECLARATION",
                "Declare class, interface, record, or enum"
            ));
            return new JavaEmptyStatement(SourceSpan.point(tok.startLocation()));
        }
    }

    private JavaClassDeclaration parseClassDeclarationRest(
        JavaTokenStream stream,
        SourceLocation startLoc,
        List<JavaAnnotation> annotations,
        List<String> modifiers
    ) {
        Token nameToken = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected class name identifier after 'class'", "Provide class name");
        String name = nameToken.lexeme();

        List<JavaTypeParameter> typeParams = parseTypeParameters(stream);
        Optional<JavaType> superclass = Optional.empty();
        if (stream.match("extends")) {
            superclass = Optional.of(parseType(stream));
        }

        List<JavaType> interfaces = List.of();
        if (stream.match("implements")) {
            interfaces = parseTypeList(stream);
        }

        List<JavaType> permits = List.of();
        if (stream.match("permits")) {
            permits = parseTypeList(stream);
        }

        List<JavaAstNode> members = parseClassBody(stream, name);
        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaClassDeclaration(name, modifiers, annotations, typeParams, superclass, interfaces, permits, members, span);
    }

    private JavaInterfaceDeclaration parseInterfaceDeclarationRest(
        JavaTokenStream stream,
        SourceLocation startLoc,
        List<JavaAnnotation> annotations,
        List<String> modifiers
    ) {
        Token nameToken = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected interface name identifier after 'interface'", "Provide interface name");
        String name = nameToken.lexeme();

        List<JavaTypeParameter> typeParams = parseTypeParameters(stream);
        List<JavaType> extended = List.of();
        if (stream.match("extends")) {
            extended = parseTypeList(stream);
        }

        List<JavaType> permits = List.of();
        if (stream.match("permits")) {
            permits = parseTypeList(stream);
        }

        List<JavaAstNode> members = parseClassBody(stream, name);
        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaInterfaceDeclaration(name, modifiers, annotations, typeParams, extended, permits, members, span);
    }

    private JavaRecordDeclaration parseRecordDeclarationRest(
        JavaTokenStream stream,
        SourceLocation startLoc,
        List<JavaAnnotation> annotations,
        List<String> modifiers
    ) {
        Token nameToken = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected record name identifier after 'record'", "Provide record name");
        String name = nameToken.lexeme();

        List<JavaTypeParameter> typeParams = parseTypeParameters(stream);
        List<JavaParameter> components = parseRecordComponents(stream);

        List<JavaType> interfaces = List.of();
        if (stream.match("implements")) {
            interfaces = parseTypeList(stream);
        }

        List<JavaAstNode> members = parseClassBody(stream, name);
        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaRecordDeclaration(name, modifiers, annotations, typeParams, components, interfaces, members, span);
    }

    private JavaEnumDeclaration parseEnumDeclarationRest(
        JavaTokenStream stream,
        SourceLocation startLoc,
        List<JavaAnnotation> annotations,
        List<String> modifiers
    ) {
        Token nameToken = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected enum name identifier after 'enum'", "Provide enum name");
        String name = nameToken.lexeme();

        List<JavaType> interfaces = List.of();
        if (stream.match("implements")) {
            interfaces = parseTypeList(stream);
        }

        stream.expect("{", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Expected '{' to open enum body", "Insert '{' before enum body");

        List<JavaEnumConstant> constants = new ArrayList<>();
        while (stream.hasMore() && !stream.check("}") && !stream.check(";")) {
            constants.add(parseEnumConstant(stream));
            if (!stream.match(",")) {
                break;
            }
        }

        if (stream.match(";")) {
            // Optional members after enum constants
        }

        List<JavaAstNode> members = new ArrayList<>();
        while (stream.hasMore() && !stream.check("}")) {
            if (stream.check(";")) {
                stream.consume();
                continue;
            }
            members.add(parseMemberDeclaration(stream, name));
        }

        stream.expect("}", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Missing '}' at end of enum body", "Insert '}' to close enum");

        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaEnumDeclaration(name, modifiers, annotations, interfaces, constants, members, span);
    }

    private JavaEnumConstant parseEnumConstant(JavaTokenStream stream) {
        SourceLocation startLoc = stream.currentLocation();
        List<JavaAnnotation> annotations = parseAnnotations(stream);
        Token nameToken = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected enum constant identifier", "Provide enum constant identifier");
        String name = nameToken.lexeme();

        List<JavaExpression> arguments = List.of();
        if (stream.match("(")) {
            arguments = parseArgumentList(stream);
            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after enum constant arguments", "Insert ')'");
        }

        List<JavaAstNode> classBody = List.of();
        if (stream.check("{")) {
            classBody = parseClassBody(stream, name);
        }

        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaEnumConstant(name, annotations, arguments, classBody, span);
    }

    private List<JavaAstNode> parseClassBody(JavaTokenStream stream, String className) {
        stream.expect("{", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Expected '{' to begin class body", "Insert '{' before body");

        List<JavaAstNode> members = new ArrayList<>();
        while (stream.hasMore() && !stream.check("}")) {
            if (stream.check(";")) {
                stream.consume();
                continue;
            }
            try {
                members.add(parseMemberDeclaration(stream, className));
            } catch (Exception e) {
                SourceLocation loc = stream.currentLocation();
                stream.addDiagnostic(Diagnostic.error(
                    CheckCategory.STATEMENT_TERMINATOR,
                    loc,
                    "Syntax error in member declaration: " + e.getMessage(),
                    "ERR_JAVA_MEMBER_SYNTAX",
                    "Fix member declaration"
                ));
                synchronizeMember(stream);
            }
        }

        stream.expect("}", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Missing '}' at end of class body", "Insert '}' to close class body");
        return members;
    }

    private JavaAstNode parseMemberDeclaration(JavaTokenStream stream, String className) {
        SourceLocation startLoc = stream.currentLocation();

        // 1. Initializer blocks
        if (stream.check("static") && stream.peek(1).lexeme().equals("{")) {
            stream.consume(); // 'static'
            JavaBlock block = parseBlock(stream);
            return new JavaInitializerBlock(true, block, spanBetween(startLoc, block.span().end()));
        }
        if (stream.check("{")) {
            JavaBlock block = parseBlock(stream);
            return new JavaInitializerBlock(false, block, spanBetween(startLoc, block.span().end()));
        }

        // 2. Modifiers and annotations
        List<JavaAnnotation> annotations = parseAnnotations(stream);
        List<String> modifiers = parseModifiers(stream);

        // 3. Nested type declarations
        if (isTypeDeclarationLookahead(stream)) {
            return parseTypeDeclaration(stream, className);
        }

        // 4. Generic type parameters (for generic constructors/methods)
        List<JavaTypeParameter> typeParams = List.of();
        if (stream.check("<")) {
            typeParams = parseTypeParameters(stream);
        }

        // 5. Constructor or Record Compact Constructor check
        if (className != null && stream.check(TokenType.IDENTIFIER, className)) {
            if (stream.peek(1).lexeme().equals("(")) {
                // Constructor
                Token nameTok = stream.consume();
                stream.consume(); // '('
                List<JavaParameter> params = parseParameterList(stream);
                stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                    "Missing ')' after constructor parameters", "Insert ')'");

                List<JavaType> thrown = List.of();
                if (stream.match("throws")) {
                    thrown = parseTypeList(stream);
                }

                JavaBlock body = parseBlock(stream);
                SourceSpan span = spanBetween(startLoc, body.span().end());
                return new JavaConstructorDeclaration(nameTok.lexeme(), modifiers, annotations, typeParams, params, thrown, false, body, span);
            } else if (stream.peek(1).lexeme().equals("{")) {
                // Record compact constructor
                Token nameTok = stream.consume();
                JavaBlock body = parseBlock(stream);
                SourceSpan span = spanBetween(startLoc, body.span().end());
                return new JavaConstructorDeclaration(nameTok.lexeme(), modifiers, annotations, typeParams, List.of(), List.of(), true, body, span);
            }
        }

        // 6. Field or Method
        JavaType returnOrFieldType = parseType(stream);

        Token nameToken = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected member name identifier", "Provide field or method name");
        String name = nameToken.lexeme();

        if (stream.match("(")) {
            // Method declaration
            List<JavaParameter> params = parseParameterList(stream);
            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after method parameters", "Insert ')'");

            List<JavaType> thrown = List.of();
            if (stream.match("throws")) {
                thrown = parseTypeList(stream);
            }

            Optional<JavaBlock> body = Optional.empty();
            if (stream.check("{")) {
                body = Optional.of(parseBlock(stream));
            } else {
                stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                    "Missing ';' after abstract or interface method header", "Insert ';' at end of method declaration");
            }

            SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
            return new JavaMethodDeclaration(name, returnOrFieldType, modifiers, annotations, typeParams, params, thrown, body, span);
        } else {
            // Field declaration
            List<JavaVariableDeclarator> variables = new ArrayList<>();
            variables.add(parseVariableDeclaratorRest(stream, nameToken));

            while (stream.match(",")) {
                Token nextName = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                    "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected variable identifier after ','", "Provide identifier name");
                variables.add(parseVariableDeclaratorRest(stream, nextName));
            }

            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' at end of field declaration", "Insert ';' at end of field declaration");

            SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
            return new JavaFieldDeclaration(returnOrFieldType, modifiers, annotations, variables, span);
        }
    }

    // =========================================================================
    // Statements
    // =========================================================================

    public JavaStatement parseStatement(JavaTokenStream stream) {
        if (!stream.hasMore()) {
            return null;
        }

        SourceLocation startLoc = stream.currentLocation();

        // 1. Block statement
        if (stream.check("{")) {
            return parseBlock(stream);
        }

        // 2. Empty statement
        if (stream.match(";")) {
            return new JavaEmptyStatement(SourceSpan.point(startLoc));
        }

        // 3. Conditionals: if
        if (stream.match("if")) {
            return parseIfStatement(stream, startLoc);
        }

        // 4. Switch statement
        if (stream.match("switch")) {
            return parseSwitchStatement(stream, startLoc);
        }

        // 5. Loops: for, while, do-while
        if (stream.match("for")) {
            return parseForStatement(stream, startLoc);
        }
        if (stream.match("while")) {
            return parseWhileStatement(stream, startLoc);
        }
        if (stream.match("do")) {
            return parseDoWhileStatement(stream, startLoc);
        }

        // 6. Exception handling: try
        if (stream.match("try")) {
            return parseTryStatement(stream, startLoc);
        }

        // 7. Transfer statements: return, throw, break, continue, yield, assert
        if (stream.match("return")) {
            Optional<JavaExpression> value = Optional.empty();
            if (!stream.check(";") && !stream.check("}")) {
                value = Optional.of(parseExpression(stream));
            }
            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' after return statement", "Insert ';' at end of return statement");
            return new JavaReturnStatement(value, spanBetween(startLoc, stream.previousEndLocation()));
        }

        if (stream.match("throw")) {
            JavaExpression expr = parseExpression(stream);
            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' after throw statement", "Insert ';' at end of throw statement");
            return new JavaThrowStatement(expr, spanBetween(startLoc, stream.previousEndLocation()));
        }

        if (stream.match("break")) {
            Optional<String> label = Optional.empty();
            if (stream.check(TokenType.IDENTIFIER)) {
                label = Optional.of(stream.consume().lexeme());
            }
            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' after break statement", "Insert ';' at end of break statement");
            return new JavaBreakStatement(label, spanBetween(startLoc, stream.previousEndLocation()));
        }

        if (stream.match("continue")) {
            Optional<String> label = Optional.empty();
            if (stream.check(TokenType.IDENTIFIER)) {
                label = Optional.of(stream.consume().lexeme());
            }
            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' after continue statement", "Insert ';' at end of continue statement");
            return new JavaContinueStatement(label, spanBetween(startLoc, stream.previousEndLocation()));
        }

        if (stream.match("yield")) {
            JavaExpression expr = parseExpression(stream);
            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' after yield statement", "Insert ';' at end of yield statement");
            return new JavaYieldStatement(expr, spanBetween(startLoc, stream.previousEndLocation()));
        }

        if (stream.match("assert")) {
            JavaExpression cond = parseExpression(stream);
            Optional<JavaExpression> detail = Optional.empty();
            if (stream.match(":")) {
                detail = Optional.of(parseExpression(stream));
            }
            stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                "Missing ';' after assert statement", "Insert ';' at end of assert statement");
            return new JavaAssertStatement(cond, detail, spanBetween(startLoc, stream.previousEndLocation()));
        }

        // 8. Variable declaration vs Expression statement
        if (isVariableDeclarationLookahead(stream)) {
            return parseVariableDeclarationStatement(stream, startLoc);
        }

        // 9. Expression statement
        JavaExpression expr = parseExpression(stream);
        stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
            "Missing ';' after statement", "Insert ';' at end of statement");
        return new JavaExpressionStatement(expr, spanBetween(startLoc, stream.previousEndLocation()));
    }

    private JavaBlock parseBlock(JavaTokenStream stream) {
        SourceLocation startLoc = stream.currentLocation();
        stream.expect("{", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Expected '{' to begin block", "Insert '{'");

        List<JavaStatement> stmts = new ArrayList<>();
        while (stream.hasMore() && !stream.check("}")) {
            if (stream.match(";")) {
                continue;
            }
            try {
                JavaStatement s = parseStatement(stream);
                if (s != null) {
                    stmts.add(s);
                }
            } catch (Exception e) {
                SourceLocation loc = stream.currentLocation();
                stream.addDiagnostic(Diagnostic.error(
                    CheckCategory.STATEMENT_TERMINATOR,
                    loc,
                    "Error parsing statement in block: " + e.getMessage(),
                    "ERR_JAVA_BLOCK_SYNTAX",
                    "Fix statement syntax"
                ));
                synchronizeStatement(stream);
            }
        }

        stream.expect("}", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Missing '}' at end of block", "Insert '}' to close block");
        return new JavaBlock(stmts, spanBetween(startLoc, stream.previousEndLocation()));
    }

    private JavaIfStatement parseIfStatement(JavaTokenStream stream, SourceLocation startLoc) {
        if (!stream.match("(")) {
            stream.addDiagnostic(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stream.currentLocation(),
                "Expected '(' after 'if'",
                "ERR_JAVA_INVALID_CONTROL_HEADER",
                "Enclose condition in parentheses '(...)'"
            ));
        }

        JavaExpression condition = parseExpression(stream);
        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Missing ')' after if condition", "Insert ')' after condition");

        JavaStatement thenBranch = parseStatement(stream);
        Optional<JavaStatement> elseBranch = Optional.empty();
        if (stream.match("else")) {
            elseBranch = Optional.ofNullable(parseStatement(stream));
        }

        SourceLocation endLoc = elseBranch.map(e -> e.span().end())
            .orElseGet(() -> thenBranch != null ? thenBranch.span().end() : stream.previousEndLocation());
        return new JavaIfStatement(condition, thenBranch, elseBranch, spanBetween(startLoc, endLoc));
    }

    private JavaSwitchStatement parseSwitchStatement(JavaTokenStream stream, SourceLocation startLoc) {
        if (!stream.match("(")) {
            stream.addDiagnostic(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stream.currentLocation(),
                "Expected '(' after 'switch'",
                "ERR_JAVA_INVALID_CONTROL_HEADER",
                "Enclose selector expression in parentheses '(...)'"
            ));
        }
        JavaExpression selector = parseExpression(stream);
        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Missing ')' after switch selector", "Insert ')'");

        List<JavaSwitchEntry> entries = parseSwitchEntries(stream);
        return new JavaSwitchStatement(selector, entries, spanBetween(startLoc, stream.previousEndLocation()));
    }

    private List<JavaSwitchEntry> parseSwitchEntries(JavaTokenStream stream) {
        stream.expect("{", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Expected '{' before switch block", "Insert '{'");

        List<JavaSwitchEntry> entries = new ArrayList<>();
        while (stream.hasMore() && !stream.check("}")) {
            SourceLocation entryStart = stream.currentLocation();
            boolean isDefault = false;
            List<JavaExpression> labels = new ArrayList<>();

            if (stream.match("default")) {
                isDefault = true;
            } else if (stream.match("case")) {
                labels.add(parseExpression(stream));
                while (stream.match(",")) {
                    labels.add(parseExpression(stream));
                }
            } else {
                // Unexpected token in switch block
                Token unk = stream.consume();
                stream.addDiagnostic(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    unk.startLocation(),
                    "Expected 'case' or 'default' in switch body",
                    "ERR_JAVA_SWITCH_CASE_EXPECTED",
                    "Add 'case' or 'default' label"
                ));
                continue;
            }

            boolean isRule = false;
            if (stream.match("->")) {
                isRule = true;
            } else {
                stream.expect(":", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_SWITCH_COLON_EXPECTED",
                    "Expected ':' or '->' after switch label", "Insert ':' or '->'");
            }

            List<JavaStatement> statements = new ArrayList<>();
            if (isRule) {
                if (stream.check("{")) {
                    statements.add(parseBlock(stream));
                } else {
                    statements.add(parseStatement(stream));
                }
            } else {
                while (stream.hasMore() && !stream.check("case") && !stream.check("default") && !stream.check("}")) {
                    JavaStatement s = parseStatement(stream);
                    if (s != null) {
                        statements.add(s);
                    }
                }
            }

            SourceSpan entrySpan = spanBetween(entryStart, stream.previousEndLocation());
            entries.add(new JavaSwitchEntry(labels, isDefault, isRule, statements, entrySpan));
        }

        stream.expect("}", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
            "Missing '}' at end of switch body", "Insert '}'");
        return entries;
    }

    private JavaStatement parseForStatement(JavaTokenStream stream, SourceLocation startLoc) {
        if (!stream.match("(")) {
            stream.addDiagnostic(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stream.currentLocation(),
                "Expected '(' after 'for'",
                "ERR_JAVA_INVALID_CONTROL_HEADER",
                "Enclose for loop header in parentheses '(...)'"
            ));
        }

        // Check if enhanced for-each loop: Type var : expr
        if (isVariableDeclarationLookahead(stream)) {
            int pos = stream.position();
            List<String> mods = parseModifiers(stream);
            JavaType type = parseType(stream);
            if (stream.check(TokenType.IDENTIFIER) && stream.peek(1).lexeme().equals(":")) {
                Token varTok = stream.consume();
                stream.consume(); // ':'
                JavaParameter param = new JavaParameter(type, varTok.lexeme(), List.of(), mods, false,
                    spanBetween(type.span().start(), varTok.endLocation()));
                JavaExpression expr = parseExpression(stream);
                stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                    "Missing ')' after for-each expression", "Insert ')'");
                JavaStatement body = parseStatement(stream);
                return new JavaEnhancedForStatement(param, expr, body, spanBetween(startLoc, body.span().end()));
            }
            // Rewind
            stream.seek(pos);
        }

        // Standard 3-part for loop
        Optional<JavaAstNode> init = Optional.empty();
        if (!stream.check(";")) {
            if (isVariableDeclarationLookahead(stream)) {
                init = Optional.of(parseVariableDeclarationStatement(stream, stream.currentLocation()));
            } else {
                JavaExpression initExpr = parseExpression(stream);
                stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
                    "Missing ';' after for init", "Insert ';'");
                init = Optional.of(new JavaExpressionStatement(initExpr, initExpr.span()));
            }
        } else {
            stream.consume(); // ';'
        }

        Optional<JavaExpression> condition = Optional.empty();
        if (!stream.check(";")) {
            condition = Optional.of(parseExpression(stream));
        }
        stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
            "Missing ';' after for condition", "Insert ';'");

        List<JavaExpression> update = new ArrayList<>();
        if (!stream.check(")")) {
            update.add(parseExpression(stream));
            while (stream.match(",")) {
                update.add(parseExpression(stream));
            }
        }
        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Missing ')' after for header", "Insert ')'");

        JavaStatement body = parseStatement(stream);
        return new JavaForStatement(init, condition, update, body, spanBetween(startLoc, body.span().end()));
    }

    private JavaWhileStatement parseWhileStatement(JavaTokenStream stream, SourceLocation startLoc) {
        if (!stream.match("(")) {
            stream.addDiagnostic(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stream.currentLocation(),
                "Expected '(' after 'while'",
                "ERR_JAVA_INVALID_CONTROL_HEADER",
                "Enclose condition in parentheses '(...)'"
            ));
        }
        JavaExpression condition = parseExpression(stream);
        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Missing ')' after while condition", "Insert ')'");

        JavaStatement body = parseStatement(stream);
        return new JavaWhileStatement(condition, body, spanBetween(startLoc, body.span().end()));
    }

    private JavaDoWhileStatement parseDoWhileStatement(JavaTokenStream stream, SourceLocation startLoc) {
        JavaStatement body = parseStatement(stream);
        stream.expect("while", CheckCategory.CONTROL_HEADER, "ERR_JAVA_MISSING_WHILE",
            "Expected 'while' after do body", "Insert 'while'");

        if (!stream.match("(")) {
            stream.addDiagnostic(Diagnostic.error(
                CheckCategory.CONTROL_HEADER,
                stream.currentLocation(),
                "Expected '(' after 'while' in do-while",
                "ERR_JAVA_INVALID_CONTROL_HEADER",
                "Enclose condition in parentheses '(...)'"
            ));
        }
        JavaExpression condition = parseExpression(stream);
        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Missing ')' after do-while condition", "Insert ')'");
        stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
            "Missing ';' after do-while statement", "Insert ';'");

        return new JavaDoWhileStatement(body, condition, spanBetween(startLoc, stream.previousEndLocation()));
    }

    private JavaTryStatement parseTryStatement(JavaTokenStream stream, SourceLocation startLoc) {
        List<JavaAstNode> resources = List.of();
        if (stream.match("(")) {
            resources = parseTryResources(stream);
            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after try resources", "Insert ')'");
        }

        JavaBlock tryBlock = parseBlock(stream);
        List<JavaCatchClause> catchClauses = new ArrayList<>();
        while (stream.match("catch")) {
            SourceLocation catchStart = stream.currentLocation();
            stream.expect("(", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Expected '(' after 'catch'", "Insert '('");

            List<String> mods = parseModifiers(stream);
            JavaType catchType = parseType(stream);
            Token varTok = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected exception variable name in catch clause", "Provide variable name");
            JavaParameter param = new JavaParameter(catchType, varTok.lexeme(), List.of(), mods, false,
                spanBetween(catchType.span().start(), varTok.endLocation()));

            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after catch parameter", "Insert ')'");
            JavaBlock catchBlock = parseBlock(stream);
            catchClauses.add(new JavaCatchClause(param, catchBlock, spanBetween(catchStart, catchBlock.span().end())));
        }

        Optional<JavaBlock> finallyBlock = Optional.empty();
        if (stream.match("finally")) {
            finallyBlock = Optional.of(parseBlock(stream));
        }

        return new JavaTryStatement(resources, tryBlock, catchClauses, finallyBlock, spanBetween(startLoc, stream.previousEndLocation()));
    }

    private List<JavaAstNode> parseTryResources(JavaTokenStream stream) {
        List<JavaAstNode> resources = new ArrayList<>();
        while (stream.hasMore() && !stream.check(")")) {
            SourceLocation resStart = stream.currentLocation();
            if (isVariableDeclarationLookahead(stream)) {
                List<JavaAnnotation> annotations = parseAnnotations(stream);
                List<String> modifiers = parseModifiers(stream);
                JavaType type = parseType(stream);

                List<JavaVariableDeclarator> variables = new ArrayList<>();
                Token firstVar = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                    "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected variable identifier in resource", "Provide variable name");
                variables.add(parseVariableDeclaratorRest(stream, firstVar));

                while (stream.match(",")) {
                    Token nextVar = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                        "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected variable identifier after ','", "Provide variable name");
                    variables.add(parseVariableDeclaratorRest(stream, nextVar));
                }

                if (stream.match(";")) {
                    // optional trailing semicolon before ')' or separating next resource
                }

                resources.add(new JavaVariableDeclarationStatement(type, modifiers, annotations, variables, spanBetween(resStart, stream.previousEndLocation())));
            } else {
                JavaExpression expr = parseExpression(stream);
                if (stream.match(";")) {
                    // optional trailing semicolon
                }
                resources.add(new JavaExpressionStatement(expr, expr.span()));
            }
        }
        return resources;
    }

    private JavaVariableDeclarationStatement parseVariableDeclarationStatement(JavaTokenStream stream, SourceLocation startLoc) {
        List<JavaAnnotation> annotations = parseAnnotations(stream);
        List<String> modifiers = parseModifiers(stream);
        JavaType type = parseType(stream);

        List<JavaVariableDeclarator> variables = new ArrayList<>();
        Token firstVar = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected variable identifier in declaration", "Provide variable name");
        variables.add(parseVariableDeclaratorRest(stream, firstVar));

        while (stream.match(",")) {
            Token nextVar = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected variable identifier after ','", "Provide variable name");
            variables.add(parseVariableDeclaratorRest(stream, nextVar));
        }

        stream.expect(";", CheckCategory.STATEMENT_TERMINATOR, "ERR_JAVA_MISSING_SEMICOLON",
            "Missing ';' after variable declaration", "Insert ';' at end of declaration");

        return new JavaVariableDeclarationStatement(type, modifiers, annotations, variables, spanBetween(startLoc, stream.previousEndLocation()));
    }

    private JavaVariableDeclarator parseVariableDeclaratorRest(JavaTokenStream stream, Token nameToken) {
        int extraDims = 0;
        while (stream.match("[")) {
            stream.expect("]", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET",
                "Missing ']' in array variable declarator", "Insert ']'");
            extraDims++;
        }

        Optional<JavaExpression> initializer = Optional.empty();
        if (stream.match("=")) {
            initializer = Optional.of(parseExpression(stream));
        }

        SourceSpan span = spanBetween(nameToken.startLocation(), stream.previousEndLocation());
        return new JavaVariableDeclarator(nameToken.lexeme(), extraDims, initializer, span);
    }

    // =========================================================================
    // Expressions: Pratt / Precedence Climbing
    // =========================================================================

    public JavaExpression parseExpression(JavaTokenStream stream) {
        return parseAssignmentExpression(stream);
    }

    private JavaExpression parseAssignmentExpression(JavaTokenStream stream) {
        JavaExpression expr = parseTernaryExpression(stream);

        if (stream.hasMore() && ASSIGNMENT_OPERATORS.contains(stream.peek().lexeme())) {
            Token opTok = stream.consume();
            JavaExpression right = parseAssignmentExpression(stream);
            return new JavaAssignmentExpression(expr, opTok.lexeme(), right, spanBetween(expr.span().start(), right.span().end()));
        }

        return expr;
    }

    private JavaExpression parseTernaryExpression(JavaTokenStream stream) {
        JavaExpression expr = parseBinaryExpression(stream, 0);

        if (stream.match("?")) {
            JavaExpression thenExpr = parseExpression(stream);
            stream.expect(":", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_TERNARY_COLON_EXPECTED",
                "Expected ':' in ternary expression", "Insert ':'");
            JavaExpression elseExpr = parseTernaryExpression(stream);
            return new JavaTernaryExpression(expr, thenExpr, elseExpr, spanBetween(expr.span().start(), elseExpr.span().end()));
        }

        return expr;
    }

    private JavaExpression parseBinaryExpression(JavaTokenStream stream, int minPrecedence) {
        JavaExpression left = parseUnaryExpression(stream);

        while (stream.hasMore()) {
            Token opTok = stream.peek();
            int prec = getBinaryPrecedence(opTok.lexeme());
            if (prec < minPrecedence) {
                break;
            }

            stream.consume();

            if ("instanceof".equals(opTok.lexeme())) {
                JavaType targetType = parseType(stream);
                Optional<String> patternVar = Optional.empty();
                if (stream.check(TokenType.IDENTIFIER)) {
                    patternVar = Optional.of(stream.consume().lexeme());
                }
                left = new JavaInstanceOfExpression(left, targetType, patternVar, spanBetween(left.span().start(), stream.previousEndLocation()));
                continue;
            }

            // Check consecutive operator error
            if (stream.hasMore() && isInvalidConsecutiveOperator(stream.peek().lexeme())) {
                Token errOp = stream.consume();
                stream.addDiagnostic(Diagnostic.error(
                    CheckCategory.OPERATOR_SYNTAX,
                    errOp.startLocation(),
                    "Unexpected consecutive operator '" + errOp.lexeme() + "' without operand",
                    "ERR_JAVA_OPERATOR_SYNTAX",
                    "Remove invalid operator '" + errOp.lexeme() + "' or provide operand"
                ));
            }

            JavaExpression right = parseBinaryExpression(stream, prec + 1);
            left = new JavaBinaryExpression(left, opTok.lexeme(), right, spanBetween(left.span().start(), right.span().end()));
        }

        return left;
    }

    private int getBinaryPrecedence(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|" -> 3;
            case "^" -> 4;
            case "&" -> 5;
            case "==", "!=" -> 6;
            case "<", "<=", ">", ">=", "instanceof" -> 7;
            case "<<", ">>", ">>>" -> 8;
            case "+", "-" -> 9;
            case "*", "/", "%" -> 10;
            default -> -1;
        };
    }

    private boolean isInvalidConsecutiveOperator(String op) {
        return "*".equals(op) || "/".equals(op) || "%".equals(op) || "==".equals(op)
            || "!=".equals(op) || "<=".equals(op) || ">=".equals(op) || "&&".equals(op) || "||".equals(op);
    }

    private JavaExpression parseUnaryExpression(JavaTokenStream stream) {
        SourceLocation startLoc = stream.currentLocation();

        // 1. Prefix operators: +, -, !, ~, ++, --
        if (stream.check("+") || stream.check("-") || stream.check("!") || stream.check("~")
            || stream.check("++") || stream.check("--")) {
            Token opTok = stream.consume();
            JavaExpression operand = parseUnaryExpression(stream);
            return new JavaUnaryExpression(opTok.lexeme(), operand, true, spanBetween(startLoc, operand.span().end()));
        }

        // 2. Cast expression: (Type) expr
        if (stream.check("(") && isCastLookahead(stream)) {
            stream.consume(); // '('
            JavaType castType = parseType(stream);
            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after cast type", "Insert ')'");
            JavaExpression operand = parseUnaryExpression(stream);
            return new JavaCastExpression(castType, operand, spanBetween(startLoc, operand.span().end()));
        }

        return parsePostfixExpression(stream);
    }

    private JavaExpression parsePostfixExpression(JavaTokenStream stream) {
        JavaExpression expr = parsePrimaryExpression(stream);

        while (stream.hasMore()) {
            if (stream.match("++")) {
                expr = new JavaUnaryExpression("++", expr, false, spanBetween(expr.span().start(), stream.previousEndLocation()));
            } else if (stream.match("--")) {
                expr = new JavaUnaryExpression("--", expr, false, spanBetween(expr.span().start(), stream.previousEndLocation()));
            } else if (stream.match(".")) {
                if (stream.match("class")) {
                    expr = new JavaMemberAccessExpression(expr, "class", spanBetween(expr.span().start(), stream.previousEndLocation()));
                } else if (stream.match("this")) {
                    expr = new JavaThisExpression(Optional.of(JavaType.of(expr.toString(), expr.span())), spanBetween(expr.span().start(), stream.previousEndLocation()));
                } else if (stream.match("super")) {
                    expr = new JavaSuperExpression(Optional.of(JavaType.of(expr.toString(), expr.span())), spanBetween(expr.span().start(), stream.previousEndLocation()));
                } else {
                    List<JavaType> typeArgs = List.of();
                    if (stream.check("<")) {
                        typeArgs = parseTypeArguments(stream);
                    }
                    Token memTok = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                        "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected member identifier after '.'", "Provide member name");
                    if (stream.match("(")) {
                        List<JavaExpression> args = parseArgumentList(stream);
                        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                            "Missing ')' after arguments", "Insert ')'");
                        expr = new JavaMethodInvocationExpression(Optional.of(expr), memTok.lexeme(), typeArgs, args,
                            spanBetween(expr.span().start(), stream.previousEndLocation()));
                    } else {
                        expr = new JavaMemberAccessExpression(expr, memTok.lexeme(), spanBetween(expr.span().start(), memTok.endLocation()));
                    }
                }
            } else if (stream.match("::")) {
                Token methodRefTok = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                    "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected method identifier after '::'", "Provide method reference name");
                expr = new JavaMethodReferenceExpression(expr, methodRefTok.lexeme(), spanBetween(expr.span().start(), methodRefTok.endLocation()));
            } else if (stream.match("[")) {
                JavaExpression index = parseExpression(stream);
                stream.expect("]", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET",
                    "Missing ']' in array access", "Insert ']'");
                expr = new JavaArrayAccessExpression(expr, index, spanBetween(expr.span().start(), stream.previousEndLocation()));
            } else if (stream.match("(")) {
                // Method call on expression
                List<JavaExpression> args = parseArgumentList(stream);
                stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                    "Missing ')' after method arguments", "Insert ')'");
                if (expr instanceof JavaIdentifierExpression id) {
                    expr = new JavaMethodInvocationExpression(Optional.empty(), id.name(), List.of(), args,
                        spanBetween(expr.span().start(), stream.previousEndLocation()));
                } else if (expr instanceof JavaMemberAccessExpression mem) {
                    expr = new JavaMethodInvocationExpression(Optional.of(mem.target()), mem.memberName(), List.of(), args,
                        spanBetween(expr.span().start(), stream.previousEndLocation()));
                } else {
                    expr = new JavaMethodInvocationExpression(Optional.of(expr), "", List.of(), args,
                        spanBetween(expr.span().start(), stream.previousEndLocation()));
                }
            } else {
                break;
            }
        }

        return expr;
    }

    private JavaExpression parsePrimaryExpression(JavaTokenStream stream) {
        SourceLocation startLoc = stream.currentLocation();

        // 1. Single-identifier lambda: x -> expr
        if (stream.check(TokenType.IDENTIFIER) && stream.peek(1).lexeme().equals("->")) {
            Token paramTok = stream.consume();
            stream.consume(); // '->'
            JavaParameter param = new JavaParameter(JavaType.of("var", paramTok.span()), paramTok.lexeme(),
                List.of(), List.of(), false, paramTok.span());
            JavaAstNode body = stream.check("{") ? parseBlock(stream) : parseExpression(stream);
            return new JavaLambdaExpression(List.of(param), body, spanBetween(startLoc, body.span().end()));
        }

        // 2. Literals
        if (stream.check(TokenType.LITERAL_NUMBER)) {
            Token t = stream.consume();
            return new JavaLiteralExpression(t.lexeme(), "NUMBER", t.span());
        }
        if (stream.check(TokenType.LITERAL_STRING)) {
            Token t = stream.consume();
            return new JavaLiteralExpression(t.lexeme(), "STRING", t.span());
        }
        if (stream.check(TokenType.LITERAL_CHAR)) {
            Token t = stream.consume();
            return new JavaLiteralExpression(t.lexeme(), "CHAR", t.span());
        }
        if (stream.check("true") || stream.check("false")) {
            Token t = stream.consume();
            return new JavaLiteralExpression(t.lexeme(), "BOOLEAN", t.span());
        }
        if (stream.check("null")) {
            Token t = stream.consume();
            return new JavaLiteralExpression(t.lexeme(), "NULL", t.span());
        }

        // 3. Identifier
        if (stream.check(TokenType.IDENTIFIER)) {
            Token t = stream.consume();
            return new JavaIdentifierExpression(t.lexeme(), t.span());
        }

        // 4. this & super
        if (stream.match("this")) {
            return JavaThisExpression.of(spanBetween(startLoc, stream.previousEndLocation()));
        }
        if (stream.match("super")) {
            return JavaSuperExpression.of(spanBetween(startLoc, stream.previousEndLocation()));
        }

        // 5. Parenthesized expression OR multi-param lambda: (a, b) -> body
        if (stream.match("(")) {
            if (isLambdaAfterParenLookahead(stream)) {
                List<JavaParameter> params = parseParameterList(stream);
                stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                    "Missing ')' after lambda parameter list", "Insert ')'");
                stream.expect("->", CheckCategory.OPERATOR_SYNTAX, "ERR_JAVA_LAMBDA_ARROW_EXPECTED",
                    "Expected '->' after lambda parameters", "Insert '->'");
                JavaAstNode body = stream.check("{") ? parseBlock(stream) : parseExpression(stream);
                return new JavaLambdaExpression(params, body, spanBetween(startLoc, body.span().end()));
            }

            JavaExpression inner = parseExpression(stream);
            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after parenthesized expression", "Insert ')'");
            return new JavaParenthesizedExpression(inner, spanBetween(startLoc, stream.previousEndLocation()));
        }

        // 6. Switch expression
        if (stream.match("switch")) {
            if (!stream.match("(")) {
                stream.addDiagnostic(Diagnostic.error(
                    CheckCategory.CONTROL_HEADER,
                    stream.currentLocation(),
                    "Expected '(' after switch in expression",
                    "ERR_JAVA_INVALID_CONTROL_HEADER",
                    "Enclose switch selector in parentheses '(...)'"
                ));
            }
            JavaExpression selector = parseExpression(stream);
            stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                "Missing ')' after switch selector", "Insert ')'");
            List<JavaSwitchEntry> entries = parseSwitchEntries(stream);
            return new JavaSwitchExpression(selector, entries, spanBetween(startLoc, stream.previousEndLocation()));
        }

        // 7. new expressions: object or array instantiation
        if (stream.match("new")) {
            JavaType type = parseBaseType(stream);
            if (stream.match("[")) {
                // Array creation
                List<JavaExpression> dims = new ArrayList<>();
                int emptyDims = 0;
                if (!stream.check("]")) {
                    dims.add(parseExpression(stream));
                    stream.expect("]", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET", "Missing ']'", "Insert ']'");
                    while (stream.match("[")) {
                        if (!stream.check("]")) {
                            dims.add(parseExpression(stream));
                            stream.expect("]", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET", "Missing ']'", "Insert ']'");
                        } else {
                            stream.consume();
                            emptyDims++;
                        }
                    }
                } else {
                    stream.consume(); // ']'
                    emptyDims++;
                    while (stream.match("[")) {
                        stream.expect("]", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET", "Missing ']'", "Insert ']'");
                        emptyDims++;
                    }
                }

                Optional<List<JavaExpression>> init = Optional.empty();
                if (stream.match("{")) {
                    List<JavaExpression> inits = new ArrayList<>();
                    while (stream.hasMore() && !stream.check("}")) {
                        inits.add(parseExpression(stream));
                        if (!stream.match(",")) break;
                    }
                    stream.expect("}", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACE",
                        "Missing '}' after array initializer", "Insert '}'");
                    init = Optional.of(inits);
                }

                return new JavaArrayCreationExpression(type, dims, emptyDims, init, spanBetween(startLoc, stream.previousEndLocation()));
            } else if (stream.match("(")) {
                // Object creation
                List<JavaExpression> args = parseArgumentList(stream);
                stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
                    "Missing ')' after constructor arguments", "Insert ')'");

                Optional<List<JavaAstNode>> anonBody = Optional.empty();
                if (stream.check("{")) {
                    anonBody = Optional.of(parseClassBody(stream, type.name()));
                }

                return new JavaObjectCreationExpression(Optional.empty(), type, args, anonBody, spanBetween(startLoc, stream.previousEndLocation()));
            }
        }

        // Fallback for unexpected token
        Token unk = stream.consume();
        stream.addDiagnostic(Diagnostic.error(
            CheckCategory.OPERATOR_SYNTAX,
            unk.startLocation(),
            "Unexpected token '" + unk.lexeme() + "' in expression",
            "ERR_JAVA_UNEXPECTED_TOKEN",
            "Remove or replace unexpected token"
        ));
        return new JavaIdentifierExpression(unk.lexeme(), unk.span());
    }

    private List<JavaExpression> parseArgumentList(JavaTokenStream stream) {
        List<JavaExpression> args = new ArrayList<>();
        if (!stream.check(")")) {
            args.add(parseExpression(stream));
            while (stream.match(",")) {
                args.add(parseExpression(stream));
            }
        }
        return args;
    }

    // =========================================================================
    // Types, Parameters, Modifiers, Annotations helpers
    // =========================================================================

    private JavaType parseBaseType(JavaTokenStream stream) {
        SourceLocation startLoc = stream.currentLocation();
        String name;

        if (stream.check(TokenType.KEYWORD) && PRIMITIVE_TYPES.contains(stream.peek().lexeme())) {
            name = stream.consume().lexeme();
        } else {
            name = parseQualifiedName(stream);
        }

        List<JavaType> typeArgs = List.of();
        if (stream.check("<")) {
            typeArgs = parseTypeArguments(stream);
        }

        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaType(name, typeArgs, 0, span);
    }

    private JavaType parseType(JavaTokenStream stream) {
        SourceLocation startLoc = stream.currentLocation();
        String name;

        if (stream.check(TokenType.KEYWORD) && PRIMITIVE_TYPES.contains(stream.peek().lexeme())) {
            name = stream.consume().lexeme();
        } else {
            name = parseQualifiedName(stream);
        }

        List<JavaType> typeArgs = List.of();
        if (stream.check("<")) {
            typeArgs = parseTypeArguments(stream);
        }

        int dims = 0;
        while (stream.match("[")) {
            stream.expect("]", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET",
                "Missing ']' in type dimension", "Insert ']'");
            dims++;
        }

        SourceSpan span = spanBetween(startLoc, stream.previousEndLocation());
        return new JavaType(name, typeArgs, dims, span);
    }

    private List<JavaType> parseTypeArguments(JavaTokenStream stream) {
        stream.expect("<", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_GENERIC_OPEN_EXPECTED", "Expected '<'", "Insert '<'");
        List<JavaType> typeArgs = new ArrayList<>();
        while (stream.hasMore() && !stream.check(">")) {
            if (stream.match("?")) {
                SourceLocation qLoc = stream.previousEndLocation();
                if (stream.match("extends") || stream.match("super")) {
                    JavaType bound = parseType(stream);
                    typeArgs.add(new JavaType("?", List.of(bound), 0, spanBetween(qLoc, bound.span().end())));
                } else {
                    typeArgs.add(JavaType.of("?", SourceSpan.point(qLoc)));
                }
            } else {
                typeArgs.add(parseType(stream));
            }
            if (!stream.match(",")) {
                break;
            }
        }
        stream.expect(">", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET",
            "Missing '>' to close type arguments", "Insert '>'");
        return typeArgs;
    }

    private List<JavaTypeParameter> parseTypeParameters(JavaTokenStream stream) {
        if (!stream.match("<")) {
            return List.of();
        }
        List<JavaTypeParameter> params = new ArrayList<>();
        while (stream.hasMore() && !stream.check(">")) {
            SourceLocation pStart = stream.currentLocation();
            Token nameTok = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected type parameter identifier", "Provide type parameter name");
            List<JavaType> bounds = List.of();
            if (stream.match("extends")) {
                bounds = parseTypeListAmpersand(stream);
            }
            params.add(new JavaTypeParameter(nameTok.lexeme(), bounds, spanBetween(pStart, stream.previousEndLocation())));
            if (!stream.match(",")) {
                break;
            }
        }
        stream.expect(">", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_BRACKET",
            "Missing '>' after type parameters", "Insert '>'");
        return params;
    }

    private List<JavaParameter> parseParameterList(JavaTokenStream stream) {
        List<JavaParameter> params = new ArrayList<>();
        while (stream.hasMore() && !stream.check(")")) {
            SourceLocation pStart = stream.currentLocation();
            List<JavaAnnotation> annotations = parseAnnotations(stream);
            List<String> modifiers = parseModifiers(stream);
            JavaType type = parseType(stream);
            boolean isVarargs = stream.match("...");

            Token nameTok = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
                "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected parameter name identifier", "Provide parameter name");
            params.add(new JavaParameter(type, nameTok.lexeme(), annotations, modifiers, isVarargs, spanBetween(pStart, nameTok.endLocation())));

            if (!stream.match(",")) {
                break;
            }
        }
        return params;
    }

    private List<JavaParameter> parseRecordComponents(JavaTokenStream stream) {
        stream.expect("(", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Expected '(' for record component list", "Insert '('");
        List<JavaParameter> comps = parseParameterList(stream);
        stream.expect(")", CheckCategory.DELIMITER_MATCH, "ERR_JAVA_UNCLOSED_PARENTHESIS",
            "Missing ')' after record components", "Insert ')'");
        return comps;
    }

    private List<JavaType> parseTypeList(JavaTokenStream stream) {
        List<JavaType> list = new ArrayList<>();
        list.add(parseType(stream));
        while (stream.match(",")) {
            list.add(parseType(stream));
        }
        return list;
    }

    private List<JavaType> parseTypeListAmpersand(JavaTokenStream stream) {
        List<JavaType> list = new ArrayList<>();
        list.add(parseType(stream));
        while (stream.match("&")) {
            list.add(parseType(stream));
        }
        return list;
    }

    private String parseQualifiedName(JavaTokenStream stream) {
        StringBuilder sb = new StringBuilder();
        Token first = stream.expect(TokenType.IDENTIFIER, CheckCategory.IDENTIFIER_NAMING,
            "ERR_JAVA_EXPECTED_IDENTIFIER", "Expected identifier in qualified name", "Provide identifier");
        sb.append(first.lexeme());
        while (stream.check(".") && stream.peek(1).tokenType() == TokenType.IDENTIFIER) {
            stream.consume(); // '.'
            Token next = stream.consume();
            sb.append(".").append(next.lexeme());
        }
        return sb.toString();
    }

    private List<JavaAnnotation> parseAnnotations(JavaTokenStream stream) {
        List<JavaAnnotation> annotations = new ArrayList<>();
        while (stream.match("@")) {
            SourceLocation aStart = stream.previousEndLocation();
            String name = parseQualifiedName(stream);
            Optional<String> args = Optional.empty();
            if (stream.match("(")) {
                int depth = 1;
                StringBuilder sb = new StringBuilder();
                while (stream.hasMore()) {
                    if (stream.check("(")) depth++;
                    else if (stream.check(")")) {
                        depth--;
                        if (depth == 0) {
                            stream.consume();
                            break;
                        }
                    }
                    sb.append(stream.consume().lexeme()).append(" ");
                }
                args = Optional.of(sb.toString().trim());
            }
            annotations.add(new JavaAnnotation(name, args, spanBetween(aStart, stream.previousEndLocation())));
        }
        return annotations;
    }

    private List<String> parseModifiers(JavaTokenStream stream) {
        List<String> mods = new ArrayList<>();
        while (stream.hasMore() && MODIFIERS.contains(stream.peek().lexeme())) {
            mods.add(stream.consume().lexeme());
        }
        return mods;
    }

    // =========================================================================
    // Lookaheads & Synchronization
    // =========================================================================

    private boolean isTypeDeclarationLookahead(JavaTokenStream stream) {
        int i = 0;
        while (stream.peek(i).lexeme().equals("@")) {
            i++;
            if (stream.peek(i).tokenType() == TokenType.IDENTIFIER) {
                i++;
                while (stream.peek(i).lexeme().equals(".") && stream.peek(i + 1).tokenType() == TokenType.IDENTIFIER) {
                    i += 2;
                }
                if (stream.peek(i).lexeme().equals("(")) {
                    i++;
                    int depth = 1;
                    while (depth > 0 && !stream.peek(i).lexeme().equals("<EOF>")) {
                        if (stream.peek(i).lexeme().equals("(")) depth++;
                        else if (stream.peek(i).lexeme().equals(")")) depth--;
                        i++;
                    }
                }
            }
        }
        while (MODIFIERS.contains(stream.peek(i).lexeme())) {
            i++;
        }
        String lex = stream.peek(i).lexeme();
        return "class".equals(lex) || "interface".equals(lex) || "record".equals(lex) || "enum".equals(lex);
    }

    private boolean isVariableDeclarationLookahead(JavaTokenStream stream) {
        int i = 0;
        while (stream.peek(i).lexeme().equals("@")) {
            i++;
            if (stream.peek(i).tokenType() == TokenType.IDENTIFIER) {
                i++;
                while (stream.peek(i).lexeme().equals(".") && stream.peek(i + 1).tokenType() == TokenType.IDENTIFIER) {
                    i += 2;
                }
                if (stream.peek(i).lexeme().equals("(")) {
                    i++;
                    int depth = 1;
                    while (depth > 0 && !stream.peek(i).lexeme().equals("<EOF>")) {
                        if (stream.peek(i).lexeme().equals("(")) depth++;
                        else if (stream.peek(i).lexeme().equals(")")) depth--;
                        i++;
                    }
                }
            }
        }
        while (MODIFIERS.contains(stream.peek(i).lexeme())) {
            i++;
        }
        Token first = stream.peek(i);
        if (first.tokenType() == TokenType.KEYWORD && PRIMITIVE_TYPES.contains(first.lexeme())) {
            return true;
        }
        if (first.tokenType() == TokenType.IDENTIFIER) {
            i++;
            if (stream.peek(i).lexeme().equals("<")) {
                int depth = 1;
                i++;
                while (depth > 0 && !stream.peek(i).lexeme().equals("<EOF>")) {
                    if (stream.peek(i).lexeme().equals("<")) depth++;
                    else if (stream.peek(i).lexeme().equals(">")) depth--;
                    i++;
                }
            }
            while (stream.peek(i).lexeme().equals("[") && stream.peek(i + 1).lexeme().equals("]")) {
                i += 2;
            }
            return stream.peek(i).tokenType() == TokenType.IDENTIFIER;
        }
        return false;
    }

    private boolean isCastLookahead(JavaTokenStream stream) {
        int pos = stream.position();
        if (!stream.match("(")) return false;

        Token first = stream.peek();
        if (first.tokenType() == TokenType.KEYWORD && PRIMITIVE_TYPES.contains(first.lexeme())) {
            stream.consume();
            while (stream.match("[") && stream.match("]")) {}
            boolean isCast = stream.match(")");
            stream.seek(pos);
            return isCast;
        }

        if (first.tokenType() == TokenType.IDENTIFIER) {
            stream.consume();
            while (stream.match(".") && stream.match(TokenType.IDENTIFIER)) {}
            if (stream.match("<")) {
                int depth = 1;
                while (depth > 0 && stream.hasMore()) {
                    if (stream.check("<")) depth++;
                    else if (stream.check(">")) depth--;
                    stream.consume();
                }
            }
            while (stream.match("[") && stream.match("]")) {}
            if (stream.match(")")) {
                Token follower = stream.peek();
                boolean isFollower = follower.tokenType() == TokenType.IDENTIFIER
                    || follower.tokenType().isLiteral()
                    || follower.lexeme().equals("(")
                    || follower.lexeme().equals("!")
                    || follower.lexeme().equals("~")
                    || follower.lexeme().equals("new")
                    || follower.lexeme().equals("this");
                stream.seek(pos);
                return isFollower;
            }
        }

        stream.seek(pos);
        return false;
    }

    private boolean isLambdaAfterParenLookahead(JavaTokenStream stream) {
        int i = 0;
        int depth = 1;
        while (!stream.peek(i).lexeme().equals("<EOF>")) {
            if (stream.peek(i).lexeme().equals("(")) {
                depth++;
            } else if (stream.peek(i).lexeme().equals(")")) {
                depth--;
                if (depth == 0) {
                    return stream.peek(i + 1).lexeme().equals("->");
                }
            }
            i++;
        }
        return false;
    }

    private void synchronizeStatement(JavaTokenStream stream) {
        while (stream.hasMore()) {
            if (stream.match(";")) {
                return;
            }
            if (stream.check("}")) {
                return;
            }
            String lex = stream.peek().lexeme();
            if (MODIFIERS.contains(lex) || PRIMITIVE_TYPES.contains(lex)
                || "if".equals(lex) || "for".equals(lex) || "while".equals(lex) || "switch".equals(lex)
                || "return".equals(lex) || "throw".equals(lex) || "class".equals(lex) || "interface".equals(lex)) {
                return;
            }
            stream.consume();
        }
    }

    private void synchronizeMember(JavaTokenStream stream) {
        while (stream.hasMore() && !stream.check("}")) {
            if (stream.match(";")) {
                return;
            }
            String lex = stream.peek().lexeme();
            if (MODIFIERS.contains(lex) || "class".equals(lex) || "interface".equals(lex)
                || "record".equals(lex) || "enum".equals(lex)) {
                return;
            }
            stream.consume();
        }
    }

    private SourceSpan spanBetween(SourceLocation start, SourceLocation end) {
        if (end.compareTo(start) < 0) {
            return SourceSpan.point(start);
        }
        return SourceSpan.of(start, end);
    }
}
