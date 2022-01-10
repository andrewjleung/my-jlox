package com.craftinginterpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        // Only a single argument is expected, that being the output directory
        // of our generated code.
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }

        String outputDir = args[0];

        // Define the AST for expressions within the Expr.java file with the given types.
        // These types are formatted as "<name> : <field1-type> <field1-name> ...
        defineAst(outputDir, "Expr", Arrays.asList(
                "Binary   : Expr left, Token operator, Expr right",
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Unary    : Token operator, Expr right"
        ));

        // Define the AST for statements within the Stmt.java file with the given types.
        defineAst(outputDir, "Stmt", Arrays.asList(
                "Expression : Expr expression",
                "Print      : Expr expression"
        ));
    }

    /**
     * Generate Java code for defining AST classes in the form of a
     * base class and specified behavior-less subclasses.
     *
     * @param outputDir the directory path to output the generated class definitions
     * @param baseName the name of the base class
     * @param types descriptions of each type
     * @throws IOException
     */
    private static void defineAst(
            String outputDir, String baseName, List<String> types)
            throws IOException {
        // Define the path to the new file.
        String path = outputDir + "/" + baseName + ".java";

        PrintWriter writer = new PrintWriter(path, "UTF-8");

        // Write the Java file defining AST classes.
        writer.println("package com.craftinginterpreters.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");

        defineVisitor(writer, baseName, types);

        // The AST classes.
        // Iterate through all the types and generate them one by one.
        for (String type : types) {
            // Parse the class name and fields from class description string.
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();

            defineType(writer, baseName, className, fields);
        }

        // The base accept() method.
        writer.println();
        writer.println("  abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");
        writer.close();
    }

    /**
     * Generate Java code for defining a Visitor interface.
     *
     * @param writer the PrintWriter to write the code to
     * @param baseName the name of the base class of the AST class
     * @param types descriptions of each type
     */
    private static void defineVisitor(
            PrintWriter writer, String baseName, List<String> types) {
        writer.println("  interface Visitor<R> {");

        // Iterate through each subclass and declare a visit method.
        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" +
                    typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("  }");
    }

    /**
     * Generate Java code define a single AST class.
     *
     * @param writer the PrintWriter to write the code to
     * @param baseName the name of the base class of the AST class
     * @param className the name of the AST class
     * @param fieldList the comma-delimited list of fields within the AST class
     */
    private static void defineType(
            PrintWriter writer, String baseName,
            String className, String fieldList) {
        // The class header.
        writer.println("  static class " + className + " extends " +
                baseName + " {");

        // Constructor.
        writer.println("    " + className + "(" + fieldList + ") {");

        // Store parameters in fields.
        String[] fields = fieldList.split(", ");
        // Iterate through each field.
        for (String field : fields) {
            // Parse the field name from the field string.
            // Type is not needed as this is just initialization.
            String name = field.split(" ")[1];
            writer.println("      this." + name + " = " + name + ";");
        }

        writer.println("    }");

        // Visitor pattern.
        writer.println();
        writer.println("    @Override");
        writer.println("    <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" +
                className + baseName + "(this);");
        writer.println("    }");

        // Fields.
        writer.println();
        for (String field : fields) {
            writer.println("    final " + field + ";");
        }

        writer.println("  }");
    }

}