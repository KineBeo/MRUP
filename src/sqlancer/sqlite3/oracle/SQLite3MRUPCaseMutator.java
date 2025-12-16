package sqlancer.sqlite3.oracle;

import java.util.List;

import sqlancer.Randomly;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;

/**
 * Phase 3: CASE WHEN Mutations for MRUP Oracle
 * 
 * Implements CASE WHEN mutations based on proven bug-finding patterns from MySQL EET oracle.
 * All mutations preserve MRUP semantics (partition-local, deterministic).
 */
public class SQLite3MRUPCaseMutator {

    /**
     * Phase 3.0: Constant Condition with Dead Branch
     * 
     * Patterns like "CASE WHEN TRUE THEN NULL ELSE wf END" found real bugs in MySQL.
     * Forces optimizer to handle dead branch elimination and constant folding.
     */
    public static String applyConstantCondition(String windowFunction) {
        int variant = Randomly.fromOptions(1, 2, 3, 4, 5, 6);
        
        switch (variant) {
            case 1:
                // Always true -> NULL (tests type inference with NULL)
                return "CASE WHEN 1 = 1 THEN NULL ELSE " + windowFunction + " END";
            
            case 2:
                // Always false -> window function (tests dead branch elimination)
                return "CASE WHEN 1 = 0 THEN NULL ELSE " + windowFunction + " END";
            
            case 3:
                // TRUE literal -> NULL
                return "CASE WHEN TRUE THEN NULL ELSE " + windowFunction + " END";
            
            case 4:
                // FALSE literal -> window function
                return "CASE WHEN FALSE THEN NULL ELSE " + windowFunction + " END";
            
            case 5:
                // Always true with 0 (tests type inference with integer)
                return "CASE WHEN 1 = 1 THEN 0 ELSE " + windowFunction + " END";
            
            case 6:
                // Always false, dead branch has window function
                return "CASE WHEN 1 = 0 THEN " + windowFunction + " ELSE NULL END";
            
            default:
                return windowFunction;
        }
    }

    /**
     * Phase 3.1: Window Function in WHEN Condition
     * 
     * Tests window function evaluation in WHEN condition.
     * Pattern from MySQL bug: WHEN (wf IN (SELECT ...)) THEN ...
     */
    public static String applyWindowFunctionCondition(String windowFunction) {
        int variant = Randomly.fromOptions(1, 2, 3, 4, 5);
        
        switch (variant) {
            case 1:
                // Simple comparison
                int threshold = Randomly.fromOptions(1, 2, 3, 5, 10);
                return "CASE WHEN " + windowFunction + " <= " + threshold + 
                       " THEN 'TOP' ELSE 'OTHER' END";
            
            case 2:
                // IN clause
                return "CASE WHEN " + windowFunction + " IN (1, 2, 3) " +
                       "THEN 'TOP_3' ELSE 'OTHER' END";
            
            case 3:
                // IS NULL check
                return "CASE WHEN " + windowFunction + " IS NULL " +
                       "THEN 'NULL_RESULT' ELSE 'HAS_VALUE' END";
            
            case 4:
                // BETWEEN
                return "CASE WHEN " + windowFunction + " BETWEEN 1 AND 5 " +
                       "THEN 'TOP_5' ELSE 'OTHER' END";
            
            case 5:
                // Greater than with string result
                return "CASE WHEN " + windowFunction + " > 0 " +
                       "THEN 'POSITIVE' ELSE 'NON_POSITIVE' END";
            
            default:
                return windowFunction;
        }
    }

    /**
     * Phase 3.2: Different Window Functions per Branch
     * 
     * Forces optimizer to handle multiple window functions.
     * Tests type mismatch between branches.
     */
    public static String applyDifferentWindowFunctions(
            String functionType,
            String windowSpec,
            List<SQLite3Column> columns,
            String condition) {
        
        // Pick a random column for window functions
        SQLite3Column col = Randomly.fromList(columns);
        
        // Generate two different window functions
        String wf1, wf2;
        
        int variant = Randomly.fromOptions(1, 2, 3);
        
        switch (variant) {
            case 1:
                // Different aggregate functions
                wf1 = "SUM(" + col.getName() + ") " + windowSpec;
                wf2 = "COUNT(*) " + windowSpec;
                break;
            
            case 2:
                // Different ranking functions
                wf1 = "ROW_NUMBER() " + windowSpec;
                wf2 = "RANK() " + windowSpec;
                break;
            
            case 3:
                // Aggregate vs ranking
                wf1 = "AVG(" + col.getName() + ") " + windowSpec;
                wf2 = "DENSE_RANK() " + windowSpec;
                break;
            
            default:
                wf1 = "COUNT(*) " + windowSpec;
                wf2 = "ROW_NUMBER() " + windowSpec;
        }
        
        return "CASE WHEN " + condition + " THEN " + wf1 + " ELSE " + wf2 + " END";
    }

    /**
     * Phase 3.3: Identical THEN/ELSE Branches
     * 
     * From MySQL bug: THEN ref_0.c20 ELSE ref_0.c20
     * Tests common subexpression elimination.
     */
    public static String applyIdenticalBranches(String expression, String condition) {
        return "CASE WHEN " + condition + " THEN " + expression + " ELSE " + expression + " END";
    }

    /**
     * Phase 3.4: NULL Handling
     * 
     * Tests NULL propagation through CASE.
     */
    public static String applyNullHandling(String windowFunction, String column) {
        int variant = Randomly.fromOptions(1, 2, 3);
        
        switch (variant) {
            case 1:
                // NULL -> NULL
                return "CASE WHEN " + column + " IS NULL THEN NULL ELSE " + windowFunction + " END";
            
            case 2:
                // NULL -> 0
                return "CASE WHEN " + column + " IS NULL THEN 0 ELSE " + windowFunction + " END";
            
            case 3:
                // NOT NULL check
                return "CASE WHEN " + column + " IS NOT NULL THEN " + windowFunction + " ELSE 0 END";
            
            default:
                return windowFunction;
        }
    }

    /**
     * Generate a partition-local condition for CASE WHEN
     */
    public static String generatePartitionLocalCondition(List<SQLite3Column> columns) {
        SQLite3Column col = Randomly.fromList(columns);
        String colName = col.getName();
        
        int variant = Randomly.fromOptions(1, 2, 3, 4, 5);
        
        switch (variant) {
            case 1:
                // Simple comparison with constant
                int value = Randomly.fromOptions(30, 40, 50, 60000, 70000);
                return colName + " > " + value;
            
            case 2:
                // IS NULL
                return colName + " IS NULL";
            
            case 3:
                // IS NOT NULL
                return colName + " IS NOT NULL";
            
            case 4:
                // BETWEEN
                return colName + " BETWEEN 30 AND 50";
            
            case 5:
                // Complex AND condition
                SQLite3Column col2 = Randomly.fromList(columns);
                return colName + " > 40 AND " + col2.getName() + " IS NOT NULL";
            
            default:
                return colName + " > 50";
        }
    }
}

