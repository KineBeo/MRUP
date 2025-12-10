package sqlancer.sqlite3.gen;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.sqlite3.SQLite3Errors;
import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;

/**
 * MRUP Table Pair Generator with Disjoint Partitions
 * 
 * Creates two tables with:
 * 1. IDENTICAL schema
 * 2. DISJOINT partition key values (critical for MRUP correctness)
 * 3. Suitable columns for window functions (partition, order, data columns)
 * 
 * Schema Design:
 * - dept VARCHAR(50)      -- Partition key (DISJOINT between t1 and t2)
 * - salary INTEGER        -- Order key 1 (suitable for ORDER BY)
 * - age INTEGER           -- Order key 2 (optional)
 * - c0, c1 (optional)     -- Additional data columns
 * 
 * Disjoint Partition Strategy:
 * - Set A (for t1): ['Finance', 'Engineering', 'HR']
 * - Set B (for t2): ['Sales', 'Marketing', 'Operations']
 * - Guarantee: A ∩ B = ∅
 */
public class SQLite3MRUPTablePairGenerator {

    private final SQLite3GlobalState globalState;
    private final ExpectedErrors errors;

    // Disjoint partition sets
    private static final String[] PARTITION_SET_A = {"Finance", "Engineering", "HR"};
    private static final String[] PARTITION_SET_B = {"Sales", "Marketing", "Operations"};

    public SQLite3MRUPTablePairGenerator(SQLite3GlobalState globalState) {
        this.globalState = globalState;
        this.errors = new ExpectedErrors();
        SQLite3Errors.addTableManipulationErrors(errors);
        SQLite3Errors.addInsertUpdateErrors(errors);
    }

    /**
     * Generates a pair of tables with the same schema and DISJOINT partitions.
     * 
     * @return Array of [table1, table2] with identical schemas but disjoint partition values
     * @throws Exception if table creation fails
     */
    public SQLite3Table[] generateTablePair() throws Exception {
        // Step 1: Generate MRUP-compliant schema
        MRUPSchema schema = generateMRUPSchema();
        
        // Step 2: Get TWO DIFFERENT table names
        final String tableName1 = globalState.getSchema().getFreeTableName();
        String tempTableName2 = globalState.getSchema().getFreeTableName();
        
        // Ensure they are different
        while (tableName1.equals(tempTableName2)) {
            tempTableName2 = globalState.getSchema().getFreeTableName();
        }
        final String tableName2 = tempTableName2;
        
        // Step 3: Create two tables with the same schema
        createTable(tableName1, schema);
        createTable(tableName2, schema);
        
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
        
        // Step 5: Insert data with DISJOINT partitions
        insertDataWithDisjointPartitions(table1, schema, true);  // Use Set A
        insertDataWithDisjointPartitions(table2, schema, false); // Use Set B
        
        // Step 6: Validate disjoint partitions
        validateDisjointPartitions(table1, table2, schema);
        
        // Step 7: Update row counts
        globalState.updateSchema();
        
        return new SQLite3Table[] { table1, table2 };
    }

    /**
     * Generate MRUP-compliant schema with:
     * - Mandatory partition column (VARCHAR)
     * - Mandatory order columns (INTEGER)
     * - Optional additional columns
     */
    private MRUPSchema generateMRUPSchema() {
        MRUPSchema schema = new MRUPSchema();
        
        // 1. Mandatory partition column
        schema.partitionColumn = new ColumnDef("dept", "TEXT");
        
        // 2. Mandatory order columns (1-2 columns)
        schema.orderColumns = new ArrayList<>();
        schema.orderColumns.add(new ColumnDef("salary", "INTEGER"));
        
        if (Randomly.getBoolean()) {
            schema.orderColumns.add(new ColumnDef("age", "INTEGER"));
        }
        
        // 3. Optional additional columns (0-2)
        schema.additionalColumns = new ArrayList<>();
        int numAdditional = Randomly.fromOptions(0, 1, 2);
        for (int i = 0; i < numAdditional; i++) {
            String colName = "c" + i;
            String colType = Randomly.fromOptions("INTEGER", "TEXT", "REAL");
            schema.additionalColumns.add(new ColumnDef(colName, colType));
        }
        
        return schema;
    }

    /**
     * Create a table with MRUP-compliant schema.
     */
    private void createTable(String tableName, MRUPSchema schema) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(tableName);
        sb.append(" (");
        
        // Add partition column
        sb.append(schema.partitionColumn.name).append(" ").append(schema.partitionColumn.type);
        
        // Add order columns
        for (ColumnDef col : schema.orderColumns) {
            sb.append(", ");
            sb.append(col.name).append(" ").append(col.type);
        }
        
