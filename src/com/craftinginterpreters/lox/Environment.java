package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    /**
     * A map to store bindings from variable names to their values.
     */
    private final Map<String, Object> values = new HashMap<>();

    /**
     * Retrieve the value of a given identifier Token if it exists,
     * otherwise raise an error.
     *
     * @param name the identifier token to retrieve a value for
     * @return the value of the variable if it exists
     */
    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    /**
     * Assign the given value to the given identifier Token if it exists,
     * otherwise raise an error.
     *
     * @param name the name of the variable
     * @param value the value of the variable
     */
    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    /**
     * Define a binding from the given name to the given value.
     *
     * @param name the name of the variable
     * @param value the value of the variable
     */
    void define(String name, Object value) {
        values.put(name, value);
    }
}