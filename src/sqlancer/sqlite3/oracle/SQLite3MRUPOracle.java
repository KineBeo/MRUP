package sqlancer.sqlite3.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.gen.SQLite3MRUPTablePairGenerator;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;

/**
 * MRUP (MR-UNION-PARTITION) Oracle for testing window functions.
 * 
 * This oracle implements a metamorphic testing approach for window functions:
 * 1. Generate two base tables t1 and t2
 * 2. Create t_union = t1 UNION ALL t2
 * 3. Generate random window function query
 * 4. Execute window query on t1, t2, and t_union
 * 5. Compare: Q_union should equal Q1 UNION ALL Q2
 * 
 * For the POC, we start with simple cardinality checking.
 */
public class SQLite3MRUPOracle implements TestOracle<SQLite3GlobalState> {

    /**
     * WindowSpec: Stores parsed window specification metadata for MRUP normalization.
     * This is essential for Phase 3 to sort results correctly while preserving window semantics.
     */
    private static class WindowSpec {
        String partitionColumn;              // e.g., "dept"
        int partitionColumnIndex;            // Index in result set
        String partitionColumnType;          // "TEXT", "INTEGER", "REAL"
        
        List<String> orderByColumns;         // e.g., ["salary", "age"]
        List<Integer> orderByColumnIndices;  // Indices in result set
        List<String> orderByColumnTypes;     // Types for each ORDER BY column
        List<String> orderByDirections;      // "ASC" or "DESC"
        List<String> nullsHandling;          // "NULLS FIRST" or "NULLS LAST"
        
        int wfResultIndex;                   // Index of wf_result column (always last)
        
        WindowSpec() {
            orderByColumns = new ArrayList<>();
            orderByColumnIndices = new ArrayList<>();
            orderByColumnTypes = new ArrayList<>();
            orderByDirections = new ArrayList<>();
            nullsHandling = new ArrayList<>();
        }
    }

    private final SQLite3GlobalState globalState;
    private final ExpectedErrors errors = new ExpectedErrors();
    private String lastQueryString;
    private int lastOrderByColumnCount = 1; // Track ORDER BY columns for RANGE constraint (C4)
    private String lastWindowFunctionType = ""; // Track function type for frame validation (C3)
    private WindowSpec currentWindowSpec; // Store current window spec for comparison (Phase 3)

    public SQLite3MRUPOracle(SQLite3GlobalState globalState) {
        this.globalState = globalState;
        SQLite3Errors.addExpectedExpressionErrors(errors);
        SQLite3Errors.addQueryErrors(errors);
        errors.add("misuse of aggregate");
        errors.add("misuse of window function");
        errors.add("second argument to nth_value must be a positive integer");
        errors.add("no such table");
    }

