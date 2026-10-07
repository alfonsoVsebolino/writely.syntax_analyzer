package com.writely.syntax_analyzer.adapter.python;

import com.writely.syntax_analyzer.adapter.ParseResult;
import com.writely.syntax_analyzer.adapter.python.ast.PyExpression;
import static com.writely.syntax_analyzer.adapter.python.ast.PyExpressions.*;
import com.writely.syntax_analyzer.adapter.python.ast.PyModule;
import com.writely.syntax_analyzer.adapter.python.ast.PyStatement;
import static com.writely.syntax_analyzer.adapter.python.ast.PyStatements.*;
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
import java.util.Optional;
import java.util.Set;

/**
 * Recursive descent parser for Python 3 syntax with indentation tracking,
 * header validation, AST construction, and syntax error diagnostics.
 */
public class PythonParser {

    private static final Set<String> AUGMENTED_ASSIGN_OPS = Set.of(
        "+=", "-=", "*=", "/=", "//=", "%=", "**=", "&=", "|=", "^=", "<<=", ">>=", "@="
    );

    private static final Set<String> COMPARISON_OPS = Set.of(
        "==", "!=", "<", "<=", ">", ">="
    );

    private SourcePayload payload;
    private List<Token> allTokens;
    private List<Token> codeTokens;
    private int current;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public ParseResult parse(SourcePayload payload, List<Token> tokens) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");

        this.payload = payload;
        this.allTokens = tokens;
        this.codeTokens = filterCodeTokens(tokens);
        this.current = 0;
        this.diagnostics.clear();

        if (codeTokens.isEmpty()) {
            SourceSpan emptySpan = SourceSpan.point(SourceLocation.start());
            PyModule emptyModule = new PyModule(emptySpan, List.of(), payload.sourceName());
            return ParseResult.of(emptyModule.toSyntaxNode(), List.of(), emptyModule, tokens);
        }

        PyModule module = parseModule();
        diagnostics.sort(Comparator.comparing(Diagnostic::location));
        SyntaxNode syntaxTree = module.toSyntaxNode();

