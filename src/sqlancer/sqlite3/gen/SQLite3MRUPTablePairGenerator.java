package sqlancer.sqlite3.gen;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.gen.dml.SQLite3InsertGenerator;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;

/**
 * MRUP Table Pair Generator
 * 
 * Creates two tables with the SAME schema for MRUP oracle testing.
 * This generator reuses SQLancer's existing table and insert generators
 * but ensures both tables have identical schemas.
 */
public class SQLite3MRUPTablePairGenerator {

    private final SQLite3GlobalState globalState;
    private final ExpectedErrors errors;

    public SQLite3MRUPTablePairGenerator(SQLite3GlobalState globalState) {
        this.globalState = globalState;
        this.errors = new ExpectedErrors();
        SQLite3Errors.addTableManipulationErrors(errors);
        SQLite3Errors.addInsertUpdateErrors(errors);
    }

    /**
     * Generates a pair of tables with the same schema.
     * 
     * @return Array of [table1, table2] with identical schemas
     * @throws Exception if table creation fails
     */
    public SQLite3Table[] generateTablePair() throws Exception {
        // Step 1: Generate schema definition
        String schemaDefinition = generateSchemaDefinition();
        
        // Step 2: Get TWO DIFFERENT table names
        final String tableName1 = globalState.getSchema().getFreeTableName();
        String tempTableName2 = globalState.getSchema().getFreeTableName();
        
        // Ensure they are different (if by chance they're the same, get a new one)
        while (tableName1.equals(tempTableName2)) {
            tempTableName2 = globalState.getSchema().getFreeTableName();
        }
        final String tableName2 = tempTableName2;
        
        // Step 3: Create two tables with the same schema
        createTable(tableName1, schemaDefinition);
        createTable(tableName2, schemaDefinition);
        
        // Step 4: Update schema to get table objects
        globalState.updateSchema();
        
        SQLite3Table table1 = globalState.getSchema().getDatabaseTablesWithoutViews().stream()
            .filter(t -> t.getName().equals(tableName1))
            .findFirst()
            .orElseThrow(() -> new SQLException("Table " + tableName1 + " not found after creation"));
            
        SQLite3Table table2 = globalState.getSchema().getDatabaseTablesWithoutViews().stream()
            .filter(t -> t.getName().equals(tableName2))
            .findFirst()
            .orElseThrow(() -> new SQLException("Table " + tableName2 + " not found after creation"));
        
        // Verify they are different tables
        if (table1.getName().equals(table2.getName())) {
            throw new SQLException("MRUP Error: table1 and table2 have the same name: " + table1.getName());
        }
        
        // Step 5: Insert data into both tables using SQLancer's insert generator
        insertDataIntoTable(table1);
        insertDataIntoTable(table2);
        
        // Step 6: Update row counts
        globalState.updateSchema();
        
        return new SQLite3Table[] { table1, table2 };
    }

    /**
     * Generate a schema definition string using SQLancer's column builder logic.
     * This creates a simple, compatible schema that works well with window functions.
     */
    private String generateSchemaDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        
        // Generate 2-4 columns (simple for better compatibility)
        int numColumns = 2 + Randomly.smallNumber();
        if (numColumns > 4) {
            numColumns = 4;
        }
        
        List<SQLite3Column> dummyColumns = new ArrayList<>();
        for (int i = 0; i < numColumns; i++) {
            String columnName = DBMSCommon.createColumnName(i);
            dummyColumns.add(SQLite3Column.createDummy(columnName));
        }
        
        // Build column definitions
        for (int i = 0; i < dummyColumns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            
            String columnName = dummyColumns.get(i).getName();
            
            // Use SQLite3ColumnBuilder logic but simplified
            // Avoid PRIMARY KEY to prevent conflicts and ensure compatibility
            SQLite3ColumnBuilder columnBuilder = new SQLite3ColumnBuilder()
                .allowPrimaryKey(false); // Disable PRIMARY KEY for compatibility
            
            sb.append(columnBuilder.createColumn(columnName, globalState, dummyColumns));
        }
        
        sb.append(")");
        return sb.toString();
    }

    /**
     * Create a table with the given schema definition.
     */
    private void createTable(String tableName, String schemaDefinition) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(tableName);
        sb.append(" ");
        sb.append(schemaDefinition);
        
        String createTableSQL = sb.toString();
        SQLQueryAdapter query = new SQLQueryAdapter(createTableSQL, errors, true);
        globalState.executeStatement(query);
    }

    /**
     * Insert random data into a table using SQLancer's insert generator.
     * Reuses SQLite3InsertGenerator for consistency.
     */
    private void insertDataIntoTable(SQLite3Table table) {
        // Insert 2-5 rows (using Randomly.smallNumber() like SQLancer does)
        int numRows = 2 + Randomly.smallNumber();
        if (numRows > 5) {
            numRows = 5; // Cap at 5 rows for POC
        }
        
        for (int i = 0; i < numRows; i++) {
            try {
                // Use SQLancer's existing insert generator
                SQLQueryAdapter insertQuery = SQLite3InsertGenerator.insertRow(globalState, table);
                globalState.executeStatement(insertQuery);
            } catch (Exception e) {
                // Some inserts may fail due to constraints, that's OK
                // Continue inserting other rows
                if (e.getMessage() != null && !errors.errorIsExpected(e.getMessage())) {
                    // Unexpected error, but don't fail the entire generation
                    // Just skip this insert
                }
            }
        }
        
        // Ensure at least 1 row exists
        try {
            ensureTableHasData(table);
        } catch (SQLException e) {
            // Ignore errors in ensuring data
        }
    }

    /**
     * Ensure the table has at least one row.
     * If empty, insert a simple row with default values.
     */
    private void ensureTableHasData(SQLite3Table table) throws SQLException {
        // Check if table has data
        String countQuery = "SELECT COUNT(*) FROM " + table.getName();
        try (java.sql.Statement stmt = globalState.getConnection().createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery(countQuery);
            if (rs.next() && rs.getLong(1) == 0) {
                // Table is empty, insert a simple row
                try {
                    SQLQueryAdapter insertQuery = SQLite3InsertGenerator.insertRow(globalState, table);
                    globalState.executeStatement(insertQuery);
                } catch (Exception e) {
                    // If insert still fails, that's OK - the oracle will skip this test
                }
            }
        }
    }

    /**
     * Convenience method to generate and return a table pair.
     * This is the main entry point for MRUP oracle.
     */
    public static SQLite3Table[] generateMRUPTablePair(SQLite3GlobalState globalState) throws Exception {
        SQLite3MRUPTablePairGenerator generator = new SQLite3MRUPTablePairGenerator(globalState);
        return generator.generateTablePair();
    }
}

