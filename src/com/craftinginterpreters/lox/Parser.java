package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * Class for parsing tokens into an Expr syntax tree.
 *
 * This abides by the following grammar:
 * program        → declaration* EOF ;
 *
 * declaration    → funDecl
 *                | varDecl
 *                | statement ;
 * funDecl        → "fun" function ;
 * function       → IDENTIFIER "(" parameters? ")" block ;
 * parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
 * varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
 *
 * statement      → exprStmt
 *                | forStmt
 *                | ifStmt
 *                | printStmt
 *                | whileStmt
 *                | block ;
 * exprStmt       → expression ";" ;
 * forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
 *                  expression? ";"
 *                  expression? ")" statement ;
 * ifStmt         → "if" "(" expression ")" statement
 *                 ( "else" statement )? ;
 * printStmt      → "print" expression ";" ;
 * whileStmt      → "while" "(" expression ")" statement ;
 * block          → "{" declaration* "}" ;
 *
 * expression     → assignment ;
 * assignment     → IDENTIFIER "=" assignment
 *                | logic_or ;
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → equality ( "and" equality )* ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary | call ;
 * call           → primary ( "(" arguments? ")" )* ;
 * arguments      → expression ( "," expression )* ;
 * primary        → "true" | "false" | "nil"
 *                | NUMBER | STRING
 *                | "(" expression ")"
 *                | IDENTIFIER ;
 *
 * TODO: add support for C-like comma expressions (add to the grammar and implement)
 * TODO: add support for the C-style conditional / ternary operator ?:
 * TODO: add error productions to handle binary operators without a left operand,
 *       this should report the error and parse / discard the right operand with
 *       the appropriate precedence
 * TODO: add support for `break` and `continue` statements in loops (https://craftinginterpreters.com/control-flow.html#challenges)
 *
 */
class Parser {
    /**
     * An error indicating a syntax error found while parsing.
     *
     * This is used as a sentinel returned upon finding a syntax error,
     * letting the parser know what has happened while letting it choose
     * what to do (whether to synchronize or not).
     */
    private static class ParseError extends RuntimeException {}

    /**
     * The list of tokens to parse into an Expr tree.
     */
    private final List<Token> tokens;

    /**
     * The next token to be parsed.
     */
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Parse and collect statement ASTs from this Parser's list of tokens.
     *
     * @return the list of parsed statement ASTs
     */
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();

        // Until the end of the list of tokens (the EOF token),
        // keep parsing and collecting statement ASTs.
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    /**
     * Match an expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr expression() {
        return assignment();
    }

    /**
     * Parse a single declaration from the current position in this
     * Parser's list of tokens.
     *
     * @return the parsed declaration
     */
    private Stmt declaration() {
        try {
            if (match(FUN)) return function("function");
            if (match(VAR)) return varDeclaration();

            // Otherwise, parse a statement.
            return statement();
        } catch (ParseError error) {
            // Initiate error recovery.
            // Now that the stack is unwound, synchronize tokens to the
            // next statement and continue parsing.
            synchronize();
            return null;
        }
    }

    /**
     * Parse a single statement AST from the current position in this
     * Parser's list of tokens.
     *
     * @return the parsed statement AST
     */
    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    /**
     * Parse a single for statement AST from the current position in
     * this Parser's list of tokens.
     *
     * @return the parsed for statement AST
     */
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // Parse the optional initializer.
        Stmt initializer;
        if (match(SEMICOLON)) {
            // There is no initializer.
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // Parse the optional condition.
        Expr condition = null;
        if (!check(SEMICOLON)) {
            // There is a condition. Parse an expression.
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        // Parse the optional increment.
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            // There is an increment, Parse an expression.
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        // Parse the body.
        Stmt body = statement();

        // Desugar this for-loop into a while loop.
        // If there is an increment, modify the body to be a block
        // containing the original parsed body and the increment expression
        // at the end.
        if (increment != null) {
            body = new Stmt.Block(
                    Arrays.asList(
                            body,
                            new Stmt.Expression(increment)));
        }

        // If there is no condition, then the desugared while loop should
        // infinitely loop.
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // If there is an initializer, we need to execute it prior to the while
        // loop to set any initial loop state.
        // This statement then becomes a block containing the initializer and
        // then the while loop.
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    /**
     * Parse a single if statement AST from the current position in this
     * Parser's list of tokens.
     *
     * @return the parsed if statement AST
     */
    private Stmt ifStatement() {
        // Parse the if-condition.
        // The condition is an expression, which when interpreted will
        // be coerced into a Boolean via Lox's rules for truthiness / falsiness.
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        // Parse the mandatory then-statement.
        Stmt thenBranch = statement();

        // Parse the optional else-statement.
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /**
     * Parse a single print statement AST from the current position in this
     * Parser's list of tokens.
     *
     * @return the parsed print statement AST
     */
    private Stmt printStatement() {
        // Parse the expression.
        // The `print` keyword has already been consumed in order to
        // know that the following statement is a print statement.
        Expr value = expression();

        // Consume the final semicolon of the statement.
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    /**
     * Parse a single variable declaration statement AST from the current
     * position in this Parser's list of tokens.
     *
     * @return the parsed variable declaration statement AST
     */
    private Stmt varDeclaration() {
        // Consume the variable's name.
        Token name = consume(IDENTIFIER, "Expect variable name.");

        // Set the variable'
        // s initializer if present.
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        // Consume the final semicolon.
        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    /**
     * Parse a single while statement AST from the current position in this
     * Parser's list of tokens.
     *
     * @return the parsed while statement AST
     */
    private Stmt whileStatement() {
        // Parse the condition expression.
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");

        // Parse the body.
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    /**
     * Parse a single expression statement AST from the current position in
     * this Parser's list of tokens.
     *
     * @return the parsed expression statement AST
     */
    private Stmt expressionStatement() {
        // Parse the expression.
        Expr expr = expression();

        // Consume the final semicolon of the statement.
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    /**
     * Parse a single function statement AST from the current position in
     * this Parser's list of tokens.
     *
     * @param kind what type of function this is (function | method)
     * @return the parsed function statement AST
     */
    private Stmt.Function function(String kind) {
        // The "fun" keyword has already been parsed.
        // Parse the function's identifier.
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        // Parse the left paren.
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

        // Parse the function's parameters.
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                // Enforce a limit of 255 function parameters.
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }

                // Parse the next parameter name.
                parameters.add(
                        consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }

        // Parse the right paren.
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        // Parse the left brace.
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");

        // Parse the function's body block.
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    /**
     * Parse a list of statements from a block starting from the current
     * position in this Parser's list of tokens.
     *
     * At this point, the left brace has already been consumed.
     *
     * @return the list of Stmts within the block
     */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        // Consume statements until the end of the block / file.
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        // Consume the closing curly brace of the block.
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    /**
     * Parse a single assignment expression AST from the current position
     * in this Parser's list of tokens.
     *
     * @return the parsed assignment expression AST
     */
    private Expr assignment() {
        // Parse the left side of the assignment.
        // Note that the equality production is actually a strict superset
        // of the set of valid l-values for assignment.
        // Because of this, if we confirm that this is an assignment,
        // we need to later validate that this expression is actually a
        // valid assignment target i.e. a Variable expression.
        Expr expr = or();

        // If an equals sign is found, then this must be assignment.
        // Effectively, we must convert the found r-value expression into an l-value.
        // Recursively parse the right side as an assignment (right-associative).
        // As previously mentioned, the left side must then be validated.
        if (match(EQUAL)) {
            // Get the equals token.
            Token equals = previous();

            // Recursively parse the right side as an assignment.
            Expr value = assignment();

            // Validate that the left is a valid assignment target.
            // If so, produce the Assign syntax tree.
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            // Report an error since an assignment was found without a valid
            // assignment target.
            // This does not throw because the interpreter isn't confused.
            // It still knows the general structure of the code and doesn't
            // need to panic and synchronize.
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    /**
     * Parse a single logical or expression AST from the current position
     * in this Parser's list of tokens.
     *
     * @return the parsed logical or expression AST
     */
    private Expr or() {
        // Parse the left and expression (or higher precedence).
        Expr expr = and();

        // If there is an `or` keyword, this is an or expression.
        // Parse the right and expression (or higher precedence).
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * Parse a single logical and expression AST from the current position
     * in this Parser's list of tokens.
     *
     * @return the parsed logical and expression AST
     */
    private Expr and() {
        // Parse the left equality expression (or higher precedence).
        Expr expr = equality();

        // If there is an `and` keyword, this is an and expression.
        // Parse the right equality expression (or higher precedence).
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * Match an equality expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr equality() {
        // Produce a syntax tree for the left comparison operand.
        Expr expr = comparison();

        // Match as many equality operators as possible.
        // Each iteration stores the resulting expression back into expr.
        // This creates a left-associative nested tree of binary operator nodes.
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            // match consumes the operator that is found, meaning that
            // in order to get that operator, we need to check the
            // previously consumed token.
            Token operator = previous();

            // Produce a syntax tree for the right comparison operand.
            Expr right = comparison();

            // Form the entire Expr including the parsed left and right
            // operands along with whichever equality operator was used.
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Match a comparison expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr comparison() {
        Expr expr = term();

        // This works the same as equality.
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Match a term expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr term() {
        Expr expr = factor();

        // This works the same as equality.
        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Match a factor expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr factor() {
        Expr expr = unary();

        // This works the same as equality.
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Match a unary expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr unary() {
        // If there is a unary operator, parse the right unary expression
        // then return the entire unary expression.
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        // If there is no unary operator, parse the next higher precedence expression.
        return call();
    }

    /**
     * Finish a function call having already parsed the given callee
     * expression and left paren.
     *
     * @param callee the callee expression
     * @return the call expression
     */
    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();

        // If there is no right paren yet, then there is an argument.
        // So long as there are still commas found, parse arguments.
        if (!check(RIGHT_PAREN)) {
            do {
                // Limit the number of arguments to 255.
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        // Parse the right paren.
        Token paren = consume(RIGHT_PAREN,
                "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    /**
     * Parse a single call expression AST from the current position
     * in this Parser's list of tokens.
     *
     * @return the parsed call expression AST
     */
    private Expr call() {
        Expr expr = primary();

        while (true) {
            // Match an arbitrary amount of calls.
            // Once there are no more, exit the loop.
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    /**
     * Match a primary expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    /**
     * Determine if the current token has any of the given types.
     * if there is a match, the token is consumed.
     * Otherwise, no token is consumed.
     *
     * @param types the types to match
     * @return whether any of the given types were matched
     */
    private boolean match(TokenType... types) {
        // Check each of the given types for a match.
        for (TokenType type : types) {
            // If a match is found, consume it and stop there.
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /**
     * Consume and return the next token if it matches a given type.
     * Otherwise, throw an error with a given message specifying the token
     * without consuming it.
     *
     * @param type the type to match
     * @param message the error message
     * @return the consumed token
     */
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    /**
     * Determine if the current token is of the given type.
     * No token is consumed.
     *
     * @param type the type to check
     * @return whether the token is of the given type
     */
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    /**
     * Consume and return the current token.
     *
     * @return the current token
     */
    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    /**
     * Determine whether the entire list of tokens has been consumed.
     *
     * @return whether the entire list of tokens has been consumed
     */
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    /**
     * Return the current token without consuming it.
     *
     * @return the current token
     */
    private Token peek() {
        return tokens.get(current);
    }

    /**
     * Return the most recently consumed token.
     *
     * @return the most recently consumed token
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /**
     * Report a parsing error at the given token and return a ParseError sentinel.
     *
     * @param token the token where the error occurred
     * @param message the error message
     * @return the ParseError sentinel
     */
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    /**
     * Synchronize the current token position by discarding tokens until the
     * next statement boundary, indicated by a statement keyword.
     */
    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
