package sqlancer.sqlite3.oracle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Column;
import sqlancer.sqlite3.schema.SQLite3Schema.SQLite3Table;

/**
 * File-based logger for MRUP test cases.
 * 
 * Instead of logging to terminal (which slows down the oracle), this class
 * writes detailed test case information to files in the mrup_logs/ directory.
 * 
 * Each test case gets its own file with complete information from Step 1 to Step 5.
 */
public class SQLite3MRUPTestCaseLogger {
    
    private static final String LOG_DIR = "mrup_logs";
    private static final boolean LOGGING_ENABLED = Boolean.parseBoolean(
        System.getProperty("mrup.logging.enabled", "false")
    );
    
    private StringBuilder logBuffer;
    private String testCaseId;
    private long startTime;
    
    public SQLite3MRUPTestCaseLogger() {
        this.logBuffer = new StringBuilder();
        this.startTime = System.currentTimeMillis();
        this.testCaseId = generateTestCaseId();
    }
    
    /**
     * Check if detailed logging is enabled.
     */
    public static boolean isLoggingEnabled() {
        return LOGGING_ENABLED;
    }
    
    /**
     * Generate unique test case ID.
     */
    private String generateTestCaseId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        return "mrup_" + sdf.format(new Date());
    }
    
    /**
     * Log test case header.
     */
    public void logHeader() {
        if (!LOGGING_ENABLED) return;
        
        logBuffer.append("╔═══════════════════════════════════════════════════════════════════╗\n");
        logBuffer.append("║                    MRUP Oracle Test Case                          ║\n");
        logBuffer.append("╚═══════════════════════════════════════════════════════════════════╝\n\n");
        logBuffer.append("Test Case ID: ").append(testCaseId).append("\n");
        logBuffer.append("Timestamp: ").append(new Date()).append("\n\n");
    }
    
    /**
     * Log Step 1 & 2: Table schema and data.
     */
    public void logTableInfo(SQLite3Table t1, SQLite3Table t2, List<SQLite3Column> columns,
                             List<List<String>> t1Data, List<List<String>> t2Data,
                             Set<String> t1Partitions, Set<String> t2Partitions) {
        if (!LOGGING_ENABLED) return;
        
        logBuffer.append("┌───────────────────────────────────────────────────────────────────┐\n");
        logBuffer.append("│ STEP 1 & 2: Table Schema and Data (Disjoint Partitions)          │\n");
        logBuffer.append("└───────────────────────────────────────────────────────────────────┘\n\n");
        
        // Schema
        StringBuilder schemaStr = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) schemaStr.append(", ");
            schemaStr.append(columns.get(i).getName()).append(" ").append(columns.get(i).getType());
        }
        logBuffer.append("📋 Schema (both tables):\n");
        logBuffer.append("   ").append(schemaStr.toString()).append("\n\n");
        
        // Table 1 data
        logBuffer.append("📊 Table t1 (").append(t1.getName()).append("):\n");
        logTableData(columns, t1Data);
        
        // Table 2 data
        logBuffer.append("\n📊 Table t2 (").append(t2.getName()).append("):\n");
        logTableData(columns, t2Data);
        
        // Partition verification
        Set<String> overlap = new java.util.HashSet<>(t1Partitions);
        overlap.retainAll(t2Partitions);
        
        logBuffer.append("\n✓ Partition Verification:\n");
        logBuffer.append("   t1 partitions: ").append(t1Partitions).append("\n");
        logBuffer.append("   t2 partitions: ").append(t2Partitions).append("\n");
        logBuffer.append("   Overlap: ").append(overlap.isEmpty() ? "NONE ✓" : overlap + " ✗").append("\n");
        logBuffer.append("   Status: ").append(overlap.isEmpty() ? "DISJOINT ✓" : "NOT DISJOINT ✗").append("\n");
        logBuffer.append("─────────────────────────────────────────────────────────────────────\n\n");
    }
    
    /**
     * Helper to log table data.
     */
    private void logTableData(List<SQLite3Column> columns, List<List<String>> data) {
        // Header
        logBuffer.append("   ");
        for (SQLite3Column col : columns) {
            logBuffer.append(String.format("%-12s ", col.getName()));
        }
        logBuffer.append("\n");
        
        // Separator
        logBuffer.append("   ");
        for (int i = 0; i < columns.size(); i++) {
            logBuffer.append("------------ ");
        }
        logBuffer.append("\n");
        
        // Data rows
        for (List<String> row : data) {
            logBuffer.append("   ");
            for (String value : row) {
                String displayValue = value;
                if (displayValue.equals("NULL")) {
                    displayValue = "<NULL>";
                } else if (displayValue.length() > 10) {
                    displayValue = displayValue.substring(0, 10);
                }
                logBuffer.append(String.format("%-12s ", displayValue));
            }
            logBuffer.append("\n");
        }
        logBuffer.append("   Total rows: ").append(data.size()).append("\n");
    }
    
    /**
     * Log Step 3: Window function generation.
     */
    public void logWindowFunctionGeneration(String functionType, String windowSpec, 
                                            String fullFunction, boolean mutationApplied,
                                            Map<String, Boolean> constraints) {
        if (!LOGGING_ENABLED) return;
        
        logBuffer.append("┌───────────────────────────────────────────────────────────────────┐\n");
        logBuffer.append("│ STEP 3: Window Function Generation (Constraint Verification)      │\n");
        logBuffer.append("└───────────────────────────────────────────────────────────────────┘\n\n");
        
        logBuffer.append("🎯 Generated Window Function:\n");
        logBuffer.append("   ").append(fullFunction).append("\n\n");
        
        logBuffer.append("📋 Constraint Verification:\n");
        for (Map.Entry<String, Boolean> entry : constraints.entrySet()) {
            logBuffer.append("   ").append(entry.getKey()).append(": ");
            logBuffer.append(entry.getValue() ? "✓ PASS" : "✗ FAIL").append("\n");
        }
        
        if (mutationApplied) {
            logBuffer.append("\n🔄 Mutation: Applied\n");
        }
        
        logBuffer.append("\n─────────────────────────────────────────────────────────────────────\n\n");
    }
    
    /**
     * Log Step 4: Generated queries.
     */
    public void logQueries(String q1, String q2, String qUnion) {
        if (!LOGGING_ENABLED) return;
        
        logBuffer.append("┌───────────────────────────────────────────────────────────────────┐\n");
        logBuffer.append("│ STEP 4: Generated Queries                                         │\n");
        logBuffer.append("└───────────────────────────────────────────────────────────────────┘\n\n");
        
        logBuffer.append("📝 Q1 (on t1):\n");
        logBuffer.append("   ").append(q1).append("\n\n");
        
        logBuffer.append("📝 Q2 (on t2):\n");
        logBuffer.append("   ").append(q2).append("\n\n");
        
        logBuffer.append("📝 Q_union (on t1 UNION ALL t2):\n");
        logBuffer.append("   ").append(qUnion).append("\n\n");
        
        logBuffer.append("─────────────────────────────────────────────────────────────────────\n\n");
    }
    
    /**
     * Log Step 5: Result comparison with detailed results.
     */
    public void logComparison(int expectedCardinality, int actualCardinality,
                             String partitionColumn, List<String> orderByColumns,
                             boolean passed,
                             List<List<String>> q1Results,
                             List<List<String>> q2Results,
                             List<List<String>> qUnionResults) {
        if (!LOGGING_ENABLED) return;
        
        logBuffer.append("┌───────────────────────────────────────────────────────────────────┐\n");
        logBuffer.append("│ STEP 5: Result Comparison (Phase 3: MRUP Normalization)          │\n");
        logBuffer.append("└───────────────────────────────────────────────────────────────────┘\n\n");
        
        logBuffer.append("Layer 1: Cardinality Check\n");
        logBuffer.append("   Expected: ").append(expectedCardinality).append("\n");
        logBuffer.append("   Actual:   ").append(actualCardinality).append("\n");
        logBuffer.append("   ").append(expectedCardinality == actualCardinality ? "✓ PASS" : "✗ FAIL").append("\n\n");
        
        logBuffer.append("Layer 2: MRUP Normalization\n");
        logBuffer.append("   Sorting by: partition (").append(partitionColumn).append(") → ");
        logBuffer.append("ORDER BY (").append(String.join(", ", orderByColumns)).append(") → wf_result\n");
        logBuffer.append("   ✓ Results normalized\n\n");
        
        logBuffer.append("Layer 3: Per-Partition Comparison\n");
        logBuffer.append("   ").append(passed ? "✓ PASS - All partitions match!" : "✗ FAIL - Mismatch detected!").append("\n\n");
        
        // Add detailed results
        logBuffer.append("───────────────────────────────────────────────────────────────────────\n");
        logBuffer.append("DETAILED RESULTS (for manual verification)\n");
        logBuffer.append("───────────────────────────────────────────────────────────────────────\n\n");
        
        // H(t1)
        logBuffer.append("H(t1) - Window function on t1:\n");
        logResultSet(q1Results, 10);
        
        // H(t2)
        logBuffer.append("\nH(t2) - Window function on t2:\n");
        logResultSet(q2Results, 10);
        
        // Expected: H(t1) ∪ H(t2)
        List<List<String>> expected = new ArrayList<>();
        expected.addAll(q1Results);
        expected.addAll(q2Results);
        logBuffer.append("\nExpected: H(t1) ∪ H(t2):\n");
        logResultSet(expected, 10);
        
        // Actual: H(t_union)
        logBuffer.append("\nActual: H(t_union) - Window function on (t1 UNION ALL t2):\n");
        logResultSet(qUnionResults, 10);
        
        logBuffer.append("\n═══════════════════════════════════════════════════════════════════\n");
        logBuffer.append(passed ? "✅ MRUP TEST PASSED" : "❌ MRUP TEST FAILED").append("\n");
        logBuffer.append("═══════════════════════════════════════════════════════════════════\n\n");
    }
    
    /**
     * Helper to log a result set.
     */
    private void logResultSet(List<List<String>> results, int maxRows) {
        if (results.isEmpty()) {
            logBuffer.append("   (empty result set)\n");
            return;
        }
        
        // Show first few rows
        int rowsToShow = Math.min(maxRows, results.size());
        for (int i = 0; i < rowsToShow; i++) {
            logBuffer.append("   Row ").append(i + 1).append(": ");
            logBuffer.append(results.get(i)).append("\n");
        }
        
        if (results.size() > maxRows) {
            logBuffer.append("   ... (").append(results.size() - maxRows).append(" more rows)\n");
        }
        
        logBuffer.append("   Total: ").append(results.size()).append(" rows\n");
    }
    
    /**
     * Log bug details (if test failed).
     */
    public void logBugDetails(String partition, String source, 
                             List<List<String>> expected, List<List<String>> actual) {
        if (!LOGGING_ENABLED) return;
        
        logBuffer.append("\n╔═══════════════════════════════════════════════════════════════════╗\n");
        logBuffer.append("║                    BUG FOUND: MRUP VIOLATION                      ║\n");
        logBuffer.append("╚═══════════════════════════════════════════════════════════════════╝\n\n");
        
        logBuffer.append("Partition: ").append(partition).append("\n");
        logBuffer.append("Source: ").append(source).append("\n\n");
        
        logBuffer.append("Expected (").append(source).append("[").append(partition).append("]):\n");
        for (int i = 0; i < Math.min(5, expected.size()); i++) {
            logBuffer.append("  Row ").append(i + 1).append(": ").append(expected.get(i)).append("\n");
        }
        if (expected.size() > 5) {
            logBuffer.append("  ... (").append(expected.size() - 5).append(" more rows)\n");
        }
        logBuffer.append("\n");
        
        logBuffer.append("Actual (Q_union[").append(partition).append("]):\n");
        for (int i = 0; i < Math.min(5, actual.size()); i++) {
            logBuffer.append("  Row ").append(i + 1).append(": ").append(actual.get(i)).append("\n");
        }
        if (actual.size() > 5) {
            logBuffer.append("  ... (").append(actual.size() - 5).append(" more rows)\n");
        }
        logBuffer.append("\n");
    }
    
    /**
     * Write log buffer to file.
     */
    public void writeToFile() {
        if (!LOGGING_ENABLED) return;
        
        try {
            // Create log directory if it doesn't exist
            Path logDirPath = Paths.get(LOG_DIR);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
            }
            
            // Write to file
            String filename = LOG_DIR + "/" + testCaseId + ".log";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                writer.write(logBuffer.toString());
                
                // Add execution time
                long duration = System.currentTimeMillis() - startTime;
                writer.write("\nExecution Time: " + duration + " ms\n");
            }
            
        } catch (IOException e) {
            // Silently fail - don't disrupt oracle execution
            System.err.println("[MRUP Logger] Failed to write log file: " + e.getMessage());
        }
    }
    
    /**
     * Get test case ID.
     */
    public String getTestCaseId() {
        return testCaseId;
    }
}

