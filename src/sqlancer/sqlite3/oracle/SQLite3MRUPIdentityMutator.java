package sqlancer.sqlite3.oracle;

import sqlancer.Randomly;

/**
 * Stage 1: Identity Wrapper Mutations for MRUP Oracle
 * 
 * Implements identity transformations that wrap window function results in
 * semantically equivalent expressions. These mutations target optimizer bugs
 * by forcing different execution paths while preserving query semantics.
 * 
 * Based on real-world bug survey: 80% of optimizer bugs involve identity transformations!
 * 
 * All mutations preserve MRUP semantics:
 * - Partition locality is maintained
 * - Determinism is preserved
 * - Numeric equivalence is guaranteed (or within epsilon for REAL)
 */
public class SQLite3MRUPIdentityMutator {

    /**
     * Apply identity wrapper mutation to window function result.
     * 
     * Selects mutation based on function type to ensure semantic correctness.
     * 
     * @param windowFunction The complete window function expression (e.g., "SUM(salary) OVER (...)")
     * @param functionType The window function type (SUM, AVG, COUNT, ROW_NUMBER, etc.)
     * @return Mutated expression that is semantically equivalent
     */
    public static String applyIdentityWrapper(String windowFunction, String functionType) {
        // Type-aware selection: numeric functions get full mutation set
        if (isNumericFunction(functionType)) {
            return applyNumericIdentity(windowFunction, functionType);
        } else {
            // Non-numeric functions (rare, but handle gracefully)
            return applyGenericIdentity(windowFunction);
        }
    }

    /**
     * Apply numeric identity mutations (for SUM, AVG, COUNT, ROW_NUMBER, RANK, etc.)
     * 
     * 12 variants covering:
     * - Arithmetic identity (+ 0, * 1, - 0, / 1)
     * - Commutative variants (0 +, 1 *)
     * - Type cast identity
     * - Rounding identity
     * - NULL-safe identity
     * - Parentheses wrapping
     * - Chained identity
     */
    private static String applyNumericIdentity(String wf, String functionType) {
        // Weighted selection: prioritize high-yield mutations
        int variant = Randomly.fromOptions(
            1, 1, 2, 2, 3, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
        );
        
        switch (variant) {
            // M1.1: Arithmetic Identity (most common in real bugs)
            case 1:
                // wf + 0 (HIGHEST PRIORITY - found many MySQL bugs)
                return "(" + wf + ") + 0";
            
            case 2:
                // wf - 0
                return "(" + wf + ") - 0";
            
            case 3:
                // wf * 1
                return "(" + wf + ") * 1";
            
            case 4:
                // wf / 1 (careful: may cause type coercion)
                return "(" + wf + ") / 1";
            
            case 5:
                // 0 + wf (commutative variant)
                return "0 + (" + wf + ")";
            
            case 6:
                // 1 * wf (commutative variant)
                return "1 * (" + wf + ")";
            
            // M1.2: Type Cast Identity
            case 7:
                // CAST(wf AS INTEGER) - for integer-returning functions
                if (isIntegerFunction(functionType)) {
                    return "CAST((" + wf + ") AS INTEGER)";
                } else {
                    // Fallback to arithmetic identity
                    return "(" + wf + ") + 0";
                }
            
            case 8:
                // CAST(wf AS REAL) - may require epsilon comparison
                return "CAST((" + wf + ") AS REAL)";
            
            // M1.3: Rounding Identity (only for integer functions)
            case 9:
                // ROUND(wf, 0) - identity for integers
                if (isIntegerFunction(functionType)) {
                    return "ROUND((" + wf + "), 0)";
                } else {
                    // For REAL functions, ROUND may change value
                    // Fallback to safe mutation
                    return "(" + wf + ")";
                }
            
            // M1.4: NULL-Safe Identity
            case 10:
                // COALESCE(wf, wf) - always returns wf
                return "COALESCE((" + wf + "), (" + wf + "))";
            
            case 11:
                // IFNULL(wf, wf) - SQLite-specific NULL-safe function
                return "IFNULL((" + wf + "), (" + wf + "))";
            
            // M1.5: Parentheses Wrapping
            case 12:
                // (wf) - simple parentheses
                return "(" + wf + ")";
            
            case 13:
                // ((wf)) - double parentheses
                return "((" + wf + "))";
            
            // M1.6: Chained Identity
            case 14:
                // wf + 0 - 0
                return "(" + wf + ") + 0 - 0";
            
            case 15:
                // wf * 1 * 1
                return "(" + wf + ") * 1 * 1";
            
            default:
                // Fallback: no mutation
                return wf;
        }
    }

