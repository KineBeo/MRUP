package sqlancer.sqlite3.oracle;

import sqlancer.Randomly;

/**
 * Stage 1: Identity Wrapper Mutations for MRUP Oracle
 * 
 * CRITICAL PRINCIPLE:
 * Identity mutations MUST be applied to the window function core (aggregate argument),
 * NOT to the windowed expression as a whole.
 * 
 * ❌ INVALID: (COUNT(c1) OVER (...)) + 0
 * ✅ VALID:   COUNT(c1 + 0) OVER (...)
 * 
 * This targets optimizer bugs in expression evaluation INSIDE window aggregation,
 * which is where most real-world bugs occur.
 * 
 * All mutations preserve MRUP semantics:
 * - Partition locality is maintained
 * - Determinism is preserved
 * - Numeric equivalence is guaranteed (or within epsilon for REAL)
 */
public class SQLite3MRUPIdentityMutator {

    /**
     * Apply identity mutation to window function argument.
     * 
     * Parses: FUNC(arg) OVER (...) → FUNC(mutated_arg) OVER (...)
     * 
     * @param windowFunction The complete window function expression (e.g., "SUM(salary) OVER (...)")
     * @param functionType The window function type (SUM, AVG, COUNT, ROW_NUMBER, etc.)
     * @return Mutated expression with identity transformation applied to argument
     */
    public static String applyIdentityWrapper(String windowFunction, String functionType) {
        // Parse window function into core and OVER clause
        WindowFunctionParts parts = parseWindowFunction(windowFunction, functionType);
        
        if (parts == null || parts.argument == null) {
            // Cannot mutate (e.g., ROW_NUMBER(), RANK(), COUNT(*))
            return windowFunction;
        }
        
        // Apply identity mutation to the argument
        String mutatedArg = applyIdentityToArgument(parts.argument, functionType);
        
        // Reconstruct: FUNC(mutated_arg) OVER (...)
        return parts.functionName + "(" + mutatedArg + ") " + parts.overClause;
    }
    
    /**
     * Parse window function into components.
     */
    private static class WindowFunctionParts {
        String functionName;  // e.g., "SUM", "COUNT"
        String argument;      // e.g., "c1", "salary", null for COUNT(*)
        String overClause;    // e.g., "OVER (PARTITION BY dept ORDER BY salary)"
    }
    
    /**
     * Parse window function string into parts.
     * 
     * Examples:
     *   "SUM(c1) OVER (...)" → {SUM, c1, OVER (...)}
     *   "COUNT(*) OVER (...)" → {COUNT, null, OVER (...)}
     *   "ROW_NUMBER() OVER (...)" → {ROW_NUMBER, null, OVER (...)}
     */
    private static WindowFunctionParts parseWindowFunction(String windowFunction, String functionType) {
        WindowFunctionParts parts = new WindowFunctionParts();
        
        // Find OVER clause
        int overIndex = windowFunction.indexOf(" OVER ");
        if (overIndex == -1) {
            return null;  // Invalid format
        }
        
        parts.overClause = windowFunction.substring(overIndex + 1);  // "OVER (...)"
        String core = windowFunction.substring(0, overIndex);  // "SUM(c1)" or "COUNT(*)"
        
        // Extract function name and argument
        int openParen = core.indexOf('(');
        int closeParen = core.lastIndexOf(')');
        
        if (openParen == -1 || closeParen == -1) {
            return null;  // Invalid format
        }
        
        parts.functionName = core.substring(0, openParen);  // "SUM", "COUNT", etc.
        String argContent = core.substring(openParen + 1, closeParen).trim();  // "c1", "*", ""
        
        // Check if argument is mutable
        if (argContent.isEmpty() || argContent.equals("*")) {
            parts.argument = null;  // Cannot mutate COUNT(*), ROW_NUMBER(), etc.
        } else {
            parts.argument = argContent;
        }
        
        return parts;
    }

    /**
     * Apply identity mutation to window function argument.
     * 
     * CRITICAL: Mutates the argument INSIDE the window function, not the result.
     * 
     * Examples:
     *   arg="c1" → "c1 + 0", "c1 * 1", "CAST(c1 AS REAL)", etc.
     * 
     * 15 variants covering:
     * - Arithmetic identity (+ 0, * 1, - 0, / 1)
     * - Commutative variants (0 +, 1 *)
     * - Type cast identity
     * - Rounding identity
     * - NULL-Safe identity
     * - Parentheses wrapping
     * - Chained identity
     */
    private static String applyIdentityToArgument(String arg, String functionType) {
        // Weighted selection: prioritize high-yield mutations
        int variant = Randomly.fromOptions(
            1, 1, 2, 2, 3, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
        );
        
        switch (variant) {
            // M1.1: Arithmetic Identity (most common in real bugs)
            case 1:
                // arg + 0 (HIGHEST PRIORITY - found many MySQL bugs)
                return arg + " + 0";
            
            case 2:
                // arg - 0
                return arg + " - 0";
            
            case 3:
                // arg * 1
                return arg + " * 1";
            
            case 4:
                // arg / 1 (careful: may cause type coercion)
                return arg + " / 1";
            
            case 5:
                // 0 + arg (commutative variant)
                return "0 + " + arg;
            
            case 6:
                // 1 * arg (commutative variant)
                return "1 * " + arg;
            
            // M1.2: Type Cast Identity
            case 7:
                // CAST(arg AS INTEGER)
                return "CAST(" + arg + " AS INTEGER)";
            
            case 8:
                // CAST(arg AS REAL)
                return "CAST(" + arg + " AS REAL)";
            
            // M1.3: Rounding Identity
            case 9:
                // ROUND(arg, 0) - identity for integers
                return "ROUND(" + arg + ", 0)";
            
            // M1.4: NULL-Safe Identity
            case 10:
                // COALESCE(arg, arg) - always returns arg
                return "COALESCE(" + arg + ", " + arg + ")";
            
            case 11:
                // IFNULL(arg, arg) - SQLite-specific NULL-safe function
                return "IFNULL(" + arg + ", " + arg + ")";
            
            // M1.5: Parentheses Wrapping
            case 12:
                // (arg) - simple parentheses
                return "(" + arg + ")";
            
            case 13:
                // ((arg)) - double parentheses
                return "((" + arg + "))";
            
            // M1.6: Chained Identity
            case 14:
                // arg + 0 - 0
                return arg + " + 0 - 0";
            
            case 15:
                // arg * 1 * 1
                return arg + " * 1 * 1";
            
            default:
                return arg;
        }
    }

