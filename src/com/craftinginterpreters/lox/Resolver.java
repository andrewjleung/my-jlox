package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

// TODO: add analysis to this or another static visiting pass which checks for:
//       - unreachable code following a return
//       - local variables that are never read
//       Note that there is a cost to doing more passes, so it may be wise
//       to continue bundling these passes within this resolver.
// TODO: extend the resolver to associate a unique index for each local variable
//       declared in a scope, then when resolving a variable, look up both the scope
//       the variable is in and its index and store that, then in the interpreter
//       use that to quickly access a variable by its index instead of using a map
// https://craftinginterpreters.com/resolving-and-binding.html#challenges
class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    /**
     * The Interpreter in which to store results of this Resolver's semantic
     * analysis, that is, the resolution of variables within the Interpreter's
     * syntax tree.
     */
    private final Interpreter interpreter;

    /**
     * The stack of lexical scopes currently in scope.
     *
     * Each map represents a single local block scope.
     * Keys represent variable names while values represent whether the
     * variable's initializer has been resolved yet. This is used to handle
     * reporting errors like the following:
     *
     *   var a = "outer";
     *   {
     *     var a = a;
     *   }
     *
     * The topmost scope in the stack represents the environment of the
     * current block being traversed, with lower scopes in the stack
     * representing consecutive outer scopes.
     *
     * This does not track the global scope since it is 100% dynamic in Lox.
     * If a variable is not found within this stack, then it is assumed to be global.
     */
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();

    /**
     * The current function that is being visited.
     * This defaults to NONE, as the resolver will always start at the top level.
     */
    private FunctionType currentFunction = FunctionType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE,
        FUNCTION
    }

    /**
     * Resolve variables within the given lexical scope, represented by
     * the list of statements within that block.
     *
     * @param statements the list of statements within the lexical scope
     */
    void resolve(List<Stmt> statements) {
        // Resolve each statement.
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        // Resolve the value of the assignment in case it also contains
        // references to other variables.
        resolve(expr.value);

        // Resolve the variable being assigned to.
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);

        for (Expr argument : expr.arguments) {
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        // We are currently trying to access a variable within its own
        // initializer, which signals an error.
        if (!scopes.isEmpty() &&
                scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name,
                    "Can't read local variable in its own initializer.");
        }

        // Resolve the variable!
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    /**
     * Resolve any variables in the given statement.
     *
     * @param stmt the statement to resolve variables within
     */
    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    /**
     * Resolve any variables in the given expression.
     *
     * @param expr the expression to resolve variables within
     */
    private void resolve(Expr expr) {
        expr.accept(this);
    }

    /**
     * Resolve the given function statement.
     *
     * This differs from the interpreter in that the resolver immediately
     * traverses the function body, as part of static analysis, whereas
     * at runtime, the interpreter does not do so until the function is called.
     *
     * @param function the function statement to resolve
     * @param type the type of the function being resolved
     */
    private void resolveFunction(
            Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        // Since we are entering the body of a function, we need to
        // introduce the body's scope.
        beginScope();

        // In this new scope, we need to bind variables for each argument.
        for (Token param : function.params) {
            // Parameters don't have initializers in Lox.
            declare(param);
            define(param);
        }

        // Now we can resolve the function body in our new scope.
        resolve(function.body);
        endScope();

        // Return the current function to the type of the outer "function"
        currentFunction = enclosingFunction;
    }

    /**
     * Enter a new scope.
     */
    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    /**
     * Exit a scope.
     */
    private void endScope() {
        scopes.pop();
    }

    /**
     * Declare the given token within the current innermost scope.
     * This marks it as unready for use, as its initializer hasn't yet
     * been resolved.
     *
     * @param name the token to declare
     */
    private void declare(Token name) {
        // We are in the global scope, no need to track this variable for
        // future resolution because any references can just be assumed to be
        // global.
        if (scopes.isEmpty()) return;

        Map<String, Boolean> scope = scopes.peek();

        // Declaring multiple variables with the same name in a local scope
        // is likely a mistake.
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name,
                    "Already a variable with this name in this scope.");
        }

        // Declare the variable, specifying `false` meaning that
        // its initializer hasn't been resolved yet.
        scope.put(name.lexeme, false);
    }

    /**
     * Define the given token within the current innermost scope.
     * This marks it as ready for use.
     *
     * @param name the token to define
     */
    private void define(Token name) {
        // We are in the global scope.
        if (scopes.isEmpty()) return;

        // Define the variable, specifying `true` meaning that
        // its initializer has been resolved and this variable can be
        // used in resolution.
        scopes.peek().put(name.lexeme, true);
    }

    /**
     * Resolve the given variable expression with the given name.
     *
     * @param expr the variable expression to resolve
     * @param name the token containing the variable's name
     */
    private void resolveLocal(Expr expr, Token name) {
        // Iterate through the scopes from innermost to outermost
        // searching for the variable.
        for (int i = scopes.size() - 1; i >= 0; i--) {
            // If the variable is found, then calculate the number of hops
            // to reach it and communicate this to the interpreter where
            // this will be stored.
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // Here we both declare and define the function BEFORE its
        // body is resolved, in contrast to when we are declaring variables.
        // This is because a function should be able to reference itself
        // in its body, otherwise recursion would be a compilation error.
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        // Check for top-level return statements.
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }

        if (stmt.value != null) {
            resolve(stmt.value);
        }

        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }
}