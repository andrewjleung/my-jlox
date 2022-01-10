package com.craftinginterpreters.lox;

import java.util.List;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    /**
     * The environment of bindings.
     */
    private Environment environment = new Environment();

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

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
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
    public Object visitAssignExpr(Expr.Assign expr) {
        // Evaluate the assignment r-value.
        Object value = evaluate(expr.value);

        // Assign it to a (hopefully) existing variable in the environment.
        environment.assign(expr.name, value);
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
    public Object visitGroupingExpr(Expr.Grouping expr) {
        // Simply evaluate the nested expression.
        return evaluate(expr);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        // A literal contains its value which was determined during scanning.
        return expr.value;
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
        return environment.get(expr.name);
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