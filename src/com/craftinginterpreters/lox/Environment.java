package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
    /**
     * A reference to the environment of the enclosing scope of this environment.
     */
    final Environment enclosing;

    /**
     * A map to store bindings from variable names to their values.
     */
    private final Map<String, Object> values = new HashMap<>();

    /**
     * Construct the top-level environment.
     */
    Environment() {
        enclosing = null;
    }

    /**
     * Construct a local-scope environment.
     *
     * @param enclosing the environment of the enclosing scope
     */
    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    /**
     * Retrieve the value of a given identifier Token if it exists,
     * otherwise raise an error.
     *
     * @param name the identifier token to retrieve a value for
     * @return the value of the variable if it exists
     */
    Object get(Token name) {
        // Check this environment for the variable.
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        // Check the enclosing environment for the variable.
        // If it recursively reaches the outermost environment and doesn't
        // find the key, then the below error will be thrown.
        if (enclosing != null) return enclosing.get(name);

        // This will only ever be thrown in the outermost environment.
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
        // Check this environment for the variable to reassign.
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        // Check the enclosing environment for the variable to reassign.
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        // This will only ever be thrown in the outermost environment.
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

    /**
     * Retrieve the given variable name from an environment which is
     * a resolved distance away from the current environment back in the
     * environment chain.
     *
     * @param distance the distance from the current environment where the
     *                 variable declaration is located
     * @param name the name of the variable being searched for
     * @return the value of the variable
     */
    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    /**
     * Assign the given value to the given variable name at its location
     * a given resolved distance away from the current environment back in
     * in the environment chain.
     *
     * @param distance the distance from the current environment where the
     *                 variable declaration is located
     * @param name the name of the variable to be assigned
     * @param value the value to assign to the variable
     */
    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }


    /**
     * Retrieve the environment a given distance away from the current
     * environment.
     *
     * @param distance the distance from the current environment of the
     *                 desired ancestor to retrieve
     * @return the ancestor environment specified
     */
    Environment ancestor(int distance) {
        Environment environment = this;

        // Walk environments up until the specified distance.
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }
}