        return ParseResult.of(syntaxTree, Collections.unmodifiableList(diagnostics), module, tokens);
    }

    private static List<Token> filterCodeTokens(List<Token> tokens) {
        List<Token> filtered = new ArrayList<>();
        for (Token t : tokens) {
            if (t.tokenType() != TokenType.COMMENT && t.tokenType() != TokenType.WHITESPACE) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    // ------------------------------------------------------------------------
    // Module and Statements
    // ------------------------------------------------------------------------

    private PyModule parseModule() {
        SourceLocation startLoc = peek().startLocation();
        List<PyStatement> statements = new ArrayList<>();

        while (!isAtEnd()) {
            while (match(TokenType.NEWLINE)) {
                // skip blank lines at module level
            }
            if (isAtEnd()) {
                break;
            }

            // Flag unexpected indentation at top level
            if (check(TokenType.INDENT)) {
                Token indentTok = consume();
                addDiagnostic(
                    CheckCategory.STATEMENT_TERMINATOR,
                    indentTok.startLocation(),
                    "Unexpected indentation",
                    PythonDiagnosticCodes.ERR_UNEXPECTED_INDENT,
                    "Remove unexpected indentation"
                );
                continue;
            }

            // Consume any stray dedents at module level
            if (match(TokenType.DEDENT)) {
                continue;
            }

            try {
                PyStatement stmt = parseStatement();
                if (stmt != null) {
                    statements.add(stmt);
                }
            } catch (Exception e) {
                synchronizeStatement();
            }
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : startLoc;
        SourceSpan moduleSpan = SourceSpan.of(startLoc, endLoc);
        return new PyModule(moduleSpan, statements, payload.sourceName());
    }

    private PyStatement parseStatement() {
        while (match(TokenType.NEWLINE)) {
            // skip blank lines
        }
        if (isAtEnd()) {
            return null;
        }

        // Decorators preceding def or class
        List<PyDecorator> decorators = new ArrayList<>();
        while (check("@")) {
            decorators.add(parseDecorator());
            while (match(TokenType.NEWLINE)) {}
        }

        if (check("def")) {
            return parseFunctionDef(decorators);
        }
        if (check("class")) {
            return parseClassDef(decorators);
        }

        if (!decorators.isEmpty()) {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                decorators.get(0).span().start(),
                "Decorators can only precede function or class definitions",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Place decorators before 'def' or 'class'"
            );
        }

        if (check("if")) {
            return parseIfStatement();
        }
        if (check("for")) {
            return parseForStatement();
        }
        if (check("while")) {
            return parseWhileStatement();
        }
        if (check("try")) {
            return parseTryStatement();
        }
        if (check("with")) {
            return parseWithStatement();
        }
        if (check("match")) {
            return parseMatchStatement();
        }

        // Simple statements
        PyStatement simple = parseSimpleStatement();
        consumeStatementTerminator();
        return simple;
    }

    private PyDecorator parseDecorator() {
        Token atToken = consume(); // @
        PyExpression expr = parsePrimary();
        while (check(".") || check("(")) {
            if (match(".")) {
                if (check(TokenType.IDENTIFIER)) {
                    Token attr = consume();
                    SourceSpan span = SourceSpan.of(expr.span().start(), attr.endLocation());
                    expr = new PyAttribute(span, expr, attr.lexeme());
                } else {
                    addDiagnostic(CheckCategory.CONTROL_HEADER, currentLocation(), "Expected attribute name after '.'", PythonDiagnosticCodes.ERR_MALFORMED_HEADER, "Add attribute name");
                    break;
                }
            } else if (check("(")) {
                expr = finishCall(expr);
            }
        }
        SourceSpan span = SourceSpan.of(atToken.startLocation(), expr.span().end());
        return new PyDecorator(span, expr);
    }

    private PyFunctionDef parseFunctionDef(List<PyDecorator> decorators) {
        Token defToken = consume(); // def
        String name = "";
        SourceLocation nameLoc = currentLocation();

        if (check(TokenType.IDENTIFIER)) {
            name = consume().lexeme();
        } else {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                nameLoc,
                "Expected function name after 'def'",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Provide a valid function identifier"
            );
            name = "<anonymous>";
        }

        List<PyParameter> parameters = new ArrayList<>();
        if (match("(")) {
            parameters = parseParameters();
            if (!match(")")) {
                addDiagnostic(
                    CheckCategory.CONTROL_HEADER,
                    currentLocation(),
                    "Expected ')' closing parameter list",
                    PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                    "Add closing ')'"
                );
            }
        } else {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                currentLocation(),
                "Expected parameter list '(...)' in function definition",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Add parameter list '()'"
            );
        }

        Optional<PyExpression> returnType = Optional.empty();
        if (match("->")) {
            returnType = Optional.ofNullable(parseExpression());
        }

        validateColon("def");
        List<PyStatement> body = parseSuite("def");

        SourceLocation startLoc = decorators.isEmpty() ? defToken.startLocation() : decorators.get(0).span().start();
        SourceLocation endLoc = previous() != null ? previous().endLocation() : defToken.endLocation();
        SourceSpan span = SourceSpan.of(startLoc, endLoc);
        return new PyFunctionDef(span, name, decorators, parameters, returnType, body);
    }

    private List<PyParameter> parseParameters() {
        List<PyParameter> params = new ArrayList<>();
        while (!check(")") && !isAtEnd()) {
            boolean isVararg = match("*");
            boolean isKwarg = !isVararg && match("**");

            if (!check(TokenType.IDENTIFIER)) {
                if (isVararg) {
                    if (match(",")) continue;
                    else break;
                }
                break;
            }

            Token paramToken = consume();
            String pName = paramToken.lexeme();
            Optional<PyExpression> annotation = Optional.empty();
            if (match(":")) {
                annotation = Optional.ofNullable(parseExpression());
            }

            Optional<PyExpression> defaultVal = Optional.empty();
            if (match("=")) {
                defaultVal = Optional.ofNullable(parseExpression());
            }

            SourceLocation endL = defaultVal.map(d -> d.span().end())
                .orElse(annotation.map(a -> a.span().end()).orElse(paramToken.endLocation()));
            SourceSpan pSpan = SourceSpan.of(paramToken.startLocation(), endL);
            params.add(new PyParameter(pSpan, pName, annotation, defaultVal, isVararg, isKwarg));

            if (!match(",")) {
                break;
            }
        }
        return params;
    }

    private PyClassDef parseClassDef(List<PyDecorator> decorators) {
        Token classToken = consume(); // class
        String name = "";
        SourceLocation nameLoc = currentLocation();

        if (check(TokenType.IDENTIFIER)) {
            name = consume().lexeme();
        } else {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                nameLoc,
                "Expected class name after 'class'",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Provide a valid class identifier"
            );
            name = "<anonymous>";
        }

        List<PyExpression> bases = new ArrayList<>();
        if (match("(")) {
            while (!check(")") && !isAtEnd()) {
                bases.add(parseExpression());
                if (!match(",")) {
                    break;
                }
            }
            if (!match(")")) {
                addDiagnostic(
                    CheckCategory.CONTROL_HEADER,
                    currentLocation(),
                    "Expected ')' closing class base list",
                    PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                    "Add closing ')'"
                );
            }
        }

        validateColon("class");
        List<PyStatement> body = parseSuite("class");

        SourceLocation startLoc = decorators.isEmpty() ? classToken.startLocation() : decorators.get(0).span().start();
        SourceLocation endLoc = previous() != null ? previous().endLocation() : classToken.endLocation();
        SourceSpan span = SourceSpan.of(startLoc, endLoc);
        return new PyClassDef(span, name, decorators, bases, body);
    }

    private PyIf parseIfStatement() {
        Token ifToken = consume(); // if
        PyExpression condition = parseHeaderCondition("if", ifToken);
        validateColon("if");
        List<PyStatement> thenBody = parseSuite("if");

        List<PyElif> elifClauses = new ArrayList<>();
        while (true) {
            while (match(TokenType.NEWLINE)) {}
            if (check("elif")) {
                Token elifToken = consume();
                PyExpression elifCond = parseHeaderCondition("elif", elifToken);
                validateColon("elif");
                List<PyStatement> elifBody = parseSuite("elif");
                SourceSpan elifSpan = SourceSpan.of(elifToken.startLocation(), previous().endLocation());
                elifClauses.add(new PyElif(elifSpan, elifCond, elifBody));
            } else {
                break;
            }
        }

        List<PyStatement> elseBody = new ArrayList<>();
        while (match(TokenType.NEWLINE)) {}
        if (match("else")) {
            validateColon("else");
            elseBody = parseSuite("else");
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : ifToken.endLocation();
        SourceSpan span = SourceSpan.of(ifToken.startLocation(), endLoc);
        return new PyIf(span, condition, thenBody, elifClauses, elseBody);
    }

    private PyFor parseForStatement() {
        Token forToken = consume(); // for
        PyExpression target = null;

        if (check("in") || check(":") || check(TokenType.NEWLINE)) {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                forToken.endLocation(),
                "Expected target variable after 'for'",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Specify loop target variable"
            );
            target = new PyIdentifier(SourceSpan.point(forToken.endLocation()), "<error>");
        } else {
            target = parseForTarget();
        }

        if (!match("in")) {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                currentLocation(),
                "Expected 'in' in 'for' loop header",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Add 'in' keyword"
            );
        }

        PyExpression iterable = null;
        if (check(":") || check(TokenType.NEWLINE) || isAtEnd()) {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                currentLocation(),
                "Expected iterable expression after 'in'",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Specify iterable sequence"
            );
            iterable = new PyIdentifier(SourceSpan.point(currentLocation()), "<error>");
        } else {
            iterable = parseExpression();
        }

        validateColon("for");
        List<PyStatement> body = parseSuite("for");

        List<PyStatement> elseBody = new ArrayList<>();
        while (match(TokenType.NEWLINE)) {}
        if (match("else")) {
            validateColon("else");
            elseBody = parseSuite("else");
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : forToken.endLocation();
        SourceSpan span = SourceSpan.of(forToken.startLocation(), endLoc);
        return new PyFor(span, target, iterable, body, elseBody);
    }

    private PyExpression parseForTarget() {
        PyExpression first = parsePrimary();
        if (check(",")) {
            List<PyExpression> targets = new ArrayList<>();
            targets.add(first);
            while (match(",")) {
                if (check("in") || check(":") || check(TokenType.NEWLINE) || isAtEnd()) {
                    break;
                }
                targets.add(parsePrimary());
            }
            SourceSpan span = SourceSpan.of(first.span().start(), targets.get(targets.size() - 1).span().end());
            return new PyTuple(span, targets);
        }
        return first;
    }

    private PyWhile parseWhileStatement() {
        Token whileToken = consume(); // while
        PyExpression condition = parseHeaderCondition("while", whileToken);
        validateColon("while");
        List<PyStatement> body = parseSuite("while");

        List<PyStatement> elseBody = new ArrayList<>();
        while (match(TokenType.NEWLINE)) {}
        if (match("else")) {
            validateColon("else");
            elseBody = parseSuite("else");
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : whileToken.endLocation();
        SourceSpan span = SourceSpan.of(whileToken.startLocation(), endLoc);
        return new PyWhile(span, condition, body, elseBody);
    }

    private PyTry parseTryStatement() {
        Token tryToken = consume(); // try
        validateColon("try");
        List<PyStatement> body = parseSuite("try");

        List<PyExcept> excepts = new ArrayList<>();
        while (true) {
            while (match(TokenType.NEWLINE)) {}
            if (check("except")) {
                Token exToken = consume();
                Optional<PyExpression> type = Optional.empty();
                Optional<String> alias = Optional.empty();

                if (!check(":") && !check(TokenType.NEWLINE)) {
                    type = Optional.ofNullable(parseExpression());
                    if (match("as") && check(TokenType.IDENTIFIER)) {
                        alias = Optional.of(consume().lexeme());
                    }
                }

                validateColon("except");
                List<PyStatement> exBody = parseSuite("except");
                SourceSpan exSpan = SourceSpan.of(exToken.startLocation(), previous().endLocation());
                excepts.add(new PyExcept(exSpan, type, alias, exBody));
            } else {
                break;
            }
        }

        List<PyStatement> elseBody = new ArrayList<>();
        while (match(TokenType.NEWLINE)) {}
        if (match("else")) {
            validateColon("else");
            elseBody = parseSuite("else");
        }

        List<PyStatement> finallyBody = new ArrayList<>();
        while (match(TokenType.NEWLINE)) {}
        if (match("finally")) {
            validateColon("finally");
            finallyBody = parseSuite("finally");
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : tryToken.endLocation();
        SourceSpan span = SourceSpan.of(tryToken.startLocation(), endLoc);
        return new PyTry(span, body, excepts, elseBody, finallyBody);
    }

    private PyWith parseWithStatement() {
        Token withToken = consume(); // with
        List<PyWithItem> items = new ArrayList<>();

        while (!check(":") && !check(TokenType.NEWLINE) && !isAtEnd()) {
            PyExpression ctx = parseExpression();
            Optional<PyExpression> alias = Optional.empty();
            if (match("as")) {
                alias = Optional.ofNullable(parseExpression());
            }
            SourceLocation iEnd = alias.map(a -> a.span().end()).orElse(ctx.span().end());
            items.add(new PyWithItem(SourceSpan.of(ctx.span().start(), iEnd), ctx, alias));
            if (!match(",")) {
                break;
            }
        }

        validateColon("with");
        List<PyStatement> body = parseSuite("with");
        SourceLocation endLoc = previous() != null ? previous().endLocation() : withToken.endLocation();
        SourceSpan span = SourceSpan.of(withToken.startLocation(), endLoc);
        return new PyWith(span, items, body);
    }

    private PyMatch parseMatchStatement() {
        Token matchToken = consume(); // match
        PyExpression subject = parseExpression();
        validateColon("match");

        List<PyCase> cases = new ArrayList<>();
        if (match(TokenType.NEWLINE)) {
            while (match(TokenType.NEWLINE)) {}
            if (match(TokenType.INDENT)) {
                while (!check(TokenType.DEDENT) && !isAtEnd()) {
                    while (match(TokenType.NEWLINE)) {}
                    if (check(TokenType.DEDENT) || isAtEnd()) break;
                    if (match("case")) {
                        Token caseToken = previous();
                        PyExpression pattern = parseWalrusOrLogicalOr();
                        Optional<PyExpression> guard = Optional.empty();
                        if (match("if")) {
                            guard = Optional.ofNullable(parseWalrusOrLogicalOr());
                        }
                        validateColon("case");
                        List<PyStatement> cBody = parseSuite("case");
                        SourceSpan cSpan = SourceSpan.of(caseToken.startLocation(), previous().endLocation());
                        cases.add(new PyCase(cSpan, pattern, guard, cBody));
                    } else {
                        consume();
                    }
                }
                match(TokenType.DEDENT);
            }
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : matchToken.endLocation();
        SourceSpan span = SourceSpan.of(matchToken.startLocation(), endLoc);
        return new PyMatch(span, subject, cases);
    }

    private PyExpression parseHeaderCondition(String headerName, Token headerToken) {
        if (check(":") || check(TokenType.NEWLINE) || isAtEnd()) {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                headerToken.endLocation(),
                "Expected condition expression after '" + headerName + "'",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Add conditional expression"
            );
            return new PyIdentifier(SourceSpan.point(headerToken.endLocation()), "<error>");
        }
        return parseExpression();
    }

    private void validateColon(String headerName) {
        if (!match(":")) {
            SourceLocation errLoc = previous() != null ? previous().endLocation() : currentLocation();
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                errLoc,
                "Missing ':' at end of '" + headerName + "' header",
                PythonDiagnosticCodes.ERR_MISSING_COLON,
                "Add ':' at end of header"
            );
        }
    }

    private List<PyStatement> parseSuite(String headerName) {
        if (match(TokenType.NEWLINE)) {
            while (match(TokenType.NEWLINE)) {
                // skip blank lines before indented block
            }
            if (match(TokenType.INDENT)) {
                List<PyStatement> body = new ArrayList<>();
                while (!isAtEnd() && !check(TokenType.DEDENT)) {
                    while (match(TokenType.NEWLINE)) {}
                    if (check(TokenType.DEDENT) || isAtEnd()) {
                        break;
                    }
                    PyStatement stmt = parseStatement();
                    if (stmt != null) {
                        body.add(stmt);
                    }
                }
                if (!match(TokenType.DEDENT) && !isAtEnd()) {
                    addDiagnostic(
                        CheckCategory.STATEMENT_TERMINATOR,
                        currentLocation(),
                        "Expected dedent at end of '" + headerName + "' block",
                        PythonDiagnosticCodes.ERR_INVALID_SYNTAX,
                        "Align block indentation"
                    );
                }
                return body;
            } else {
                addDiagnostic(
                    CheckCategory.STATEMENT_TERMINATOR,
                    currentLocation(),
                    "Expected an indented block after '" + headerName + "' statement",
                    PythonDiagnosticCodes.ERR_EXPECTED_INDENTED_BLOCK,
                    "Indent the block following the statement"
                );
                return List.of();
            }
        } else {
            // Same line simple statement e.g. "if x: return 1"
            PyStatement simple = parseSimpleStatement();
            consumeStatementTerminator();
            return simple != null ? List.of(simple) : List.of();
        }
    }

    // ------------------------------------------------------------------------
    // Simple Statements
    // ------------------------------------------------------------------------

    private PyStatement parseSimpleStatement() {
        if (check("return")) {
            Token retToken = consume();
            Optional<PyExpression> val = Optional.empty();
            if (!check(";") && !check(TokenType.NEWLINE) && !isAtEnd()) {
                val = Optional.of(parseAssignmentValue());
            }
            SourceLocation endLoc = val.map(v -> v.span().end()).orElse(retToken.endLocation());
            return new PyReturn(SourceSpan.of(retToken.startLocation(), endLoc), val);
        }

        if (check("raise")) {
            Token raiseToken = consume();
            Optional<PyExpression> exc = Optional.empty();
            Optional<PyExpression> cause = Optional.empty();
            if (!check(";") && !check(TokenType.NEWLINE) && !isAtEnd()) {
                exc = Optional.ofNullable(parseExpression());
                if (match("from")) {
                    cause = Optional.ofNullable(parseExpression());
                }
            }
            SourceLocation endLoc = cause.map(c -> c.span().end()).orElse(exc.map(e -> e.span().end()).orElse(raiseToken.endLocation()));
            return new PyRaise(SourceSpan.of(raiseToken.startLocation(), endLoc), exc, cause);
        }

        if (check("assert")) {
            Token assertToken = consume();
            PyExpression test = parseExpression();
            Optional<PyExpression> msg = Optional.empty();
            if (match(",")) {
                msg = Optional.ofNullable(parseExpression());
            }
            SourceLocation endLoc = msg.map(m -> m.span().end()).orElse(test.span().end());
            return new PyAssert(SourceSpan.of(assertToken.startLocation(), endLoc), test, msg);
        }

        if (check("pass")) {
            return new PyPass(consume().span());
        }
        if (check("break")) {
            return new PyBreak(consume().span());
        }
        if (check("continue")) {
            return new PyContinue(consume().span());
        }

        if (check("import")) {
            return parseImportStatement();
        }
        if (check("from")) {
            return parseFromImportStatement();
        }

        if (check("global")) {
            Token gTok = consume();
            List<String> names = parseIdentifierList();
            SourceLocation endLoc = previous() != null ? previous().endLocation() : gTok.endLocation();
            return new PyGlobal(SourceSpan.of(gTok.startLocation(), endLoc), names);
        }

        if (check("nonlocal")) {
            Token nlTok = consume();
            List<String> names = parseIdentifierList();
            SourceLocation endLoc = previous() != null ? previous().endLocation() : nlTok.endLocation();
            return new PyNonlocal(SourceSpan.of(nlTok.startLocation(), endLoc), names);
        }

        if (check("del")) {
            Token delTok = consume();
            List<PyExpression> targets = new ArrayList<>();
            targets.add(parseExpression());
            while (match(",")) {
                if (check(";") || check(TokenType.NEWLINE) || isAtEnd()) break;
                targets.add(parseExpression());
            }
            SourceLocation endLoc = previous() != null ? previous().endLocation() : delTok.endLocation();
            return new PyDelete(SourceSpan.of(delTok.startLocation(), endLoc), targets);
        }

        // Expression statement or assignment
        PyExpression expr = parseExpression();

        // 1. Augmented assignment: +=, -=, etc.
        if (isAugmentedAssign(peek().lexeme())) {
            Token opToken = consume();
            PyExpression value = parseAssignmentValue();
            SourceSpan span = SourceSpan.of(expr.span().start(), value.span().end());
            return new PyAugAssign(span, expr, opToken.lexeme(), value);
        }

        // 2. Annotated assignment: target: type [= value]
        if (match(":")) {
            PyExpression typeAnn = parseExpression();
            Optional<PyExpression> value = Optional.empty();
            if (match("=")) {
                value = Optional.ofNullable(parseAssignmentValue());
            }
            SourceLocation endLoc = value.map(v -> v.span().end()).orElse(typeAnn.span().end());
            return new PyAnnAssign(SourceSpan.of(expr.span().start(), endLoc), expr, typeAnn, value);
        }

        // 3. Simple or tuple assignment: x = y or x, y = 1, 2
        if (check("=") || check(",")) {
            List<PyExpression> targets = new ArrayList<>();
            targets.add(expr);
            while (match(",")) {
                if (check("=") || check(";") || check(TokenType.NEWLINE) || isAtEnd()) break;
                targets.add(parseExpression());
            }
            if (match("=")) {
                PyExpression value = parseAssignmentValue();
                SourceSpan span = SourceSpan.of(targets.get(0).span().start(), value.span().end());
                return new PyAssign(span, targets, value);
            }
        }

        // 4. Expression statement
        return new PyExprStmt(expr.span(), expr);
    }

    private PyExpression parseAssignmentValue() {
        PyExpression first = parseExpression();
        if (check(",")) {
            List<PyExpression> elements = new ArrayList<>();
            elements.add(first);
            while (match(",")) {
                if (check(";") || check(TokenType.NEWLINE) || isAtEnd()) break;
                elements.add(parseExpression());
            }
            SourceSpan span = SourceSpan.of(first.span().start(), elements.get(elements.size() - 1).span().end());
            return new PyTuple(span, elements);
        }
        return first;
    }

    private PyImport parseImportStatement() {
        Token impToken = consume(); // import
        List<PyAlias> aliases = new ArrayList<>();
        do {
            aliases.add(parseImportAlias());
        } while (match(","));
        SourceLocation endLoc = previous() != null ? previous().endLocation() : impToken.endLocation();
        return new PyImport(SourceSpan.of(impToken.startLocation(), endLoc), aliases);
    }

    private PyImportFrom parseFromImportStatement() {
        Token fromToken = consume(); // from
        int dots = 0;
        while (match(".")) {
            dots++;
        }

        Optional<String> module = Optional.empty();
        if (check(TokenType.IDENTIFIER)) {
            StringBuilder sb = new StringBuilder();
            sb.append(consume().lexeme());
            while (match(".")) {
                if (check(TokenType.IDENTIFIER)) {
                    sb.append(".").append(consume().lexeme());
                } else {
                    break;
                }
            }
            module = Optional.of(sb.toString());
        }

        if (!match("import")) {
            addDiagnostic(
                CheckCategory.CONTROL_HEADER,
                currentLocation(),
                "Expected 'import' in 'from ... import' statement",
                PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                "Add 'import' keyword"
            );
        }

        List<PyAlias> aliases = new ArrayList<>();
        if (match("*")) {
            Token star = previous();
            aliases.add(new PyAlias(star.span(), "*", Optional.empty()));
        } else {
            boolean hasParen = match("(");
            do {
                if (hasParen && check(")")) break;
                aliases.add(parseImportAlias());
            } while (match(","));
            if (hasParen) {
                match(")");
            }
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : fromToken.endLocation();
        return new PyImportFrom(SourceSpan.of(fromToken.startLocation(), endLoc), module, dots, aliases);
    }

    private PyAlias parseImportAlias() {
        if (!check(TokenType.IDENTIFIER)) {
            SourceLocation loc = currentLocation();
            consume();
            return new PyAlias(SourceSpan.point(loc), "<error>", Optional.empty());
        }
        Token first = consume();
        StringBuilder sb = new StringBuilder(first.lexeme());
        while (match(".")) {
            if (check(TokenType.IDENTIFIER)) {
                sb.append(".").append(consume().lexeme());
            } else {
                break;
            }
        }
        Optional<String> asName = Optional.empty();
        SourceLocation endLoc = previous().endLocation();
        if (match("as")) {
            if (check(TokenType.IDENTIFIER)) {
                Token asTok = consume();
                asName = Optional.of(asTok.lexeme());
                endLoc = asTok.endLocation();
            }
        }
        return new PyAlias(SourceSpan.of(first.startLocation(), endLoc), sb.toString(), asName);
    }

    private List<String> parseIdentifierList() {
        List<String> list = new ArrayList<>();
        if (check(TokenType.IDENTIFIER)) {
            list.add(consume().lexeme());
            while (match(",")) {
                if (check(TokenType.IDENTIFIER)) {
                    list.add(consume().lexeme());
                } else {
                    break;
                }
            }
        }
        return list;
    }

    private void consumeStatementTerminator() {
        while (match(";")) {
            // semicolons consume trailing statement separators
        }
        if (!match(TokenType.NEWLINE) && !isAtEnd() && !check(TokenType.DEDENT)) {
            // statement boundary
        }
    }

    private void synchronizeStatement() {
        while (!isAtEnd()) {
            if (match(TokenType.NEWLINE)) {
                return;
            }
            if (check("def") || check("class") || check("if") || check("for") || check("while")
                || check("return") || check("import") || check("from") || check(TokenType.DEDENT)) {
                return;
            }
            consume();
        }
    }

    // ------------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------------

    private PyExpression parseExpression() {
        return parseTernaryOrLambda();
    }

    private PyExpression parseTernaryOrLambda() {
        if (match("lambda")) {
            Token lamToken = previous();
            List<String> params = new ArrayList<>();
            while (!check(":") && !isAtEnd()) {
                if (check(TokenType.IDENTIFIER)) {
                    params.add(consume().lexeme());
                    match(",");
                } else {
                    break;
                }
            }
            validateColon("lambda");
            PyExpression body = parseExpression();
            SourceSpan span = SourceSpan.of(lamToken.startLocation(), body.span().end());
            return new PyLambda(span, params, body);
        }

        PyExpression expr = parseWalrusOrLogicalOr();

        if (match("if")) {
            PyExpression condition = parseWalrusOrLogicalOr();
            if (!match("else")) {
                addDiagnostic(
                    CheckCategory.CONTROL_HEADER,
                    currentLocation(),
                    "Expected 'else' in conditional expression",
                    PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                    "Add 'else' branch"
                );
            }
            PyExpression falseVal = parseExpression();
            SourceSpan span = SourceSpan.of(expr.span().start(), falseVal.span().end());
            return new PyTernaryExpr(span, condition, expr, falseVal);
        }

        return expr;
    }

    private PyExpression parseWalrusOrLogicalOr() {
        if (check(TokenType.IDENTIFIER) && peek(1).lexeme().equals(":=")) {
            Token target = consume();
            consume(); // :=
            PyExpression val = parseExpression();
            SourceSpan span = SourceSpan.of(target.startLocation(), val.span().end());
            return new PyWalrusExpr(span, target.lexeme(), val);
        }
        return parseLogicalOr();
    }

    private PyExpression parseLogicalOr() {
        PyExpression expr = parseLogicalAnd();
        while (match("or")) {
            Token op = previous();
            PyExpression right = parseLogicalAnd();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseLogicalAnd() {
        PyExpression expr = parseLogicalNot();
        while (match("and")) {
            Token op = previous();
            PyExpression right = parseLogicalNot();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseLogicalNot() {
        if (match("not")) {
            Token op = previous();
            PyExpression operand = parseLogicalNot();
            SourceSpan span = SourceSpan.of(op.startLocation(), operand.span().end());
            return new PyUnaryExpr(span, op.lexeme(), operand);
        }
        return parseComparison();
    }

    private PyExpression parseComparison() {
        PyExpression expr = parseBitwiseOr();

        while (true) {
            String op = null;
            if (match("not") && match("in")) {
                op = "not in";
            } else if (match("is")) {
                if (match("not")) {
                    op = "is not";
                } else {
                    op = "is";
                }
            } else if (match("in")) {
                op = "in";
            } else if (COMPARISON_OPS.contains(peek().lexeme())) {
                op = consume().lexeme();
            }

            if (op == null) {
                break;
            }

            PyExpression right = parseBitwiseOr();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op, right);
        }

        return expr;
    }

    private PyExpression parseBitwiseOr() {
        PyExpression expr = parseBitwiseXor();
        while (match("|")) {
            Token op = previous();
            PyExpression right = parseBitwiseXor();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseBitwiseXor() {
        PyExpression expr = parseBitwiseAnd();
        while (match("^")) {
            Token op = previous();
            PyExpression right = parseBitwiseAnd();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseBitwiseAnd() {
        PyExpression expr = parseShift();
        while (match("&")) {
            Token op = previous();
            PyExpression right = parseShift();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseShift() {
        PyExpression expr = parseAddition();
        while (match("<<") || match(">>")) {
            Token op = previous();
            PyExpression right = parseAddition();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseAddition() {
        PyExpression expr = parseMultiplication();
        while (match("+") || match("-")) {
            Token op = previous();
            if (isBinaryOperator(peek().lexeme())) {
                Token badOp = consume();
                addDiagnostic(
                    CheckCategory.OPERATOR_SYNTAX,
                    badOp.startLocation(),
                    "Unexpected consecutive operator '" + badOp.lexeme() + "'",
                    PythonDiagnosticCodes.ERR_INVALID_SYNTAX,
                    "Provide a valid operand between operators"
                );
            }
            PyExpression right = parseMultiplication();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseMultiplication() {
        PyExpression expr = parseUnary();
        while (match("*") || match("/") || match("//") || match("%") || match("@")) {
            Token op = previous();
            if (isBinaryOperator(peek().lexeme())) {
                Token badOp = consume();
                addDiagnostic(
                    CheckCategory.OPERATOR_SYNTAX,
                    badOp.startLocation(),
                    "Unexpected consecutive operator '" + badOp.lexeme() + "'",
                    PythonDiagnosticCodes.ERR_INVALID_SYNTAX,
                    "Provide a valid operand between operators"
                );
            }
            PyExpression right = parseUnary();
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            expr = new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parseUnary() {
        if (match("+") || match("-") || match("~")) {
            Token op = previous();
            PyExpression operand = parseUnary();
            SourceSpan span = SourceSpan.of(op.startLocation(), operand.span().end());
            return new PyUnaryExpr(span, op.lexeme(), operand);
        }
        return parsePower();
    }

    private PyExpression parsePower() {
        PyExpression expr = parsePostfix();
        if (match("**")) {
            Token op = previous();
            PyExpression right = parseUnary(); // right-associative
            SourceSpan span = SourceSpan.of(expr.span().start(), right.span().end());
            return new PyBinaryExpr(span, expr, op.lexeme(), right);
        }
        return expr;
    }

    private PyExpression parsePostfix() {
        PyExpression expr = parsePrimary();

        while (true) {
            if (check("(")) {
                expr = finishCall(expr);
            } else if (check("[")) {
                expr = finishSubscript(expr);
            } else if (match(".")) {
                if (check(TokenType.IDENTIFIER)) {
                    Token attr = consume();
                    SourceSpan span = SourceSpan.of(expr.span().start(), attr.endLocation());
                    expr = new PyAttribute(span, expr, attr.lexeme());
                } else {
                    addDiagnostic(
                        CheckCategory.CONTROL_HEADER,
                        currentLocation(),
                        "Expected attribute identifier after '.'",
                        PythonDiagnosticCodes.ERR_MALFORMED_HEADER,
                        "Add attribute name"
                    );
                    break;
                }
            } else {
                break;
            }
        }

        return expr;
    }

    private PyCall finishCall(PyExpression callee) {
        Token openParen = consume(); // (
        List<PyArgument> args = new ArrayList<>();

        while (!check(")") && !isAtEnd()) {
            boolean isVararg = match("*");
            boolean isKwarg = !isVararg && match("**");
            Optional<String> name = Optional.empty();

            if (!isVararg && !isKwarg && check(TokenType.IDENTIFIER) && peek(1).lexeme().equals("=")) {
                name = Optional.of(consume().lexeme());
                consume(); // =
            }

            PyExpression val = parseExpression();
            SourceLocation aStart = isVararg || isKwarg ? previous().startLocation() : val.span().start();
            SourceSpan aSpan = SourceSpan.of(aStart, val.span().end());
            args.add(new PyArgument(aSpan, name, val, isVararg, isKwarg));

            if (!match(",")) {
                break;
            }
        }

        if (!match(")")) {
            addDiagnostic(
                CheckCategory.DELIMITER_MATCH,
                openParen.startLocation(),
                "Unclosed parenthesis '('. Expected ')' before line end",
                PythonDiagnosticCodes.ERR_UNCLOSED_DELIMITER,
                "Add closing ')'"
            );
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : callee.span().end();
        SourceSpan span = SourceSpan.of(callee.span().start(), endLoc);
        return new PyCall(span, callee, args);
    }

    private PySubscript finishSubscript(PyExpression value) {
        Token openBracket = consume(); // [
        PyExpression slice = null;

        // Check for slicing syntax like [:5] or [1:5:2]
        if (check(":")) {
            slice = parseSlice(Optional.empty());
        } else {
            PyExpression lower = parseExpression();
            if (check(":")) {
                slice = parseSlice(Optional.of(lower));
            } else {
                slice = lower;
            }
        }

        if (!match("]")) {
            addDiagnostic(
                CheckCategory.DELIMITER_MATCH,
                openBracket.startLocation(),
                "Unclosed square bracket '['",
                PythonDiagnosticCodes.ERR_UNCLOSED_DELIMITER,
                "Add closing ']'"
            );
        }

        SourceLocation endLoc = previous() != null ? previous().endLocation() : value.span().end();
        SourceSpan span = SourceSpan.of(value.span().start(), endLoc);
        return new PySubscript(span, value, slice);
    }

    private PySlice parseSlice(Optional<PyExpression> lower) {
        Token colon1 = consume(); // :
        Optional<PyExpression> upper = Optional.empty();
        if (!check(":") && !check("]") && !isAtEnd()) {
            upper = Optional.ofNullable(parseExpression());
        }

        Optional<PyExpression> step = Optional.empty();
        if (match(":")) {
            if (!check("]") && !isAtEnd()) {
                step = Optional.ofNullable(parseExpression());
            }
        }

        SourceLocation startL = lower.map(l -> l.span().start()).orElse(colon1.startLocation());
        SourceLocation endL = step.map(s -> s.span().end()).orElse(upper.map(u -> u.span().end()).orElse(colon1.endLocation()));
        return new PySlice(SourceSpan.of(startL, endL), lower, upper, step);
    }

    private PyExpression parsePrimary() {
        Token tok = peek();

        if (tok.tokenType() == TokenType.LITERAL_NUMBER) {
            consume();
            return new PyLiteral(tok.span(), tok.lexeme(), "NUMBER", tok.lexeme());
        }

        if (tok.tokenType() == TokenType.LITERAL_STRING) {
            consume();
            return new PyLiteral(tok.span(), tok.lexeme(), "STRING", tok.lexeme());
        }

        if (match("True") || match("False")) {
            Token t = previous();
            return new PyLiteral(t.span(), t.lexeme(), "BOOLEAN", Boolean.parseBoolean(t.lexeme()));
        }

        if (match("None")) {
            Token t = previous();
            return new PyLiteral(t.span(), t.lexeme(), "NONE", null);
        }

        if (tok.tokenType() == TokenType.IDENTIFIER) {
            consume();
            return new PyIdentifier(tok.span(), tok.lexeme());
        }

        // Parentheses: parenthesized expr, tuple, generator expression
        if (match("(")) {
            Token openP = previous();
            if (match(")")) {
                return new PyTuple(SourceSpan.of(openP.startLocation(), previous().endLocation()), List.of());
            }

            PyExpression first = parseExpression();

            // Generator expression? (x for x in items)
            if (check("for")) {
                List<PyComprehensionClause> clauses = parseComprehensionClauses();
                expectParen(")", openP);
                SourceSpan span = SourceSpan.of(openP.startLocation(), previous().endLocation());
                return new PyGeneratorExpr(span, first, clauses);
            }

            // Tuple? (a, b) or (a,)
            if (match(",")) {
                List<PyExpression> elements = new ArrayList<>();
                elements.add(first);
                while (!check(")") && !isAtEnd()) {
                    elements.add(parseExpression());
                    if (!match(",")) break;
                }
                expectParen(")", openP);
                SourceSpan span = SourceSpan.of(openP.startLocation(), previous().endLocation());
                return new PyTuple(span, elements);
            }

            expectParen(")", openP);
            return first;
        }

        // Brackets: list or list comprehension
        if (match("[")) {
            Token openB = previous();
            if (match("]")) {
                return new PyList(SourceSpan.of(openB.startLocation(), previous().endLocation()), List.of());
            }

            PyExpression first = parseExpression();

            // List comprehension? [x for x in items]
            if (check("for")) {
                List<PyComprehensionClause> clauses = parseComprehensionClauses();
                expectBracket("]", openB);
                SourceSpan span = SourceSpan.of(openB.startLocation(), previous().endLocation());
                return new PyListComp(span, first, clauses);
            }

            List<PyExpression> elements = new ArrayList<>();
            elements.add(first);
            while (match(",")) {
                if (check("]")) break;
                elements.add(parseExpression());
            }
            expectBracket("]", openB);
            SourceSpan span = SourceSpan.of(openB.startLocation(), previous().endLocation());
            return new PyList(span, elements);
        }

        // Braces: dict, set, or dict/set comprehension
        if (match("{")) {
            Token openBrace = previous();
            if (match("}")) {
                return new PyDict(SourceSpan.of(openBrace.startLocation(), previous().endLocation()), List.of());
            }

            PyExpression first = parseExpression();

            // Dict entry: key: value
            if (match(":")) {
                PyExpression val = parseExpression();
                PyDictEntry entry = new PyDictEntry(SourceSpan.of(first.span().start(), val.span().end()), first, val);

                // Dict comprehension? {k: v for k, v in items}
                if (check("for")) {
                    List<PyComprehensionClause> clauses = parseComprehensionClauses();
                    expectBrace("}", openBrace);
                    SourceSpan span = SourceSpan.of(openBrace.startLocation(), previous().endLocation());
                    return new PyDictComp(span, first, val, clauses);
                }

                List<PyDictEntry> entries = new ArrayList<>();
                entries.add(entry);
                while (match(",")) {
                    if (check("}")) break;
                    PyExpression k = parseExpression();
                    if (!match(":")) {
                        addDiagnostic(CheckCategory.STATEMENT_TERMINATOR, currentLocation(), "Expected ':' after dictionary key", PythonDiagnosticCodes.ERR_INVALID_SYNTAX, "Add ':' between key and value");
                    }
                    PyExpression v = parseExpression();
                    entries.add(new PyDictEntry(SourceSpan.of(k.span().start(), v.span().end()), k, v));
                }
                expectBrace("}", openBrace);
                SourceSpan span = SourceSpan.of(openBrace.startLocation(), previous().endLocation());
                return new PyDict(span, entries);
            }

            // Set or set comprehension
            if (check("for")) {
                List<PyComprehensionClause> clauses = parseComprehensionClauses();
                expectBrace("}", openBrace);
                SourceSpan span = SourceSpan.of(openBrace.startLocation(), previous().endLocation());
                return new PySetComp(span, first, clauses);
            }

            List<PyExpression> elements = new ArrayList<>();
            elements.add(first);
            while (match(",")) {
                if (check("}")) break;
                elements.add(parseExpression());
            }
            expectBrace("}", openBrace);
            SourceSpan span = SourceSpan.of(openBrace.startLocation(), previous().endLocation());
            return new PySet(span, elements);
        }

        if (match("yield")) {
            Token yTok = previous();
            boolean isFrom = match("from");
            Optional<PyExpression> val = Optional.empty();
            if (!check(";") && !check(TokenType.NEWLINE) && !isAtEnd()) {
                val = Optional.ofNullable(parseExpression());
            }
            SourceLocation endLoc = val.map(v -> v.span().end()).orElse(yTok.endLocation());
            return new PyYieldExpr(SourceSpan.of(yTok.startLocation(), endLoc), val, isFrom);
        }

        // Fallback for unexpected tokens
        Token unexp = consume();
        addDiagnostic(
            CheckCategory.OPERATOR_SYNTAX,
            unexp.startLocation(),
            "Unexpected token '" + unexp.lexeme() + "' in expression",
            PythonDiagnosticCodes.ERR_INVALID_SYNTAX,
            "Remove or replace unexpected token"
        );
        return new PyIdentifier(unexp.span(), "<error>");
    }

    private List<PyComprehensionClause> parseComprehensionClauses() {
        List<PyComprehensionClause> clauses = new ArrayList<>();
        while (match("for")) {
            Token forTok = previous();
            PyExpression target = parseForTarget();
            if (!match("in")) {
                addDiagnostic(CheckCategory.CONTROL_HEADER, currentLocation(), "Expected 'in' in comprehension", PythonDiagnosticCodes.ERR_MALFORMED_HEADER, "Add 'in'");
            }
            PyExpression iter = parseWalrusOrLogicalOr();
            List<PyExpression> ifs = new ArrayList<>();
            while (match("if")) {
                ifs.add(parseWalrusOrLogicalOr());
            }
            SourceLocation endL = ifs.isEmpty() ? iter.span().end() : ifs.get(ifs.size() - 1).span().end();
            clauses.add(new PyComprehensionClause(SourceSpan.of(forTok.startLocation(), endL), target, iter, ifs));
        }
        return clauses;
    }

    private void expectParen(String delim, Token openToken) {
        if (!match(delim)) {
            addDiagnostic(
                CheckCategory.DELIMITER_MATCH,
                openToken.startLocation(),
                "Unclosed parenthesis '('. Expected ')' before line end",
                PythonDiagnosticCodes.ERR_UNCLOSED_DELIMITER,
                "Add closing ')'"
            );
        }
    }

    private void expectBracket(String delim, Token openToken) {
        if (!match(delim)) {
            addDiagnostic(
                CheckCategory.DELIMITER_MATCH,
                openToken.startLocation(),
                "Unclosed square bracket '['. Expected ']' before line end",
                PythonDiagnosticCodes.ERR_UNCLOSED_DELIMITER,
                "Add closing ']'"
            );
        }
    }

    private void expectBrace(String delim, Token openToken) {
        if (!match(delim)) {
            addDiagnostic(
                CheckCategory.DELIMITER_MATCH,
                openToken.startLocation(),
                "Unclosed curly brace '{'. Expected '}' before line end",
                PythonDiagnosticCodes.ERR_UNCLOSED_DELIMITER,
                "Add closing '}'"
            );
        }
    }

    // ------------------------------------------------------------------------
    // Token navigation helpers
    // ------------------------------------------------------------------------

    private boolean isBinaryOperator(String lexeme) {
        return "*".equals(lexeme) || "/".equals(lexeme) || "//".equals(lexeme) || "%".equals(lexeme)
            || "+".equals(lexeme) || "-".equals(lexeme) || "**".equals(lexeme) || "@".equals(lexeme);
    }

    private boolean isAugmentedAssign(String lexeme) {
        return AUGMENTED_ASSIGN_OPS.contains(lexeme);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().tokenType() == type;
    }

    private boolean check(String lexeme) {
        if (isAtEnd()) return false;
        return peek().lexeme().equals(lexeme);
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            consume();
            return true;
        }
        return false;
    }

    private boolean match(String lexeme) {
        if (check(lexeme)) {
            consume();
            return true;
        }
        return false;
    }

    private Token consume() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private Token peek() {
        if (current >= codeTokens.size()) {
            SourceLocation loc = previous() != null ? previous().endLocation() : SourceLocation.start();
            return Token.of(TokenType.UNKNOWN, "", SourceSpan.point(loc));
        }
        return codeTokens.get(current);
    }

    private Token peek(int k) {
        int idx = current + k;
        if (idx >= codeTokens.size()) {
            SourceLocation loc = previous() != null ? previous().endLocation() : SourceLocation.start();
            return Token.of(TokenType.UNKNOWN, "", SourceSpan.point(loc));
        }
        return codeTokens.get(idx);
    }

    private Token previous() {
        if (current == 0) return null;
        return codeTokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return current >= codeTokens.size();
    }

    private SourceLocation currentLocation() {
        if (isAtEnd()) {
            return previous() != null ? previous().endLocation() : SourceLocation.start();
        }
        return peek().startLocation();
    }

    private void addDiagnostic(
        CheckCategory category,
        SourceLocation location,
        String message,
        String code,
        String suggestedFix
    ) {
        diagnostics.add(Diagnostic.error(category, location, message, code, suggestedFix));
    }
}
