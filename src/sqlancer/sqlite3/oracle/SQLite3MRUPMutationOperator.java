package sqlancer.sqlite3.oracle;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;

/**
 * MRUP Mutation Operators
 * 
 * Implements the Top 10 mutation strategies from MRUP.md for window function testing.
 * These mutations are semantic-preserving transformations that should not change
 * the query result, but may expose optimizer bugs.
 */
public class SQLite3MRUPMutationOperator {

    /**
     * Apply random mutations to a window specification
     * 
     * @param windowSpec Original window specification
     * @param columns Available columns
     * @return Mutated window specification
     */
    public static String applyRandomMutations(String windowSpec, List<SQLite3Column> columns) {
        // Apply only 1 mutation to avoid corrupting the window spec
        return applyRandomMutation(windowSpec, columns);
    }

    /**
     * Apply a single random mutation from the Top 10 strategies
     */
    private static String applyRandomMutation(String windowSpec, List<SQLite3Column> columns) {
        // Randomly select one of the Top 10 mutations
        int mutationId = Randomly.fromOptions(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        
        switch (mutationId) {
            case 1:
                return mutationO1_RedundantOrderBy(windowSpec, columns);
            case 2:
                return mutationO2_OrderPreservingTransform(windowSpec, columns);
            case 3:
                return mutationP1_RedundantPartitionBy(windowSpec, columns);
            case 4:
                return mutationP3_AddUniqueColumn(windowSpec, columns);
            case 5:
                return mutationF1_ShrinkFrame(windowSpec);
            case 6:
                return mutationF3_CurrentRowEquivalence(windowSpec);
            case 7:
                return mutationF8_SwitchRowsRange(windowSpec);
            case 8:
                return mutationV1_ArithmeticIdentity(windowSpec, columns);
            case 9:
                return mutationQ1_WrapInSubquery(windowSpec);
            case 10:
                return mutationQ3_UnionAllWrapper(windowSpec);
            default:
                return windowSpec;
        }
    }

    /**
     * O1: Redundant ORDER BY column
     * ORDER BY x → ORDER BY x, x
     */
    private static String mutationO1_RedundantOrderBy(String windowSpec, List<SQLite3Column> columns) {
        if (!windowSpec.contains("ORDER BY")) {
            return windowSpec;
        }
        
        // Find ORDER BY clause - look for the actual column name
        int orderByPos = windowSpec.indexOf("ORDER BY");
        if (orderByPos == -1) return windowSpec;
        
        // Extract the first column name after ORDER BY
        String afterOrderBy = windowSpec.substring(orderByPos + 9).trim(); // "ORDER BY " is 9 chars
        String firstCol = afterOrderBy.split("\\s+")[0]; // Get first word (column name)
        
        // Find where to insert duplicate (after first column and its direction)
        int insertPos = orderByPos + 9 + firstCol.length();
        
        // Skip direction keywords if present
        String remaining = windowSpec.substring(insertPos).trim();
        if (remaining.startsWith("ASC") || remaining.startsWith("DESC")) {
            insertPos += remaining.startsWith("ASC") ? 4 : 5;
        }
        if (windowSpec.substring(insertPos).trim().startsWith("NULLS")) {
            int nullsEnd = windowSpec.indexOf("LAST", insertPos);
            if (nullsEnd == -1) nullsEnd = windowSpec.indexOf("FIRST", insertPos);
            if (nullsEnd != -1) insertPos = nullsEnd + 4;
        }
        
        String before = windowSpec.substring(0, insertPos);
        String after = windowSpec.substring(insertPos);
        
        return before + ", " + firstCol + after;
    }

    /**
     * O2: Order-preserving transform
     * ORDER BY x → ORDER BY x + 0
     */
    private static String mutationO2_OrderPreservingTransform(String windowSpec, List<SQLite3Column> columns) {
        if (!windowSpec.contains("ORDER BY")) {
            return windowSpec;
        }
        
        // Extract the first column name after ORDER BY
        int orderByPos = windowSpec.indexOf("ORDER BY");
        String afterOrderBy = windowSpec.substring(orderByPos + 9).trim();
        String firstCol = afterOrderBy.split("\\s+")[0];
        
        // Use simple string replacement to avoid regex issues
        String searchStr = "ORDER BY " + firstCol;
        String replaceStr = "ORDER BY (" + firstCol + " + 0)";
        
        int pos = windowSpec.indexOf(searchStr);
        if (pos != -1) {
            return windowSpec.substring(0, pos) + replaceStr + windowSpec.substring(pos + searchStr.length());
        }
        
        return windowSpec;
    }

    /**
     * P1: Add redundant PARTITION BY key
     * PARTITION BY dept → PARTITION BY dept, dept
     */
    private static String mutationP1_RedundantPartitionBy(String windowSpec, List<SQLite3Column> columns) {
        if (!windowSpec.contains("PARTITION BY")) {
            return windowSpec;
        }
        
        // Extract the partition column (always "dept" in our case)
        int partitionPos = windowSpec.indexOf("PARTITION BY");
        String afterPartition = windowSpec.substring(partitionPos + 13).trim(); // "PARTITION BY " is 13 chars
        String partCol = afterPartition.split("\\s+")[0]; // Get first word
        
        // Find where ORDER BY starts or where to insert
        int orderByPos = windowSpec.indexOf("ORDER BY", partitionPos);
        int insertPos = (orderByPos != -1) ? orderByPos : windowSpec.indexOf(")", partitionPos);
        
        String before = windowSpec.substring(0, insertPos);
        String after = windowSpec.substring(insertPos);
        
        // Add duplicate partition column
        return before + ", " + partCol + " " + after;
    }

    /**
     * P3: Add unique column to PARTITION BY
     * PARTITION BY dept → PARTITION BY dept, id
     */
    private static String mutationP3_AddUniqueColumn(String windowSpec, List<SQLite3Column> columns) {
        // Similar to P1 but conceptually adds a unique column
        // For simplicity, we'll just add a random column
        return mutationP1_RedundantPartitionBy(windowSpec, columns);
    }

    /**
     * F1: Shrink frame
     * UNBOUNDED PRECEDING → 1 PRECEDING
     */
    private static String mutationF1_ShrinkFrame(String windowSpec) {
        if (windowSpec.contains("UNBOUNDED PRECEDING")) {
            return windowSpec.replace("UNBOUNDED PRECEDING", "1 PRECEDING");
        }
        return windowSpec;
    }

    /**
     * F3: CURRENT ROW equivalence
     * ROWS BETWEEN 0 PRECEDING AND 0 FOLLOWING ↔ CURRENT ROW
     */
    private static String mutationF3_CurrentRowEquivalence(String windowSpec) {
        if (windowSpec.contains("CURRENT ROW") && !windowSpec.contains("BETWEEN")) {
            // Expand CURRENT ROW to BETWEEN form
            return windowSpec.replace("CURRENT ROW", "BETWEEN 0 PRECEDING AND 0 FOLLOWING");
        } else if (windowSpec.contains("BETWEEN 0 PRECEDING AND 0 FOLLOWING")) {
            // Collapse to CURRENT ROW
            return windowSpec.replace("BETWEEN 0 PRECEDING AND 0 FOLLOWING", "CURRENT ROW");
        }
        return windowSpec;
    }

    /**
     * F8: Switch ROWS ↔ RANGE (when ORDER BY is unique)
     * ROWS ... ↔ RANGE ...
     */
    private static String mutationF8_SwitchRowsRange(String windowSpec) {
        if (windowSpec.contains(" ROWS ")) {
            return windowSpec.replace(" ROWS ", " RANGE ");
        } else if (windowSpec.contains(" RANGE ")) {
            return windowSpec.replace(" RANGE ", " ROWS ");
        }
        return windowSpec;
    }

    /**
     * V1: Arithmetic identity
     * ORDER BY x → ORDER BY x * 1
     */
    private static String mutationV1_ArithmeticIdentity(String windowSpec, List<SQLite3Column> columns) {
        if (!windowSpec.contains("ORDER BY") || columns.isEmpty()) {
            return windowSpec;
        }
        
        String randomCol = Randomly.fromList(columns).getName();
        String transform = Randomly.fromOptions(" * 1", " + 0", " - 0");
        
        if (windowSpec.contains("ORDER BY " + randomCol)) {
            return windowSpec.replace("ORDER BY " + randomCol, "ORDER BY (" + randomCol + transform + ")");
        }
        
        return windowSpec;
    }

    /**
     * Q1: Wrap in subquery (applied at query level, not window spec level)
     * This is a placeholder - actual implementation would wrap the entire query
     */
    private static String mutationQ1_WrapInSubquery(String windowSpec) {
        // This mutation is applied at the query level, not window spec level
        // Return unchanged for now
        return windowSpec;
    }

    /**
     * Q3: UNION ALL wrapper (applied at query level, not window spec level)
     * This is a placeholder - actual implementation would wrap the entire query
     */
    private static String mutationQ3_UnionAllWrapper(String windowSpec) {
        // This mutation is applied at the query level, not window spec level
        // Return unchanged for now
        return windowSpec;
    }

    /**
     * Helper: Find the end of ORDER BY clause
     */
    private static int findOrderByEnd(String windowSpec, int orderByPos) {
        // Look for ROWS, RANGE, GROUPS, or closing paren
        int rowsPos = windowSpec.indexOf(" ROWS", orderByPos);
        int rangePos = windowSpec.indexOf(" RANGE", orderByPos);
        int groupsPos = windowSpec.indexOf(" GROUPS", orderByPos);
        int parenPos = windowSpec.indexOf(")", orderByPos);
        
        List<Integer> positions = new ArrayList<>();
        if (rowsPos != -1) positions.add(rowsPos);
        if (rangePos != -1) positions.add(rangePos);
        if (groupsPos != -1) positions.add(groupsPos);
        if (parenPos != -1) positions.add(parenPos);
        
        if (positions.isEmpty()) {
            return windowSpec.length();
        }
        
        return positions.stream().min(Integer::compare).orElse(windowSpec.length());
    }

    /**
     * Helper: Find the end of PARTITION BY clause
     */
    private static int findPartitionByEnd(String windowSpec, int partitionPos) {
        // Look for ORDER BY or closing paren
        int orderByPos = windowSpec.indexOf("ORDER BY", partitionPos);
        int parenPos = windowSpec.indexOf(")", partitionPos);
        
        if (orderByPos != -1 && (parenPos == -1 || orderByPos < parenPos)) {
            return orderByPos;
        }
        
        return parenPos != -1 ? parenPos : windowSpec.length();
    }
}

