package sqlancer.sqlite3.oracle;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import sqlancer.sqlite3.SQLite3GlobalState;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;

/**
 * Bug Reproducer for MRUP Oracle
 * 
 * When a bug is detected, this class generates a standalone SQL script
 * that can be used to reproduce and verify the bug outside of SQLancer.
 */
public class SQLite3MRUPBugReproducer {

    /**
     * Generate a reproducible bug report SQL script
     */
    public static void generateBugReport(
            SQLite3GlobalState globalState,
            SQLite3Table t1,
            SQLite3Table t2,
            String q1,
            String q2,
            String qUnion,
            String bugDescription) throws SQLException, IOException {
        
        StringBuilder script = new StringBuilder();
        
        // Header
        script.append("-- MRUP Oracle Bug Report\n");
        script.append("-- Generated: ").append(java.time.LocalDateTime.now()).append("\n");
        script.append("-- Bug Description: ").append(bugDescription).append("\n");
        script.append("--\n");
        script.append("-- This script reproduces a potential bug where:\n");
        script.append("-- H(t1 UNION ALL t2) != H(t1) UNION ALL H(t2)\n");
        script.append("-- where H is a window function\n\n");
        
        // Clean up
        script.append("-- Clean up\n");
        script.append("DROP TABLE IF EXISTS t1;\n");
        script.append("DROP TABLE IF EXISTS t2;\n\n");
        
        // Create tables with schema
        script.append("-- Create table t1\n");
        script.append(generateCreateTableStatement(t1, "t1"));
        script.append("\n");
        
        script.append("-- Create table t2 (same schema)\n");
        script.append(generateCreateTableStatement(t2, "t2"));
        script.append("\n");
        
        // Insert data
        script.append("-- Insert data into t1\n");
        script.append(generateInsertStatements(globalState, t1, "t1"));
        script.append("\n");
        
        script.append("-- Insert data into t2\n");
        script.append(generateInsertStatements(globalState, t2, "t2"));
        script.append("\n");
        
        // Configure SQLite for better display
        script.append("-- Configure display mode for better readability\n");
        script.append(".mode column\n");
        script.append(".headers on\n");
        script.append(".nullvalue <NULL>\n\n");
        
        // Display tables
        script.append("-- Display tables\n");
        script.append(".print \"=== Table t1 ===\"\n");
        script.append("SELECT * FROM t1;\n\n");
        script.append(".print \"\\n=== Table t2 ===\"\n");
        script.append("SELECT * FROM t2;\n\n");
        
        // Execute queries
        script.append("-- Execute Q1 (window function on t1)\n");
        script.append(".print \"\\n=== Q1 Results ===\"\n");
        String q1Fixed = q1.replaceAll("\\b" + t1.getName() + "\\b", "t1");
        script.append(q1Fixed).append(";\n\n");
        
        script.append("-- Execute Q2 (window function on t2)\n");
        script.append(".print \"\\n=== Q2 Results ===\"\n");
        String q2Fixed = q2.replaceAll("\\b" + t2.getName() + "\\b", "t2");
        script.append(q2Fixed).append(";\n\n");
        
        script.append("-- Execute Q_union (window function on UNION ALL)\n");
        script.append(".print \"\\n=== Q_union Results ===\"\n");
        String qUnionFixed = qUnion.replaceAll("\\b" + t1.getName() + "\\b", "t1")
                                   .replaceAll("\\b" + t2.getName() + "\\b", "t2");
        script.append(qUnionFixed).append(";\n\n");
        
        // Expected vs Actual
        script.append("-- Expected result (Q1 UNION ALL Q2)\n");
        script.append(".print \"\\n=== Expected (Q1 ∪ Q2) ===\"\n");
        script.append("SELECT * FROM (\n");
        script.append("    ").append(q1Fixed).append("\n");
        script.append("    UNION ALL\n");
        script.append("    ").append(q2Fixed).append("\n");
        script.append(") ORDER BY 1, 2;\n\n");
        
        script.append("-- Actual result (Q_union)\n");
        script.append(".print \"\\n=== Actual (Q_union) ===\"\n");
        script.append(qUnionFixed);
        script.append(" ORDER BY 1, 2;\n\n");
        
        // Verification
        script.append("-- Verification: Check for differences\n");
        script.append(".print \"\\n=== Verification ===\"\n");
        script.append(".print \"If no rows returned, results match (no bug).\"\n");
        script.append(".print \"If rows returned, there's a mismatch (bug confirmed).\"\n");
        script.append(".print \"\"\n\n");
        
        // Write to file in bug_reports directory
        String bugReportsDir = "bug_reports";
        java.io.File dir = new java.io.File(bugReportsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String filename = bugReportsDir + "/bug_report_" + System.currentTimeMillis() + ".sql";
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(script.toString());
        }
        
        System.out.println("\n[MRUP] Bug report saved to: " + filename);
    }
    
    /**
     * Generate CREATE TABLE statement
     */
    private static String generateCreateTableStatement(SQLite3Table table, String newName) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(newName).append(" (\n");
        
        List<SQLite3Column> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            SQLite3Column col = columns.get(i);
            sb.append("    ").append(col.getName()).append(" ");
            sb.append(col.getType().toString());
            
            if (i < columns.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        
        sb.append(");\n");
        return sb.toString();
    }
    
    /**
     * Generate INSERT statements for table data
     */
    private static String generateInsertStatements(
            SQLite3GlobalState globalState,
            SQLite3Table table,
            String newName) throws SQLException {
        
        StringBuilder sb = new StringBuilder();
        
        try (Statement stmt = globalState.getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + table.getName());
            
            List<SQLite3Column> columns = table.getColumns();
            
            while (rs.next()) {
                sb.append("INSERT INTO ").append(newName).append(" (");
                
                // Column names
                for (int i = 0; i < columns.size(); i++) {
                    sb.append(columns.get(i).getName());
                    if (i < columns.size() - 1) sb.append(", ");
                }
                
                sb.append(") VALUES (");
                
                // Values
                for (int i = 0; i < columns.size(); i++) {
                    Object value = rs.getObject(columns.get(i).getName());
                    
                    if (value == null) {
                        sb.append("NULL");
                    } else if (value instanceof String) {
                        // Escape single quotes
                        String strValue = value.toString().replace("'", "''");
                        sb.append("'").append(strValue).append("'");
                    } else if (value instanceof byte[]) {
                        // BLOB - convert to hex
                        byte[] bytes = (byte[]) value;
                        sb.append("x'");
                        for (byte b : bytes) {
                            sb.append(String.format("%02x", b));
                        }
                        sb.append("'");
                    } else {
                        sb.append(value.toString());
                    }
                    
                    if (i < columns.size() - 1) sb.append(", ");
                }
                
                sb.append(");\n");
            }
        }
        
        return sb.toString();
    }
}