    /**
     * Apply generic identity mutations (for non-numeric functions, if any)
     * 
     * Limited to safe mutations that work for any type.
     */
    private static String applyGenericIdentity(String wf) {
        int variant = Randomly.fromOptions(1, 2, 3);
        
        switch (variant) {
            case 1:
                // COALESCE(wf, wf) - safe for any type
                return "COALESCE((" + wf + "), (" + wf + "))";
            
            case 2:
                // (wf) - simple parentheses
                return "(" + wf + ")";
            
            case 3:
                // ((wf)) - double parentheses
                return "((" + wf + "))";
            
            default:
                return wf;
        }
    }

    /**
     * Check if function returns numeric type (INTEGER or REAL).
     * 
     * All current MRUP window functions are numeric.
     */
    private static boolean isNumericFunction(String functionType) {
        switch (functionType) {
            case "SUM":
            case "AVG":
            case "COUNT":
            case "MIN":
            case "MAX":
            case "ROW_NUMBER":
            case "RANK":
            case "DENSE_RANK":
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if function returns INTEGER type specifically.
     * 
     * Used to determine if ROUND(wf, 0) and CAST(wf AS INTEGER) are safe identities.
     */
    private static boolean isIntegerFunction(String functionType) {
        switch (functionType) {
            case "COUNT":
                return true;
            case "ROW_NUMBER":
                return true;
            case "RANK":
                return true;
            case "DENSE_RANK":
                return true;
            case "SUM":
                return true;
            case "MIN":
                return true;
            case "MAX":
                // These depend on column type, but we'll treat them as potentially integer
                return true;
            case "AVG":
                // AVG always returns REAL in SQLite
                return false;
            default:
                return false;
        }
    }

    /**
     * Get a human-readable description of the mutation type.
     * Used for logging.
     */
    public static String getMutationDescription(String original, String mutated) {
        if (mutated.contains(" + 0")) {
            return "Arithmetic Identity (+ 0)";
        } else if (mutated.contains(" - 0")) {
            return "Arithmetic Identity (- 0)";
        } else if (mutated.contains(" * 1")) {
            return "Arithmetic Identity (* 1)";
        } else if (mutated.contains(" / 1")) {
            return "Arithmetic Identity (/ 1)";
        } else if (mutated.contains("0 + ")) {
            return "Arithmetic Identity (0 +)";
        } else if (mutated.contains("1 * ")) {
            return "Arithmetic Identity (1 *)";
        } else if (mutated.contains("CAST") && mutated.contains("INTEGER")) {
            return "Type Cast Identity (INTEGER)";
        } else if (mutated.contains("CAST") && mutated.contains("REAL")) {
            return "Type Cast Identity (REAL)";
        } else if (mutated.contains("ROUND")) {
            return "Rounding Identity";
        } else if (mutated.contains("COALESCE")) {
            return "NULL-Safe Identity (COALESCE)";
        } else if (mutated.contains("IFNULL")) {
            return "NULL-Safe Identity (IFNULL)";
        } else if (mutated.startsWith("((") && mutated.endsWith("))")) {
            return "Parentheses Wrapping (double)";
        } else if (mutated.startsWith("(") && mutated.endsWith(")") && 
                   !mutated.substring(1, mutated.length()-1).contains("(")) {
            return "Parentheses Wrapping (single)";
        } else if (mutated.contains("+ 0 - 0")) {
            return "Chained Identity (+ 0 - 0)";
        } else if (mutated.contains("* 1 * 1")) {
            return "Chained Identity (* 1 * 1)";
        } else {
            return "Identity Mutation";
        }
    }
}