        // Add additional columns
        for (ColumnDef col : schema.additionalColumns) {
            sb.append(", ");
            sb.append(col.name).append(" ").append(col.type);
        }
        
        sb.append(")");
        
        String createTableSQL = sb.toString();
        SQLQueryAdapter query = new SQLQueryAdapter(createTableSQL, errors, true);
        globalState.executeStatement(query);
    }

    /**
     * Insert data with DISJOINT partitions.
     * 
     * @param table The table to insert into
     * @param schema The schema definition
     * @param useSetA If true, use partition Set A; otherwise use Set B
     */
    private void insertDataWithDisjointPartitions(SQLite3Table table, MRUPSchema schema, boolean useSetA) {
        String[] partitionSet = useSetA ? PARTITION_SET_A : PARTITION_SET_B;
        
        // Generate 5-20 rows (as per spec)
        int numRows = 5 + Randomly.smallNumber();
        if (numRows > 20) {
            numRows = 20;
        }
        
        // Ensure we have 2-3 partitions per table
        int numPartitions = Randomly.fromOptions(2, 3);
        
        // Select partitions from the set
        List<String> selectedPartitions = new ArrayList<>();
        for (int i = 0; i < numPartitions && i < partitionSet.length; i++) {
            selectedPartitions.add(partitionSet[i]);
        }
        
        // Add NULL partition ONLY in table1 (useSetA=true) to ensure disjoint
        // If both tables had NULL, they would overlap
        boolean includeNullPartition = useSetA && Randomly.getBoolean() && Randomly.getBoolean();
        
        // Silently insert rows (verbose logging removed)
        
        // Insert rows
        for (int i = 0; i < numRows; i++) {
            try {
                // Choose partition for this row
                String partition;
                if (includeNullPartition && Randomly.getBoolean() && Randomly.getBoolean()) {
                    partition = null; // NULL partition
                } else {
                    partition = Randomly.fromList(selectedPartitions);
                }
                
                // Generate row data
                insertRow(table, schema, partition);
                
            } catch (Exception e) {
                // Some inserts may fail due to constraints, that's OK
                // Silently continue
            }
        }
        
        // Ensure at least 5 rows exist
        try {
            ensureMinimumRows(table, schema, selectedPartitions);
        } catch (Exception e) {
            // Silently continue
        }
    }

    /**
     * Insert a single row with specified partition value.
     */
    private void insertRow(SQLite3Table table, MRUPSchema schema, String partitionValue) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(table.getName()).append(" VALUES (");
        
        // Partition column value
        if (partitionValue == null) {
            sb.append("NULL");
        } else {
            sb.append("'").append(partitionValue).append("'");
        }
        
        // Order columns (generate random integers)
        for (int i = 0; i < schema.orderColumns.size(); i++) {
            sb.append(", ");
            ColumnDef col = schema.orderColumns.get(i);
            if (col.type.contains("INTEGER")) {
                // Generate salary: 20000-100000, age: 20-65
                if (col.name.equals("salary")) {
                    long salaryLong = 20000 + Randomly.getNotCachedInteger(0, 80000);
                    // Round to nearest 5000 to create some duplicates
                    long salary = (salaryLong / 5000) * 5000;
                    sb.append(salary);
                } else if (col.name.equals("age")) {
                    sb.append(20 + Randomly.getNotCachedInteger(0, 45));
                } else {
                    sb.append(Randomly.getNotCachedInteger(-1000000, 1000000));
                }
            } else {
                sb.append(Randomly.getNotCachedInteger(-1000000, 1000000));
            }
        }
        
        // Additional columns
        for (ColumnDef col : schema.additionalColumns) {
            sb.append(", ");
            
            // 20% chance of NULL
            if (Randomly.getBoolean() && Randomly.getBoolean() && Randomly.getBoolean()) {
                sb.append("NULL");
            } else if (col.type.contains("INTEGER")) {
                sb.append(Randomly.getNotCachedInteger(-1000000, 1000000));
            } else if (col.type.contains("TEXT")) {
                String text = Randomly.fromOptions("A", "B", "C", "Test", "Data", "Value");
                sb.append("'").append(text).append("'");
            } else if (col.type.contains("REAL")) {
                sb.append(Randomly.getNotCachedInteger(0, 100000) / 1000.0);
            } else {
                sb.append("0");
            }
        }
        
        sb.append(")");
        
        String insertSQL = sb.toString();
        SQLQueryAdapter query = new SQLQueryAdapter(insertSQL, errors, true);
        globalState.executeStatement(query);
    }

    /**
     * Ensure table has at least 5 rows.
     */
    private void ensureMinimumRows(SQLite3Table table, MRUPSchema schema, List<String> partitions) throws Exception {
        String countQuery = "SELECT COUNT(*) FROM " + table.getName();
        try (java.sql.Statement stmt = globalState.getConnection().createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery(countQuery);
            if (rs.next()) {
                int count = rs.getInt(1);
                
                // If less than 5 rows, insert more
                while (count < 5) {
                    try {
                        String partition = Randomly.fromList(partitions);
                        insertRow(table, schema, partition);
                        count++;
                    } catch (Exception e) {
                        // Ignore insert failures
                        break;
                    }
                }
            }
        }
    }

    /**
     * Validate that t1 and t2 have DISJOINT partition values.
     * This is CRITICAL for MRUP correctness.
     */
    private void validateDisjointPartitions(SQLite3Table table1, SQLite3Table table2, MRUPSchema schema) throws SQLException {
        String partitionCol = schema.partitionColumn.name;
        
        // Get distinct partition values from t1
        Set<String> partitions1 = new HashSet<>();
        String query1 = "SELECT DISTINCT " + partitionCol + " FROM " + table1.getName();
        try (java.sql.Statement stmt = globalState.getConnection().createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery(query1);
            while (rs.next()) {
                String val = rs.getString(1);
                partitions1.add(val == null ? "<NULL>" : val);
            }
        }
        
        // Get distinct partition values from t2
        Set<String> partitions2 = new HashSet<>();
        String query2 = "SELECT DISTINCT " + partitionCol + " FROM " + table2.getName();
        try (java.sql.Statement stmt = globalState.getConnection().createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery(query2);
            while (rs.next()) {
                String val = rs.getString(1);
                partitions2.add(val == null ? "<NULL>" : val);
            }
        }
        
        // Check for overlap
        Set<String> overlap = new HashSet<>(partitions1);
        overlap.retainAll(partitions2);
        
        if (!overlap.isEmpty()) {
            throw new SQLException("MRUP CRITICAL ERROR: Partition overlap detected! " +
                                 "t1 and t2 must have DISJOINT partition values. " +
                                 "Overlapping partitions: " + overlap);
        }
        
        // Validation passed - print summary
        // Silent - detailed logging done in SQLite3MRUPOracle
    }

    /**
     * Get row count for a table.
     */
    private int getRowCount(SQLite3Table table) {
        try {
            String countQuery = "SELECT COUNT(*) FROM " + table.getName();
            try (java.sql.Statement stmt = globalState.getConnection().createStatement()) {
                java.sql.ResultSet rs = stmt.executeQuery(countQuery);
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
        return 0;
    }

    /**
     * Display table contents for verification (temporary debug method).
     */
    private void displayTableContents(SQLite3Table table, String label) {
        try {
            System.out.println("\n[DEBUG] " + label + " (" + table.getName() + "):");
            System.out.println("─────────────────────────────────────────────────────────");
            
            // Get all data
            String query = "SELECT * FROM " + table.getName();
            try (java.sql.Statement stmt = globalState.getConnection().createStatement()) {
                java.sql.ResultSet rs = stmt.executeQuery(query);
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                // Print column headers
                System.out.print("| ");
                for (int i = 1; i <= columnCount; i++) {
                    System.out.printf("%-15s | ", metaData.getColumnName(i));
                }
                System.out.println();
                
                // Print separator
                System.out.print("| ");
                for (int i = 1; i <= columnCount; i++) {
                    System.out.print("--------------- | ");
                }
                System.out.println();
                
                // Print rows
                int rowNum = 0;
                while (rs.next()) {
                    System.out.print("| ");
                    for (int i = 1; i <= columnCount; i++) {
                        String value = rs.getString(i);
                        if (value == null) {
                            value = "<NULL>";
                        }
                        // Truncate long values
                        if (value.length() > 15) {
                            value = value.substring(0, 12) + "...";
                        }
                        System.out.printf("%-15s | ", value);
                    }
                    System.out.println();
                    rowNum++;
                }
                
                System.out.println("─────────────────────────────────────────────────────────");
                System.out.println("Total rows: " + rowNum);
            }
        } catch (Exception e) {
            System.err.println("[DEBUG] Error displaying table: " + e.getMessage());
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

    /**
     * Helper class to represent MRUP schema structure.
     */
    private static class MRUPSchema {
        ColumnDef partitionColumn;
        List<ColumnDef> orderColumns;
        List<ColumnDef> additionalColumns;
    }

    /**
     * Helper class to represent a column definition.
     */
    private static class ColumnDef {
        String name;
        String type;
        
        ColumnDef(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}
