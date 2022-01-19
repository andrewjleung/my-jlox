package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Note that this interpreter is tightly-coupled with and heavily
// relies upon semantic analysis done by the Resolver class beforehand.
// TODO: write assertions to enforce the contract this interpreter expects
//       to be upheld by the resolver
class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    /**
     * The global environment.
     */
    final Environment globals = new Environment();

    /**
     * This interpreter's current environment.
     */
    private Environment environment = globals;

    /**
     * A map from nodes to the depth of their declarations.
     *
     * These values are found and populated within this map via semantic
     * analysis done within the `Resolver` class, and are used to maintain
     * consistency when resolving variables at runtime adhering to Lox's
     * principle of static scope.
     */
    private final Map<Expr, Integer> locals = new HashMap<>();

    Interpreter() {
        // Add the `clock` native function to the global environment.
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() { return 0; }

            @Override
            public Object call(Interpreter interpreter,
                               List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() { return "<native fn>"; }
        });
    }

    /**
     * Interpret the given list of statements.
     *
     * @param statements the list of statements ot interpret
     */
    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    /**
     * Evaluate the given expression.
     *
     * @param expr the expression to evaluate
     * @return the value of the given expression
     */
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    /**
     * Execute the given statement.
     *
     * @param stmt the statement AST to execute
     */
    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    /**
     * Resolve the given expression with the given depth of scope.
     *
     * @param expr the expression to resolve
     * @param depth how many scopes there are between the current
     *              scope and the scope where this variable is defined
     */
    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    /**
     * Execute the given block of statements using the given Environment
     * as this block's environment.
     *
     * TODO: refactor the use of environments to not mutate an environment field
     *       but instead explicitly pass the environment as a parameter to each
     *       visit method, passing a different method when necessary.
     *
     * @param statements the statements within the block
     * @param environment the block's environment
     */
    void executeBlock(List<Stmt> statements,
                      Environment environment) {
        // Save a reference to the outer environment to return to after
        // exiting the block.
        Environment previous = this.environment;

        try {
            // Point this Interpreter to the block's environment.
            this.environment = environment;

            // Execute each statement within the block.
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            // Unwind to the outer environment now that the block is
            // done executing.
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // Create a callable `LoxFunction` from the Function node.
        // This provides the function with the current environment as
        // its closure.
        LoxFunction function = new LoxFunction(stmt, environment);

        // Bind the function's name to its callable representation
        // in the current environment.
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        // Evaluate our return value to be given to the Return exception.
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        // Throw a `Return` exception to unwind the call stack back to the
        // `LoxCallable` `call()` method which is invoking the current function.
        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        // Set the value of the variable if an initializer is present.
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        // Define a new binding within our environment from the variable
        // name to its value.
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        // Evaluate the assignment r-value.
        Object value = evaluate(expr.value);

        // Lookup the resolved depth for this variable within the locals map.
        Integer distance = locals.get(expr);

        if (distance != null) {
            // Assign the variable at its location in the environment chain.
            environment.assignAt(distance, expr.name, value);
        } else {
            // If there is no distance stored from semantic analysis,
            // The variable must be either global or was not declared.
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        // Note here that both operators are evaluated before checking either type.
        // An alternative would be to evaluate and type check a single side before
        // moving onto the other.
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                // Addition.
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }

                // Concatenation.
                // TODO: if either operand is a string, convert the other to a string and concatenate.
                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }

                // Types are already checked before since PLUS can either be
                // used for addition or concatenation.
                // All we need to check for is if neither success cases match.
                throw new RuntimeError(expr.operator,
                        "Operands must be two numbers or two strings.");
            case SLASH:
                // TODO: handle division by zero.
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        // Evaluate the callee and arguments expressions.
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        // Check that our callee is actually callable.
        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren,
                    "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;

        // Validate the arity of the function against the number of args.
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                    function.arity() + " arguments but got " +
                    arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        // Simply evaluate the nested expression.
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        // A literal contains its value which was determined during scanning.
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        // Evaluate the left expression.
        Object left = evaluate(expr.left);

        // Short circuit depending on the logical operator and the
        // value of the left expression.
        if (expr.operator.type == TokenType.OR) {
            // The operator is `or` and the left expression is truthy.
            // We don't need to evaluate the right so short circuit.
            if (isTruthy(left)) return left;
        } else {
            // The operator is `and` and the left expression is falsey.
            // We don't need to evaluate the right so short circuit.
            if (!isTruthy(left)) return left;
        }

        // We didn't short circuit.
        // The entire expression simplifies to whatever the right evaluates to.
        return evaluate(expr.right);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        // First, evaluate the subtree.
        // This is post-order traversal of the syntax tree.
        Object right = evaluate(expr.right);

        // Evaluate the subtree with the unary operator depending on
        // what unary operator it is.
        switch (expr.operator.type) {
            case BANG:
                // The operand of a logical not operator may be not a Boolean.
                // Because of this, we need to first coerce the object we have
                // into a Boolean using predefined truthy / falsey rules before
                // negating it.
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        // TODO: make it a runtime error to access an unassigned (nil) variable.
        return lookUpVariable(expr.name, expr);
    }

    /**
     * Lookup the given variable expression in the environment chain
     * by traversing its declaration's resolved depth within the chain.
     *
     * @param name the variable expression's name
     * @param expr the variable expression
     * @return
     */
    private Object lookUpVariable(Token name, Expr expr) {
        // Lookup the resolved depth for this variable within the locals map.
        Integer distance = locals.get(expr);

        if (distance != null) {
            // Retrieve the variable's value from the environment.
            return environment.getAt(distance, name.lexeme);
        } else {
            // If there is no distance stored from semantic analysis,
            // The variable must be either global or was not declared.
            return globals.get(name);
        }
    }

    /**
     * Validate that the given operand is a number.
     *
     * @param operator the operator token operating on the operand
     * @param operand the operand to check
     */
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    /**
     * Validate that the given left and right operands are both numbers.
     *
     * @param operator the operator token operating on the operands
     * @param left the left operand to check
     * @param right the right operand to check
     */
    private void checkNumberOperands(Token operator,
                                     Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    /**
     * Determine whether the given object is considered truthy.
     *
     * These rules are chosen to match those within Ruby which are:
     * - false and nil are falsey
     * - everything else is truthy
     *
     * @param object the object to check
     * @return whether the given object is truthy
     */
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    /**
     * Determine if the given two objects are considered equal in Lox.
     *
     * @param a the first object
     * @param b the second object
     * @return whether the first and second objects are considered equal
     */
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        // Lox's concept of equality will be the same as Java's.
        // Luckily, this means that like Java, Lox does not do implicit conversions.
        return a.equals(b);
    }

    /**
     * Convert the given Lox value into a string.
     *
     * @param object the Lox value
     * @return the stringified value
     */
    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();

            // Remove the decimal in the case of an integer.
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }
}