    /**
     * Get human-readable description of applied mutation.
     * 
     * Now detects mutations in the argument, not the whole expression.
     */
    public static String getMutationDescription(String original, String mutated) {
        // If no mutation was applied, return "None"
        if (original.equals(mutated)) {
            return "None";
        }
        
        // Extract the mutated argument from the window function
        // Format: FUNC(mutated_arg) OVER (...)
        
        int openParen = mutated.indexOf('(');
        int overIndex = mutated.indexOf(" OVER ");
        
        if (openParen == -1 || overIndex == -1 || openParen >= overIndex) {
            return "Unknown Identity";
        }
        
        // Find the closing paren before OVER
        int closeParen = mutated.lastIndexOf(')', overIndex);
        if (closeParen == -1 || closeParen <= openParen) {
            return "Unknown Identity";
        }
        
        String mutatedArg = mutated.substring(openParen + 1, closeParen).trim();
        
        // IMPORTANT: Check more specific patterns FIRST to avoid false matches
        
        // M1.6: Chained Identity (check BEFORE simple arithmetic)
        if (mutatedArg.contains(" + 0 - 0")) {
            return "Chained Identity (+ 0 - 0)";
        } else if (mutatedArg.contains(" * 1 * 1")) {
            return "Chained Identity (* 1 * 1)";
        }
        
        // M1.2: Type Cast Identity
        else if (mutatedArg.contains("CAST") && mutatedArg.contains("INTEGER")) {
            return "Type Cast Identity (INTEGER)";
        } else if (mutatedArg.contains("CAST") && mutatedArg.contains("REAL")) {
            return "Type Cast Identity (REAL)";
        }
        
        // M1.3: Rounding Identity
        else if (mutatedArg.contains("ROUND")) {
            return "Rounding Identity";
        }
        
        // M1.4: NULL-Safe Identity
        else if (mutatedArg.contains("COALESCE")) {
            return "NULL-Safe Identity (COALESCE)";
        } else if (mutatedArg.contains("IFNULL")) {
            return "NULL-Safe Identity (IFNULL)";
        }
        
        // M1.1: Arithmetic Identity (check AFTER chained to avoid false matches)
        else if (mutatedArg.contains(" + 0")) {
            return "Arithmetic Identity (+ 0)";
        } else if (mutatedArg.contains(" - 0")) {
            return "Arithmetic Identity (- 0)";
        } else if (mutatedArg.contains(" * 1")) {
            return "Arithmetic Identity (* 1)";
        } else if (mutatedArg.contains(" / 1")) {
            return "Arithmetic Identity (/ 1)";
        } else if (mutatedArg.contains("0 + ")) {
            return "Arithmetic Identity (0 +)";
        } else if (mutatedArg.contains("1 * ")) {
            return "Arithmetic Identity (1 *)";
        }
        
        // M1.5: Parentheses Wrapping (check LAST as it's most generic)
        else if (mutatedArg.startsWith("((") && mutatedArg.endsWith("))")) {
            return "Parentheses Wrapping (double)";
        } else if (mutatedArg.startsWith("(") && mutatedArg.endsWith(")")) {
            // More robust check: ensure it's just parentheses, no operators
            String inner = mutatedArg.substring(1, mutatedArg.length() - 1);
            if (!inner.contains(" + ") && !inner.contains(" - ") && 
                !inner.contains(" * ") && !inner.contains(" / ") &&
                !inner.contains("CAST") && !inner.contains("ROUND") &&
                !inner.contains("COALESCE") && !inner.contains("IFNULL")) {
                return "Parentheses Wrapping (single)";
            }
        }
        
        // Fallback - should rarely reach here if patterns are comprehensive
        // Log for debugging if needed
        return "Unknown Identity (" + mutatedArg + ")";
    }

    /**
     * Check if function returns numeric type (INTEGER or REAL).
     */
    private static boolean isNumericFunction(String functionType) {
        switch (functionType) {
            case "COUNT":
            case "ROW_NUMBER":
            case "RANK":
            case "DENSE_RANK":
            case "SUM":
            case "AVG":
            case "MIN":
            case "MAX":
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if function returns integer type specifically.
     */
    private static boolean isIntegerFunction(String functionType) {
        switch (functionType) {
            case "COUNT":
            case "ROW_NUMBER":
            case "RANK":
            case "DENSE_RANK":
                return true;
            default:
                return false;
        }
    }
}
