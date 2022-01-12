package com.craftinginterpreters.lox;

class AstPrinter implements Expr.Visitor<String> {
    /**
     * Print the given Expression.
     *
     * @param expr the expression
     * @return a string of the printed expression
     */
    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        // TODO: verify with text.
        return parenthesize("assign " + expr.name.lexeme, expr.value);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme,
                expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        // TODO: verify with text.
        return parenthesize(
                expr.callee.toString(),
                expr.arguments.toArray(new Expr[0]));
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        // TODO: verify with text.
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        // TODO: verify with text.
        return expr.name.toString();
    }

    /**
     * Print the given non-literal expression surrounded by parentheses
     * including any nested subexpressions.
     *
     * @param name the name of the expression
     * @param exprs the subexpressions
     * @return the string containing the printed and parenthesized expression
     */
    private String parenthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);

        // Iterate through every subexpression and use this visitor to print them.
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }

//    Printer testing.
//
//    public static void main(String[] args) {
//        Expr expression = new Expr.Binary(
//                new Expr.Unary(
//                        new Token(TokenType.MINUS, "-", null, 1),
//                        new Expr.Literal(123)),
//                new Token(TokenType.STAR, "*", null, 1),
//                new Expr.Grouping(
//                        new Expr.Literal(45.67)));
//
//        System.out.println(new AstPrinter().print(expression));
//    }
}
