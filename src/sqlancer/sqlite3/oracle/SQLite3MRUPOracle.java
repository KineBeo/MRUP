package sqlancer.sqlite3.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

    private final SQLite3GlobalState globalState;
    private final ExpectedErrors errors = new ExpectedErrors();
    private String lastQueryString;

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

        // Step 3: Generate window function query using OSRB algorithm
        String windowSpec = generateWindowSpecOSRB(columns);
        
        // Step 3.5: Apply random mutations to window spec (Top 10 strategies)
        // Randomly decide whether to apply mutations (50% chance for POC)
        if (Randomly.getBoolean()) {
            String mutatedSpec = SQLite3MRUPMutationOperator.applyRandomMutations(windowSpec, columns);
            System.out.println("  [MUTATION] Original: " + windowSpec);
            System.out.println("  [MUTATION] Mutated:  " + mutatedSpec);
            windowSpec = mutatedSpec;
        }
        
        // Pick a random column for the window function
        SQLite3Column targetColumn = Randomly.fromList(columns);
        
        // Generate window function (using aggregate functions that work as window functions)
        String windowFunction = generateWindowFunction(targetColumn, windowSpec);

        // Step 4: Execute queries
        // Q1: window function on t1
        String q1 = buildWindowQuery(t1, columns, windowFunction);
        
        // Q2: window function on t2
        String q2 = buildWindowQuery(t2, columns, windowFunction);
        
        // Q_union: window function on (t1 UNION ALL t2)
        String qUnion = buildWindowQueryOnUnion(t1, t2, columns, windowFunction);

        lastQueryString = "-- Q1:\n" + q1 + "\n-- Q2:\n" + q2 + "\n-- Q_union:\n" + qUnion;


        // Execute and get results
        List<List<String>> results1 = executeAndGetResults(q1);
        List<List<String>> results2 = executeAndGetResults(q2);
        List<List<String>> resultsUnion = executeAndGetResults(qUnion);

        // Step 5: Compare results
        // 5.1: Check cardinality first (fast check)
        int expectedCardinality = results1.size() + results2.size();
        int actualCardinality = resultsUnion.size();
        
        if (actualCardinality != expectedCardinality) {
            throw new AssertionError(
                String.format("MRUP Oracle: Cardinality mismatch!\n" +
                    "Expected: %d (Q1: %d + Q2: %d)\n" +
                    "Actual: %d\n" +
                    "Queries:\n%s",
                    expectedCardinality, results1.size(), results2.size(), actualCardinality, lastQueryString)
            );
        }
        
        // 5.2: Check actual result values (simple comparison for POC)
        // Combine Q1 and Q2 results
        List<List<String>> expectedResults = new ArrayList<>();
        expectedResults.addAll(results1);
        expectedResults.addAll(results2);
        
        // Sort both for comparison (since UNION ALL order may vary)
        sortResults(expectedResults);
        sortResults(resultsUnion);
        
        // Compare row by row
        if (!resultsMatch(expectedResults, resultsUnion)) {
            throw new AssertionError(
                String.format("MRUP Oracle: Result set mismatch!\n" +
                    "Expected rows: %d\n" +
                    "Actual rows: %d\n" +
                    "First difference at row: %s\n" +
                    "Queries:\n%s",
                    expectedResults.size(), resultsUnion.size(), 
                    findFirstDifference(expectedResults, resultsUnion),
                    lastQueryString)
            );
        }
    }

    /**
     * OSRB (OVER-Spec Random Builder) Algorithm
     * Generates a random OVER() clause with optional PARTITION BY, ORDER BY, and FRAME
     */
    private String generateWindowSpecOSRB(List<SQLite3Column> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("OVER (");

        // Optional: PARTITION BY
        if (Randomly.getBoolean()) {
            sb.append("PARTITION BY ");
            int numPartitionCols = Randomly.smallNumber() + 1;
            for (int i = 0; i < numPartitionCols; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(Randomly.fromList(columns).getName());
            }
            sb.append(" ");
        }

        // Always add ORDER BY (for deterministic results)
        sb.append("ORDER BY ");
        int numOrderCols = Randomly.smallNumber() + 1;
        for (int i = 0; i < numOrderCols; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Randomly.fromList(columns).getName());
            if (Randomly.getBoolean()) {
                sb.append(Randomly.fromOptions(" ASC", " DESC"));
            }
            // Add NULLS FIRST/LAST
            if (Randomly.getBoolean()) {
                sb.append(Randomly.fromOptions(" NULLS FIRST", " NULLS LAST"));
            }
        }

        // Optional: FRAME clause
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(generateFrameClause());
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Generate a random FRAME clause (ROWS/RANGE/GROUPS)
     */
    private String generateFrameClause() {
        StringBuilder sb = new StringBuilder();
        
        // Frame type
        sb.append(Randomly.fromOptions("ROWS", "RANGE", "GROUPS"));
        sb.append(" ");

        // Frame extent
        if (Randomly.getBoolean()) {
            // Simple frame start
            sb.append(Randomly.fromOptions(
                "UNBOUNDED PRECEDING",
                "CURRENT ROW",
                "1 PRECEDING",
                "2 PRECEDING"
            ));
        } else {
            // BETWEEN frame
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

        // Optional: EXCLUDE clause
        if (Randomly.getBoolean()) {
            sb.append(" EXCLUDE ");
            sb.append(Randomly.fromOptions("NO OTHERS", "TIES", "CURRENT ROW", "GROUP"));
        }

        return sb.toString();
    }

    /**
     * Generate a window function with the given spec
     * Using simple aggregate functions that work as window functions
     */
    private String generateWindowFunction(SQLite3Column column, String windowSpec) {
        String funcName = Randomly.fromOptions(
            "ROW_NUMBER()",
            "RANK()",
            "DENSE_RANK()",
            "SUM(" + column.getName() + ")",
            "AVG(" + column.getName() + ")",
            "COUNT(" + column.getName() + ")",
            "MIN(" + column.getName() + ")",
            "MAX(" + column.getName() + ")",
            "COUNT(*)"
        );
        
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
}

