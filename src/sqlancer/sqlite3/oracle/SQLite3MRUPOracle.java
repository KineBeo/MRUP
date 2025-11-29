package sqlancer.sqlite3.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.schema.SQLite3Schema;
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
        // Step 1 & 2: Get two random tables (reusing existing tables from SQLancer)
        SQLite3Schema schema = globalState.getSchema();
        List<SQLite3Table> tables = schema.getDatabaseTablesWithoutViews();
        
        if (tables.size() < 2) {
            throw new IgnoreMeException(); // Need at least 2 tables
        }

        // Select two random tables
        SQLite3Table t1 = Randomly.fromList(tables);
        SQLite3Table t2 = Randomly.fromList(tables);
        
        // Ensure tables have rows
        if (t1.getNrRows(globalState) == 0 || t2.getNrRows(globalState) == 0) {
            throw new IgnoreMeException();
        }

        // Get columns from first table (for simplicity, assume compatible schemas)
        List<SQLite3Column> columns = t1.getColumns();
        if (columns.isEmpty()) {
            throw new IgnoreMeException();
        }

        // Step 3: Generate window function query using OSRB algorithm
        String windowSpec = generateWindowSpecOSRB(columns);
        
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

        // Print queries for demonstration
        System.out.println("\n=== MRUP Generated Queries ===");
        System.out.println(lastQueryString);
        System.out.println("==============================\n");

        // Execute and get cardinalities
        int card1 = executeAndGetCardinality(q1);
        int card2 = executeAndGetCardinality(q2);
        int cardUnion = executeAndGetCardinality(qUnion);

        // Step 5: Compare cardinalities (simple check for POC)
        int expectedCardinality = card1 + card2;
        
        if (cardUnion != expectedCardinality) {
            throw new AssertionError(
                String.format("MRUP Oracle: Cardinality mismatch!\n" +
                    "Expected: %d (Q1: %d + Q2: %d)\n" +
                    "Actual: %d\n" +
                    "Queries:\n%s",
                    expectedCardinality, card1, card2, cardUnion, lastQueryString)
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
     * Execute query and return the number of rows (cardinality)
     */
    private int executeAndGetCardinality(String query) throws SQLException {
        int rowCount = 0;
        
        try (Statement stmt = globalState.getConnection().createStatement()) {
            boolean hasResultSet = stmt.execute(query);
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    while (rs.next()) {
                        rowCount++;
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
        
        return rowCount;
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }
}

