package com.craftinginterpreters.lox;

class Return extends RuntimeException {
    /**
     * The value being returned.
     */
    final Object value;

    Return(Object value) {
        // This is to disable unnecessary JVM machinery like stack traces
        // since this exception is being used for control flow rather than
        // for an error.
        super(null, null, false, false);
        this.value = value;
    }
}
