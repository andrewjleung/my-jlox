package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

/**
 * Class for parsing tokens into an Expr syntax tree.
 *
 * This abides by the following grammar:
 * expression     → equality ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | primary ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")" ;
 *
 * TODO: add support for C-like comma expressions (add to the grammar and implement)
 * TODO: add support for the C-style conditional / ternary operator ?:
 * TODO: add error productions to handle binary operators without a left operand,
 *       this should report the error and parse / discard the right operand with
 *       the appropriate precedence
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
     * Begin parsing this Parser's list of tokens.
     *
     * @return the syntax tree in the case of no syntax errors, null if there
     * are syntax errors
     */
    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    /**
     * Match an expression starting from the current token in the
     * list of tokens and produce its corresponding Expr syntax tree.
     *
     * @return an Expr syntax tree representing the matched expression
     */
    private Expr expression() {
        return equality();
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
        return primary();
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