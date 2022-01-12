package com.craftinginterpreters.lox;

import java.util.List;

interface LoxCallable {
    /**
     * Get the arity (number of arguments) of this callable.
     *
     * @return the callable's arity
     */
    int arity();

    /**
     * Call this callable and return the result.
     *
     * @param interpreter the interpreter being used to execute code
     * @param arguments the arguments to the callable
     * @return the result of the call
     */
    Object call(Interpreter interpreter, List<Object> arguments);
}