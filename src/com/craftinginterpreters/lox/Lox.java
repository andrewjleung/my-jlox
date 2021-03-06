package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    /**
     * The Lox interpreter.
     * This is static so that a single REPL session can reuse it and
     * store global variables.
     */
    private static final Interpreter interpreter = new Interpreter();

    /**
     * Flag used to ensure code with known errors is not executed.
     */
    static boolean hadError = false;

    /**
     * Flag used to reflect that a runtime error occurred.
     */
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            // Only a single argument is expected.
            // If more than one is given, remind the user of the usage.
            System.out.println("Usage: jlox [script]");

            // The command was used incorrectly, so we exit.
            // https://www.freebsd.org/cgi/man.cgi?query=sysexits&apropos=0&sektion=0&manpath=FreeBSD+4.3-RELEASE&format=html
            System.exit(64);
        } else if (args.length == 1) {
            // Only a single argument was provided.
            // This is interpreted as the Lox file to read and execute.
            runFile(args[0]);
        } else {
            // No arguments were provided.
            // This runs the interpreter interactively, REPL-style.
            // You may enter end execute code one line at a time.
            runPrompt();
        }
    }

    /**
     * Execute source code located at the given file path.
     *
     * @param path the file path
     * @throws IOException
     */
    private static void runFile(String path) throws IOException {
        // Read all bytes from the provided file path into a byte array.
        byte[] bytes = Files.readAllBytes(Paths.get(path));

        // Create a string from the byte array and run it!
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code.
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    /**
     * Run a Lox interactive prompt (REPL).
     * This will continuously consume and execute user input line by line.
     * The loop is terminated via an EOF signalled by CTRL+D.
     *
     * @throws IOException
     */
    private static void runPrompt() throws IOException {
        // Setup Readers for user input.
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        // Infinitely loop until the user types CTRL+D signalling an EOF.
        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();

            // CTRL+D causes `readLine()` to return `null`.
            if (line == null) break;
            run(line);

            // Reset the `hadError` flag to prepare for the next line of input.
            // This way, a mistake won't kill the entire session.
            hadError = false;
        }
    }

    /**
     * Execute a given string of source code.
     *
     * TODO: restore the ability to enter a single expression and evaluate it
     *
     * @param source the string of source code
     */
    private static void run(String source) {
        // Initialize our Lox scanner with the given source code.
        Scanner scanner = new Scanner(source);

        // Use the scanner to lex the source code into tokens.
        List<Token> tokens = scanner.scanTokens();

        // Parse the tokens.
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        // Run variable resolution semantic analysis.
        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        // Interpret the expression syntax tree.
        interpreter.interpret(statements);
    }


    /**
     * Report an error at the given line with the given message.
     *
     * TODO: Setup more helpful error reporting by providing the exact location of the syntax error.
     *
     * For example:
     * ```
     * Error: Unexpected "," in argument list.
     *
     * 15 | function(first, second,);
     *                            ^-- Here.
     * ```
     *
     * @param line the line where the error occurred
     * @param message the error message
     */
    static void error(int line, String message) {
        report(line, "", message);
    }

    /**
     * Report an error at the given line and location with the given message.
     *
     * @param line the line where the error occurred
     * @param where where in the line the error occurred
     * @param message the error message
     */
    private static void report(int line, String where,
                               String message) {
        System.err.println(
                "[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    /**
     * Report a parsing error at the given token with the given message.
     *
     * @param token the token where the error occurred
     * @param message the error message
     */
    static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    /**
     * Report a runtime error.
     *
     * @param error the RuntimeError which occurred
     */
    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }
}