    @Override
    public void check() throws Exception {
        // Create file-based logger for this test case
        SQLite3MRUPTestCaseLogger logger = new SQLite3MRUPTestCaseLogger();
        logger.logHeader();
        
        // Step 1 & 2: Generate two tables with the SAME schema
        // Using custom MRUP table pair generator that reuses SQLancer's generators
        SQLite3Table[] tablePair = SQLite3MRUPTablePairGenerator.generateMRUPTablePair(globalState);
        SQLite3Table t1 = tablePair[0];
        SQLite3Table t2 = tablePair[1];
        
        // Verify tables have data (should always be true, but check anyway)
        if (t1.getNrRows(globalState) == 0 || t2.getNrRows(globalState) == 0) {
            throw new IgnoreMeException();
        }

        // Get columns (both tables have the same schema)
        List<SQLite3Column> columns = t1.getColumns();
        if (columns.isEmpty()) {
            throw new IgnoreMeException();
        }
        
        // Collect table data for logging
        List<List<String>> t1Data = collectTableData(t1);
        List<List<String>> t2Data = collectTableData(t2);
        Set<String> t1Partitions = getPartitionValues(t1);
        Set<String> t2Partitions = getPartitionValues(t2);
        
        // Log Step 1 & 2 to file
        logger.logTableInfo(t1, t2, columns, t1Data, t2Data, t1Partitions, t2Partitions);

        // Step 3: Generate window function type first (to determine if frame is allowed)
        // C5: Only use deterministic functions
        String functionType = Randomly.fromOptions(
            "ROW_NUMBER",    // Ranking - no frame
            "RANK",          // Ranking - no frame
            "DENSE_RANK",    // Ranking - no frame
            "SUM",           // Aggregate - frame allowed
            "AVG",           // Aggregate - frame allowed
            "COUNT",         // Aggregate - frame allowed
            "MIN",           // Aggregate - frame allowed
            "MAX"            // Aggregate - frame allowed
        );
        this.lastWindowFunctionType = functionType;
        
        // Step 3.1: Generate window spec with constraints
        String windowSpec = generateWindowSpecOSRB(columns);
        
        // Step 3.2: Add frame clause if applicable (C3: no frames for ranking functions)
        boolean isRankingFunction = functionType.equals("ROW_NUMBER") || 
                                   functionType.equals("RANK") || 
                                   functionType.equals("DENSE_RANK");
        
        if (!isRankingFunction && Randomly.getBoolean()) {
            // Add frame for aggregate functions
            String frameClause = generateFrameClause();
            // Insert frame before the closing parenthesis
            windowSpec = windowSpec.substring(0, windowSpec.length() - 1) + " " + frameClause + ")";
        }
        
        // Step 3.3: Apply random mutations to window spec (Top 10 strategies)
        // Randomly decide whether to apply mutations (50% chance for POC)
        String originalWindowSpec = windowSpec;
        boolean mutationApplied = false;
        if (Randomly.getBoolean()) {
            String mutatedSpec = SQLite3MRUPMutationOperator.applyRandomMutations(windowSpec, columns);
            if (!mutatedSpec.equals(windowSpec)) {
                windowSpec = mutatedSpec;
                mutationApplied = true;
            }
        }
        
        // Pick a random column for the window function (only for aggregate functions)
        SQLite3Column targetColumn = Randomly.fromList(columns);
        
        // Generate window function
        String windowFunction = generateWindowFunction(functionType, targetColumn, windowSpec);
        
        // Phase 3: Parse window spec for MRUP normalization
        this.currentWindowSpec = parseWindowSpec(windowSpec, columns);
        
        // Log Step 3 to file: Window function generation with constraint verification
        Map<String, Boolean> constraints = verifyConstraints(functionType, windowSpec);
        logger.logWindowFunctionGeneration(functionType, windowSpec, windowFunction, mutationApplied, constraints);

        // Step 4: Execute queries
        // Q1: window function on t1
        String q1 = buildWindowQuery(t1, columns, windowFunction);
        
        // Q2: window function on t2
        String q2 = buildWindowQuery(t2, columns, windowFunction);
        
        // Q_union: window function on (t1 UNION ALL t2)
        String qUnion = buildWindowQueryOnUnion(t1, t2, columns, windowFunction);

        lastQueryString = "-- Q1:\n" + q1 + "\n-- Q2:\n" + q2 + "\n-- Q_union:\n" + qUnion;
        
        // Log Step 4 to file: Generated queries
        logger.logQueries(q1, q2, qUnion);


        // Execute and get results
        List<List<String>> results1 = executeAndGetResults(q1);
        List<List<String>> results2 = executeAndGetResults(q2);
        List<List<String>> resultsUnion = executeAndGetResults(qUnion);

        // Step 5: Compare results (Phase 3: MRUP Normalization & Smart Comparison)
        // Layer 1: Cardinality Check (Fast Fail)
        int expectedCardinality = results1.size() + results2.size();
        int actualCardinality = resultsUnion.size();
        
        if (actualCardinality != expectedCardinality) {
            // Log failure to file
            logger.logComparison(expectedCardinality, actualCardinality,
                               currentWindowSpec.partitionColumn, currentWindowSpec.orderByColumns, false,
                               results1, results2, resultsUnion);
            logger.writeToFile();
            
            throw new AssertionError(
                String.format("MRUP Oracle: Cardinality mismatch!\n" +
                    "Expected: %d (Q1: %d + Q2: %d)\n" +
                    "Actual: %d\n" +
                    "Queries:\n%s",
                    expectedCardinality, results1.size(), results2.size(), actualCardinality, lastQueryString)
            );
        }
        
        // Layer 2: MRUP Normalization (Semantic Sorting)
        // Make copies for logging before normalization
        List<List<String>> results1Original = new ArrayList<>();
        List<List<String>> results2Original = new ArrayList<>();
        List<List<String>> resultsUnionOriginal = new ArrayList<>();
        for (List<String> row : results1) results1Original.add(new ArrayList<>(row));
        for (List<String> row : results2) results2Original.add(new ArrayList<>(row));
        for (List<String> row : resultsUnion) resultsUnionOriginal.add(new ArrayList<>(row));
        
        normalizeForMRUP(results1, currentWindowSpec);
        normalizeForMRUP(results2, currentWindowSpec);
        normalizeForMRUP(resultsUnion, currentWindowSpec);
        
        // Layer 3: Per-Partition Comparison (Exact Match)
        boolean match = comparePerPartition(results1, results2, resultsUnion, currentWindowSpec, logger);
        
        if (!match) {
            // Log failure to file
            logger.logComparison(expectedCardinality, actualCardinality,
                               currentWindowSpec.partitionColumn, currentWindowSpec.orderByColumns, false,
                               results1Original, results2Original, resultsUnionOriginal);
            logger.writeToFile();
            
            // Generate bug report for reproduction
            try {
                String bugDesc = "MRUP metamorphic relation violated - per-partition comparison failed";
                
                SQLite3MRUPBugReproducer.generateBugReport(
                    globalState, t1, t2, q1, q2, qUnion, bugDesc);
            } catch (Exception e) {
                System.err.println("[MRUP] Failed to generate bug report: " + e.getMessage());
            }
            
            throw new AssertionError(
                String.format("MRUP Oracle: Per-partition comparison failed!\n" +
                    "See details above.\n" +
                    "Queries:\n%s",
                    lastQueryString)
            );
        }
        
        // Log success to file
        logger.logComparison(expectedCardinality, actualCardinality,
                           currentWindowSpec.partitionColumn, currentWindowSpec.orderByColumns, true,
                           results1Original, results2Original, resultsUnionOriginal);
        logger.writeToFile();
    }

