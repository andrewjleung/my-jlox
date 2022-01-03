package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.craftinginterpreters.lox.TokenType;

public class Scanner {
    /**
     * A map from reserved keywords to their TokenType.
     */
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",    TokenType.AND);
        keywords.put("class",  TokenType.CLASS);
        keywords.put("else",   TokenType.ELSE);
        keywords.put("false",  TokenType.FALSE);
        keywords.put("for",    TokenType.FOR);
        keywords.put("fun",    TokenType.FUN);
        keywords.put("if",     TokenType.IF);
        keywords.put("nil",    TokenType.NIL);
        keywords.put("or",     TokenType.OR);
        keywords.put("print",  TokenType.PRINT);
        keywords.put("return", TokenType.RETURN);
        keywords.put("super",  TokenType.SUPER);
        keywords.put("this",   TokenType.THIS);
        keywords.put("true",   TokenType.TRUE);
        keywords.put("var",    TokenType.VAR);
        keywords.put("while",  TokenType.WHILE);
    }

    /**
     * The source code being scanned.
     */
    private final String source;

    /**
     * The List of Tokens produced from scanning the source code.
     */
    private final List<Token> tokens = new ArrayList<>();

    /**
     * The offset of the first character of the current lexeme being scanned.
     */
    private int start = 0;

    /**
     * The offset of the current character being considered in the current lexeme.
     */
    private int current = 0;

    /**
     * Which source line the `current` offset is on in the source code.
     * This is necessary since tokens must know their locations.
     */
    private int line = 1;

    Scanner(String source) {
        this.source = source;
    }

    /**
     * Scan tokens from this Scanner's source code.
     *
     * @return the List of Tokens produced from scanning the source code
     */
    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;

            // Scan a single token and append it to the tokens list.
            scanToken();
        }

        // Append the final EOF token to make parsing cleaner.
        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    /**
     * Scan a single token starting at the next character.
     */
    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(TokenType.LEFT_PAREN); break;
            case ')': addToken(TokenType.RIGHT_PAREN); break;
            case '{': addToken(TokenType.LEFT_BRACE); break;
            case '}': addToken(TokenType.RIGHT_BRACE); break;
            case ',': addToken(TokenType.COMMA); break;
            case '.': addToken(TokenType.DOT); break;
            case '-': addToken(TokenType.MINUS); break;
            case '+': addToken(TokenType.PLUS); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '*': addToken(TokenType.STAR); break;
            case '!':
                addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
                break;
            case '=':
                addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                break;
            case '<':
                addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                break;
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line (the newline character).
                    // Keep advancing the current offset until reaching the newline.
                    // `peek` is necessary so that we don't end up consuming the newline.
                    while (peek() != '\n' && !isAtEnd()) advance();

                    // No token is added when we consume the entire comment.
                    // Comments are not meaningful to interpretation, and should be
                    // discarded before it's time for parsing.
                } else {
                    // We have a single slash here meaning that it must be a division token.
                    addToken(TokenType.SLASH);
                }
                break;
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;
            case '\n':
                line++;
                break;
            case '"': string(); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    // Identifiers must begin with an alphabetical character.
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    /**
     * Consume and tokenize an identifier.
     *
     * Note that an identifier may be a reserved keyword.
     */
    private void identifier() {
        // Consume as many alphanumeric characters as possible.
        // Following the first alphabetical character, characters in an
        // identifier may be digits.
        while (isAlphaNumeric(peek())) advance();

        // Get the identifier substring from the source code.
        String text = source.substring(start, current);

        // Use the keywords map to find any existing corresponding
        // reserved keyword type that matches the identifier.
        TokenType type = keywords.get(text);

        // If no match was found, then the identifier is not a reserved
        // keyword and can just be considered as an identifier.
        if (type == null) type = TokenType.IDENTIFIER;

        // Append the token.
        addToken(type);
    }

    /**
     * Consume and tokenize a number literal.
     */
    private void number() {
        // Consume digits before the decimal point.
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        // We now need two characters of lookahead since we need to check that
        // there is both a decimal point and a number following it, and we don't
        // want to consume anything until both of those are made certain.
        // We can call methods on number literals!
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the decimal point.
            advance();

            // Consume digits following the decimal point.
            while (isDigit(peek())) advance();
        }

        // Append the token with its Double value.
        addToken(TokenType.NUMBER,
                Double.parseDouble(source.substring(start, current)));
    }

    /**
     * Consume and tokenize a string literal.
     */
    private void string() {
        // Consume characters until reaching the closing double quote.
        while (peek() != '"' && !isAtEnd()) {
            // Strings may span multiple lines so newlines may be consumed as part
            // of them. However, the line count must still be incremented.
            if (peek() == '\n') line++;
            advance();
        }

        // The string ended before the closing double quote was reached.
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing ".
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);

        // TODO: support escape sequences by unescaping them here.

        // Append the token with its string literal value.
        addToken(TokenType.STRING, value);
    }

    /**
     * Determine whether the current character matches the given expected character.
     *
     * The current character is only consumed (the offset advances past it) if it
     * matches the expected character.
     *
     * Otherwise, its scanning is deferred since it does not belong to the current lexeme.
     *
     * @param expected the character being checked for a match with the current character
     * @return whether the current character matches the expected character
     */
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    /**
     * Return the current character without consuming it.
     *
     * This is otherwise known as a lookahead, where in this case there is a
     * "single character of lookahead."
     *
     * The smaller the lookahead, the faster the scanner.
     *
     * @return the character at the current offset
     */
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    /**
     * Return the character following the current character without consuming it.
     *
     * This implements a second character of lookahead.
     *
     * @return the character at the current offset + 1
     */
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    /**
     * Determine if the given character is an alphabetical character.
     *
     * @param c the character to check
     * @return whether the given character is an alphabetical character.
     */
    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    /**
     * Determine if the given character is an alphanumeric character.
     *
     * @param c the character to check
     * @return whether the given character is an alphanumeric character
     */
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }


    /**
     * Determine if the given character is a digit 0-9.
     *
     * @param c the character to check
     * @return whether the given character is a digit 0-9
     */
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Determine if all characters in the source code have been consumed.
     *
     * @return whether all characters in the source cde have been consumed.
     */
    private boolean isAtEnd() {
        return current >= source.length();
    }

    /**
     * Advance the current offset to the next character in the source code.
     *
     * @return the next character
     */
    private char advance() {
        return source.charAt(current++);
    }

    /**
     * Add a token of the given TokenType bounded by the start and current offsets.
     * This helper is for tokens without a defined literal value.
     *
     * @param type the token's type
     */
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    /**
     * Add a token of the given TokenType and with the given literal value.
     * The token is bounded by the start and current offsets.
     *
     * @param type the token's type
     * @param literal the token's literal value
     */
    private void addToken(TokenType type, Object literal) {
        // Grab the token from the source using the start and current bounds.
        String text = source.substring(start, current);

        // Append the token to the List of Tokens
        tokens.add(new Token(type, text, literal, line));
    }
}
