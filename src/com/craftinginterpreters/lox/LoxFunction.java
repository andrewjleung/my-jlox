package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    /**
     * The parsed `Function` node representing this function.
     */
    private final Stmt.Function declaration;

    LoxFunction(Stmt.Function declaration) {
        this.declaration = declaration;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        // Create this function's environment.
        // When executing, beyond its own arguments, the function only
        // has access to the global environment.
        // This is regardless of whatever scope it's being called in.
        Environment environment = new Environment(interpreter.globals);

        // Bind each of the given arguments to the function's parameters
        // within its environment.
        // We are safe to assume that both the arguments and params lists
        // are of the same length since arity is checked before this method
        // is invoked in `visitCallExpr()`.
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        // Tell the interpreter to execute the function's body with its
        // environment.
        // The environment will return to the callsite afterwards.
        // This is wrapped in a try/catch to catch and handle any return statements.
        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            // There was a return statement in the function.
            // Return its value.
            return returnValue.value;
        }

        // No return values for now.
        return null;
    }
}