    /**
     * OSRB (OVER-Spec Random Builder) Algorithm - Phase 2 Enhanced
     * Generates a random OVER() clause following strict MRUP constraints:
     * 
     * PHASE 2 CONSTRAINTS:
     * - C1: PARTITION BY must use only 'dept' column (the disjoint partition key)
     * - C2: ORDER BY must use only 'salary' or 'age' columns (numeric/orderable)
     * - C3: FRAME must be valid for the window function type
     * - C4: RANGE only with single ORDER BY column
     * - C5: No nondeterministic functions
     */
    private String generateWindowSpecOSRB(List<SQLite3Column> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("OVER (");

        // C1: PARTITION BY - MANDATORY for MRUP to work correctly!
        // Without PARTITION BY, window function treats entire e_union as one partition,
        // which is different from e1 + e2 (breaks MRUP metamorphic relation)
        SQLite3Column deptColumn = findColumnByName(columns, "dept");
        if (deptColumn == null) {
            throw new IgnoreMeException(); // Skip if dept column not found
        }
        sb.append("PARTITION BY ");
        sb.append(deptColumn.getName());
        sb.append(" ");

        // C2: ORDER BY - must use only 'salary' or 'age' columns (numeric columns)
        // Always add ORDER BY for determinism
        sb.append("ORDER BY ");
        List<SQLite3Column> orderableColumns = findOrderableColumns(columns);
        
        if (orderableColumns.isEmpty()) {
            // Fallback: use any column
            sb.append(Randomly.fromList(columns).getName());
        } else {
            // Use 1-2 orderable columns
            int numOrderCols = Randomly.getBoolean() ? 1 : 2;
            numOrderCols = Math.min(numOrderCols, orderableColumns.size());
            
            for (int i = 0; i < numOrderCols; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(orderableColumns.get(i).getName());
                
                // Optional: ASC/DESC
                if (Randomly.getBoolean()) {
                    sb.append(Randomly.fromOptions(" ASC", " DESC"));
                }
                
                // Optional: NULLS FIRST/LAST
                if (Randomly.getBoolean()) {
                    sb.append(Randomly.fromOptions(" NULLS FIRST", " NULLS LAST"));
                }
            }
        }

        // Store ORDER BY column count for frame validation (C4)
        this.lastOrderByColumnCount = orderableColumns.isEmpty() ? 1 : 
            (Randomly.getBoolean() ? 1 : Math.min(2, orderableColumns.size()));

        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Find column by name (case-insensitive).
     */
    private SQLite3Column findColumnByName(List<SQLite3Column> columns, String name) {
        for (SQLite3Column col : columns) {
            if (col.getName().equalsIgnoreCase(name)) {
                return col;
            }
        }
        return null;
    }
    
    /**
     * Find orderable columns (salary, age) - numeric columns suitable for ORDER BY.
     * C2: ORDER BY must use only numeric/orderable columns.
     */
    private List<SQLite3Column> findOrderableColumns(List<SQLite3Column> columns) {
        List<SQLite3Column> orderable = new ArrayList<>();
        for (SQLite3Column col : columns) {
            String name = col.getName().toLowerCase();
            // Only salary and age are guaranteed to be numeric and orderable
            if (name.equals("salary") || name.equals("age")) {
                orderable.add(col);
            }
        }
        return orderable;
    }

    /**
     * Generate a random FRAME clause (ROWS/RANGE/GROUPS).
     * Phase 2 Constraints:
     * - C4: RANGE only allowed with single ORDER BY column
     * - C3: This method should only be called for aggregate functions (not ranking)
     */
    private String generateFrameClause() {
        StringBuilder sb = new StringBuilder();
        
        // C4: RANGE only if single ORDER BY column
        String frameType;
        if (lastOrderByColumnCount == 1) {
            // Can use ROWS or RANGE (GROUPS not widely supported in SQLite3)
            frameType = Randomly.fromOptions("ROWS", "RANGE");
        } else {
            // Multiple ORDER BY columns: only ROWS allowed
            frameType = "ROWS";
        }
        
        sb.append(frameType);
        sb.append(" ");

        // Frame extent
        if (Randomly.getBoolean()) {
            // Simple frame start (implicit: AND CURRENT ROW)
            sb.append(Randomly.fromOptions(
                "UNBOUNDED PRECEDING",
                "CURRENT ROW",
                "1 PRECEDING",
                "2 PRECEDING"
            ));
        } else {
            // BETWEEN frame (explicit boundaries)
            sb.append("BETWEEN ");
            sb.append(Randomly.fromOptions(
                "UNBOUNDED PRECEDING",
                "CURRENT ROW",
                "1 PRECEDING",
                "2 PRECEDING"
            ));
            sb.append(" AND ");
            sb.append(Randomly.fromOptions(
                "CURRENT ROW",
                "UNBOUNDED FOLLOWING",
                "1 FOLLOWING",
                "2 FOLLOWING"
            ));
        }

        // Optional: EXCLUDE clause (SQLite3 supports this)
        if (Randomly.getBoolean()) {
            sb.append(" EXCLUDE ");
            sb.append(Randomly.fromOptions("NO OTHERS", "TIES", "CURRENT ROW", "GROUP"));
        }

        return sb.toString();
    }

    /**
     * Generate a window function with the given spec.
     * Phase 2: Uses only deterministic functions (C5).
     * 
     * @param functionType The type of window function (ROW_NUMBER, RANK, SUM, etc.)
     * @param column The column to apply the function to (for aggregate functions)
     * @param windowSpec The OVER() clause specification
     * @return Complete window function string
     */
    private String generateWindowFunction(String functionType, SQLite3Column column, String windowSpec) {
        String funcName;
        
        switch (functionType) {
            case "ROW_NUMBER":
                funcName = "ROW_NUMBER()";
                break;
            case "RANK":
                funcName = "RANK()";
                break;
            case "DENSE_RANK":
                funcName = "DENSE_RANK()";
                break;
            case "SUM":
                funcName = "SUM(" + column.getName() + ")";
                break;
            case "AVG":
                funcName = "AVG(" + column.getName() + ")";
                break;
            case "COUNT":
                // Randomly choose COUNT(*) or COUNT(column)
                funcName = Randomly.getBoolean() ? "COUNT(*)" : "COUNT(" + column.getName() + ")";
                break;
            case "MIN":
                funcName = "MIN(" + column.getName() + ")";
                break;
            case "MAX":
                funcName = "MAX(" + column.getName() + ")";
                break;
            default:
                funcName = "COUNT(*)";
        }
        
        return funcName + " " + windowSpec;
    }

    /**
     * Build a SELECT query with window function on a single table
     */
    private String buildWindowQuery(SQLite3Table table, List<SQLite3Column> columns, String windowFunction) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        
        // Select all columns
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
        }
        
        // Add window function
        sb.append(", ");
        sb.append(windowFunction);
        sb.append(" AS wf_result");
        
        sb.append(" FROM ");
        sb.append(table.getName());
        
        return sb.toString();
    }

    /**
     * Build a SELECT query with window function on UNION of two tables
     */
    private String buildWindowQueryOnUnion(SQLite3Table t1, SQLite3Table t2, 
                                           List<SQLite3Column> columns, String windowFunction) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        
        // Select all columns
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
        }
        
        // Add window function
        sb.append(", ");
        sb.append(windowFunction);
        sb.append(" AS wf_result");
        
        sb.append(" FROM (");
        sb.append("SELECT * FROM ");
        sb.append(t1.getName());
        sb.append(" UNION ALL SELECT * FROM ");
        sb.append(t2.getName());
        sb.append(") AS t_union");
        
        return sb.toString();
    }

    /**
     * Execute query and return all results as list of rows
     * Each row is a list of string values
     */
    private List<List<String>> executeAndGetResults(String query) throws SQLException {
        List<List<String>> results = new ArrayList<>();
        
        try (Statement stmt = globalState.getConnection().createStatement()) {
            boolean hasResultSet = stmt.execute(query);
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    int columnCount = rs.getMetaData().getColumnCount();
                    
                    while (rs.next()) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            // Convert to string for comparison (handle nulls)
                            row.add(value == null ? "NULL" : value.toString());
                        }
                        results.add(row);
                    }
                }
            }
        } catch (Exception e) {
            // Check if it's an expected error
            if (e.getMessage() != null && errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException(); // Expected error, skip this test
            }
            // Unexpected error, rethrow
            throw e;
        }
        
        return results;
    }
    
    /**
     * Sort results for comparison (simple lexicographic sort)
     */
    private void sortResults(List<List<String>> results) {
        results.sort((row1, row2) -> {
            for (int i = 0; i < Math.min(row1.size(), row2.size()); i++) {
                int cmp = row1.get(i).compareTo(row2.get(i));
                if (cmp != 0) return cmp;
            }
            return Integer.compare(row1.size(), row2.size());
        });
    }
    
    /**
     * Check if two result sets match
     */
    private boolean resultsMatch(List<List<String>> expected, List<List<String>> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        
        for (int i = 0; i < expected.size(); i++) {
            List<String> expectedRow = expected.get(i);
            List<String> actualRow = actual.get(i);
            
            if (expectedRow.size() != actualRow.size()) {
                return false;
            }
            
            for (int j = 0; j < expectedRow.size(); j++) {
                if (!expectedRow.get(j).equals(actualRow.get(j))) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Find first difference between two result sets for error reporting
     */
    private String findFirstDifference(List<List<String>> expected, List<List<String>> actual) {
        int minSize = Math.min(expected.size(), actual.size());
        
        for (int i = 0; i < minSize; i++) {
            List<String> expectedRow = expected.get(i);
            List<String> actualRow = actual.get(i);
            
            if (!expectedRow.equals(actualRow)) {
                return String.format("Row %d: Expected %s, Got %s", i + 1, expectedRow, actualRow);
            }
        }
        
        if (expected.size() != actual.size()) {
            return String.format("Row count mismatch: Expected %d, Got %d", expected.size(), actual.size());
        }
        
        return "No difference found";
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }
    
    /**
     * Log comprehensive table information including schema, data, and partition validation.
     * Step 1 & 2: Table Schema and Data
     */
    /**
     * Old logging methods removed - now using file-based logging via SQLite3MRUPTestCaseLogger
     */
    
    /**
     * Print table information including schema and data
     */
    // private void printTableInfo(SQLite3Table table, String label) {
    //     try {
    //         System.out.println("\n" + label + ": " + table.getName());
            
    //         // Print schema
    //         System.out.print("  Schema: (");
    //         List<SQLite3Column> cols = table.getColumns();
    //         for (int i = 0; i < cols.size(); i++) {
    //             if (i > 0) System.out.print(", ");
    //             System.out.print(cols.get(i).getName() + " " + cols.get(i).getType());
    //         }
    //         System.out.println(")");
            
    //         // Print row count
    //         long rowCount = table.getNrRows(globalState);
    //         System.out.println("  Rows: " + rowCount);
            
    //         // Print actual data (first 5 rows)
    //         if (rowCount > 0) {
    //             String dataQuery = "SELECT * FROM " + table.getName() + " LIMIT 5";
    //             try (Statement stmt = globalState.getConnection().createStatement()) {
    //                 ResultSet rs = stmt.executeQuery(dataQuery);
    //                 System.out.println("  Data:");
    //                 int rowNum = 0;
    //                 while (rs.next() && rowNum < 5) {
    //                     System.out.print("    Row " + (rowNum + 1) + ": ");
    //                     for (int i = 0; i < cols.size(); i++) {
    //                         if (i > 0) System.out.print(", ");
    //                         Object value = rs.getObject(i + 1);
    //                         System.out.print(cols.get(i).getName() + "=" + value);
    //                     }
    //                     System.out.println();
    //                     rowNum++;
    //                 }
    //             }
    //         }
    //     } catch (Exception e) {
    //         System.out.println("  Error printing table info: " + e.getMessage());
    //     }
    // }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 3: MRUP NORMALIZATION & SMART COMPARISON
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Component 1: Parse window specification to extract metadata for MRUP normalization.
     * 
     * Extracts:
     * - PARTITION BY column (always "dept" in our case)
     * - ORDER BY columns, directions (ASC/DESC), and NULLS handling
     * - Column indices and types for comparison
     * 
     * Example input: "OVER (PARTITION BY dept ORDER BY salary DESC, age ASC NULLS FIRST)"
     */
    private WindowSpec parseWindowSpec(String windowSpec, List<SQLite3Column> columns) {
        WindowSpec spec = new WindowSpec();
        
        // Extract PARTITION BY column (always "dept" due to C0/C1 constraints)
        Pattern partitionPattern = Pattern.compile("PARTITION BY\\s+(\\w+)");
        Matcher partitionMatcher = partitionPattern.matcher(windowSpec);
        if (partitionMatcher.find()) {
            spec.partitionColumn = partitionMatcher.group(1);
            
            // Find column index and type
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getName().equals(spec.partitionColumn)) {
                    spec.partitionColumnIndex = i;
                    spec.partitionColumnType = columns.get(i).getType().toString();
                    break;
                }
            }
        }
        
        // Extract ORDER BY columns
        Pattern orderByPattern = Pattern.compile("ORDER BY\\s+([^)]+?)(?:\\s+(?:ROWS|RANGE|GROUPS)|\\))");
        Matcher orderByMatcher = orderByPattern.matcher(windowSpec);
        if (orderByMatcher.find()) {
            String orderByClause = orderByMatcher.group(1).trim();
            
            // Split by comma to handle multiple ORDER BY columns
            String[] orderByParts = orderByClause.split(",");
            
            for (String part : orderByParts) {
                part = part.trim();
                
                // Extract column name
                String columnName = part.split("\\s+")[0];
                
                // Extract direction (ASC/DESC)
                String direction = "ASC"; // Default
                if (part.toUpperCase().contains(" DESC")) {
                    direction = "DESC";
                }
                
                // Extract NULLS handling
                String nullsHandling = "NULLS LAST"; // SQL standard default
                if (part.toUpperCase().contains("NULLS FIRST")) {
                    nullsHandling = "NULLS FIRST";
                } else if (part.toUpperCase().contains("NULLS LAST")) {
                    nullsHandling = "NULLS LAST";
                }
                
                // Find column index and type
                for (int i = 0; i < columns.size(); i++) {
                    if (columns.get(i).getName().equals(columnName)) {
                        spec.orderByColumns.add(columnName);
                        spec.orderByColumnIndices.add(i);
                        spec.orderByColumnTypes.add(columns.get(i).getType().toString());
                        spec.orderByDirections.add(direction);
                        spec.nullsHandling.add(nullsHandling);
                        break;
                    }
                }
            }
        }
        
        // wf_result is always the last column
        spec.wfResultIndex = columns.size(); // +1 for wf_result column
        
        return spec;
    }

    /**
     * Component 2: MRUP Normalization - Sort results preserving window function semantics.
     * 
     * Sorts by:
     * 1. Partition key (dept)
     * 2. ORDER BY keys (salary, age) in specified direction
     * 3. Window function result (wf_result) as tie-breaker
     * 
     * This is the CORRECT way to sort for MRUP comparison, unlike the naive
     * lexicographic sorting which breaks window semantics.
     */
    private void normalizeForMRUP(List<List<String>> results, WindowSpec spec) {
        results.sort((row1, row2) -> {
            // 1. Compare partition key
            int partitionCmp = compareValue(
                row1.get(spec.partitionColumnIndex),
                row2.get(spec.partitionColumnIndex),
                spec.partitionColumnType,
                "NULLS FIRST"  // SQL standard default for partition
            );
            if (partitionCmp != 0) return partitionCmp;
            
            // 2. Compare ORDER BY keys in order
            for (int i = 0; i < spec.orderByColumns.size(); i++) {
                int colIndex = spec.orderByColumnIndices.get(i);
                String direction = spec.orderByDirections.get(i);
                String nullsHandling = spec.nullsHandling.get(i);
                String colType = spec.orderByColumnTypes.get(i);
                
                int cmp = compareValue(
                    row1.get(colIndex),
                    row2.get(colIndex),
                    colType,
                    nullsHandling
                );
                
                // Reverse if DESC
                if (direction.equals("DESC")) {
                    cmp = -cmp;
                }
                
                if (cmp != 0) return cmp;
            }
            
            // 3. Compare window function result (tie-breaker)
            int wfCmp = compareValue(
                row1.get(spec.wfResultIndex),
                row2.get(spec.wfResultIndex),
                "INTEGER",  // Window function results are typically numeric
                "NULLS LAST"
            );
            
            return wfCmp;
        });
    }

    /**
     * Component 3: Type-aware value comparator.
     * 
     * Compares values based on their actual types, not just as strings.
     * Handles:
     * - NULL values (NULLS FIRST/LAST)
     * - INTEGER (numeric comparison)
     * - REAL (floating point with epsilon tolerance)
     * - TEXT (lexicographic comparison)
     */
    private int compareValue(String val1, String val2, String type, String nullsHandling) {
        // 1. Handle NULL
        boolean isNull1 = val1.equals("NULL");
        boolean isNull2 = val2.equals("NULL");
        
        if (isNull1 && isNull2) return 0;
        if (isNull1) return nullsHandling.equals("NULLS FIRST") ? -1 : 1;
        if (isNull2) return nullsHandling.equals("NULLS FIRST") ? 1 : -1;
        
        // 2. Type-specific comparison
        try {
            if (type.contains("INT")) {
                // INTEGER comparison
                long l1 = Long.parseLong(val1);
                long l2 = Long.parseLong(val2);
                return Long.compare(l1, l2);
            } else if (type.contains("REAL") || type.contains("DOUBLE") || type.contains("FLOAT")) {
                // REAL comparison with epsilon tolerance
                double d1 = Double.parseDouble(val1);
                double d2 = Double.parseDouble(val2);
                if (Math.abs(d1 - d2) < 1e-9) return 0;
                return Double.compare(d1, d2);
            } else {
                // TEXT comparison (lexicographic)
                return val1.compareTo(val2);
            }
        } catch (NumberFormatException e) {
            // Fallback to string comparison if parsing fails
            return val1.compareTo(val2);
        }
    }

    /**
     * Component 4: Per-partition comparison.
     * 
     * Groups results by partition and compares each partition independently.
     * This validates the MRUP metamorphic relation:
     *   For each partition P: Q_union[P] = Q1[P] if P ∈ t1, or Q2[P] if P ∈ t2
     * 
     * Returns true if all partitions match, false otherwise.
     */
    private boolean comparePerPartition(
        List<List<String>> q1Results,
        List<List<String>> q2Results,
        List<List<String>> qUnionResults,
        WindowSpec spec,
        SQLite3MRUPTestCaseLogger logger
    ) {
        // Group by partition
        Map<String, List<List<String>>> q1Partitions = groupByPartition(q1Results, spec);
        Map<String, List<List<String>>> q2Partitions = groupByPartition(q2Results, spec);
        Map<String, List<List<String>>> qUnionPartitions = groupByPartition(qUnionResults, spec);
        
        // Check each partition in Q_union
        for (String partition : qUnionPartitions.keySet()) {
            List<List<String>> unionRows = qUnionPartitions.get(partition);
            
            // Determine source (Q1 or Q2)
            if (q1Partitions.containsKey(partition)) {
                // This partition should come from Q1
                List<List<String>> expectedRows = q1Partitions.get(partition);
                
                if (!rowsMatch(expectedRows, unionRows, spec)) {
                    reportPartitionMismatch(partition, expectedRows, unionRows, "Q1", logger);
                    return false;
                }
            } else if (q2Partitions.containsKey(partition)) {
                // This partition should come from Q2
                List<List<String>> expectedRows = q2Partitions.get(partition);
                
                if (!rowsMatch(expectedRows, unionRows, spec)) {
                    reportPartitionMismatch(partition, expectedRows, unionRows, "Q2", logger);
                    return false;
                }
            } else {
                // Partition in Q_union but not in Q1 or Q2 → BUG!
                System.err.println("BUG: Partition '" + partition + "' found in Q_union but not in Q1 or Q2!");
                return false;
            }
        }
        
        // Check no partition is missing
        Set<String> allPartitions = new HashSet<>();
        allPartitions.addAll(q1Partitions.keySet());
        allPartitions.addAll(q2Partitions.keySet());
        
        if (!allPartitions.equals(qUnionPartitions.keySet())) {
            Set<String> missing = new HashSet<>(allPartitions);
            missing.removeAll(qUnionPartitions.keySet());
            System.err.println("BUG: Missing partitions in Q_union: " + missing);
            return false;
        }
        
        return true;
    }

    /**
     * Group results by partition key.
     */
    private Map<String, List<List<String>>> groupByPartition(List<List<String>> results, WindowSpec spec) {
        Map<String, List<List<String>>> partitions = new HashMap<>();
        
        for (List<String> row : results) {
            String partitionValue = row.get(spec.partitionColumnIndex);
            partitions.computeIfAbsent(partitionValue, k -> new ArrayList<>()).add(row);
        }
        
        return partitions;
    }

    /**
     * Check if two sets of rows match exactly.
     */
    private boolean rowsMatch(List<List<String>> expected, List<List<String>> actual, WindowSpec spec) {
        if (expected.size() != actual.size()) {
            return false;
        }
        
        for (int i = 0; i < expected.size(); i++) {
            List<String> expectedRow = expected.get(i);
            List<String> actualRow = actual.get(i);
            
            if (expectedRow.size() != actualRow.size()) {
                return false;
            }
            
            for (int j = 0; j < expectedRow.size(); j++) {
                if (!expectedRow.get(j).equals(actualRow.get(j))) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Component 5: Enhanced bug reporting with partition-level details.
     */
    private void reportPartitionMismatch(String partition, List<List<String>> expected, 
                                        List<List<String>> actual, String source,
                                        SQLite3MRUPTestCaseLogger logger) {
        // Log to file
        logger.logBugDetails(partition, source, expected, actual);
        
        // Print minimal error to terminal
        System.err.println("[MRUP] BUG FOUND: Partition '" + partition + "' mismatch (source: " + source + ")");
    }
    
    /**
     * Helper: Collect table data for logging.
     */
    private List<List<String>> collectTableData(SQLite3Table table) {
        List<List<String>> data = new ArrayList<>();
        try {
            String query = "SELECT * FROM " + table.getName();
            try (Statement stmt = globalState.getConnection().createStatement()) {
                ResultSet rs = stmt.executeQuery(query);
                int columnCount = rs.getMetaData().getColumnCount();
                
                while (rs.next()) {
                    List<String> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = rs.getObject(i);
                        row.add(value == null ? "NULL" : value.toString());
                    }
                    data.add(row);
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
        return data;
    }
    
    /**
     * Helper: Get partition values from table.
     */
    private Set<String> getPartitionValues(SQLite3Table table) {
        Set<String> partitions = new HashSet<>();
        try {
            String query = "SELECT DISTINCT dept FROM " + table.getName();
            try (Statement stmt = globalState.getConnection().createStatement()) {
                ResultSet rs = stmt.executeQuery(query);
                while (rs.next()) {
                    String dept = rs.getString(1);
                    partitions.add(dept == null ? "NULL" : dept);
                }
            }
        } catch (Exception e) {
            // Silently fail
        }
        return partitions;
    }
    
    /**
     * Helper: Verify constraints for logging.
     */
    private Map<String, Boolean> verifyConstraints(String functionType, String windowSpec) {
        Map<String, Boolean> constraints = new HashMap<>();
        
        // C0: PARTITION BY is mandatory
        boolean hasPartitionBy = windowSpec.contains("PARTITION BY");
        constraints.put("[C0] PARTITION BY is MANDATORY", hasPartitionBy);
        
        // C1: PARTITION BY uses only dept
        boolean usesOnlyDept = !windowSpec.contains("PARTITION BY") || 
                              (windowSpec.contains("PARTITION BY dept") && 
                               !windowSpec.matches(".*PARTITION BY.*,.*"));
        constraints.put("[C1] PARTITION BY only uses 'dept'", usesOnlyDept);
        
        // C2: ORDER BY uses only salary/age
        boolean orderByValid = true;
        if (windowSpec.contains("ORDER BY")) {
            String orderPart = windowSpec.substring(windowSpec.indexOf("ORDER BY"));
            orderByValid = !orderPart.matches(".*ORDER BY[^(]*\\b(?!salary|age)\\w+\\b.*");
        }
        constraints.put("[C2] ORDER BY only uses salary/age", orderByValid);
        
        // C3: No FRAME for ranking functions
        boolean isRanking = functionType.equals("ROW_NUMBER") || 
                           functionType.equals("RANK") || 
                           functionType.equals("DENSE_RANK");
        boolean hasFrame = windowSpec.matches(".*(ROWS|RANGE|GROUPS).*");
        boolean c3Valid = !isRanking || !hasFrame;
        constraints.put("[C3] No FRAME for ranking functions", c3Valid);
        
        // C4: RANGE only with single ORDER BY
        boolean c4Valid = true;
        if (windowSpec.contains("RANGE") && windowSpec.contains("ORDER BY")) {
            String orderPart = windowSpec.substring(windowSpec.indexOf("ORDER BY"), 
                                                  windowSpec.indexOf("RANGE"));
            int commaCount = orderPart.length() - orderPart.replace(",", "").length();
            c4Valid = commaCount == 0;
        }
        constraints.put("[C4] RANGE only with single ORDER BY", c4Valid);
        
        // C5: Only deterministic functions
        boolean c5Valid = functionType.matches("ROW_NUMBER|RANK|DENSE_RANK|SUM|AVG|COUNT|MIN|MAX");
        constraints.put("[C5] Only deterministic functions", c5Valid);
        
        return constraints;
    }
}

