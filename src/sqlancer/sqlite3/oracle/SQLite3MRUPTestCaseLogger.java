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
        
        // Machine-readable header
        logBuffer.append("METRICS_START|\n");
    }
    
    /**
     * Log Step 1 & 2: Table schema and data.
     */
    public void logTableInfo(SQLite3Table t1, SQLite3Table t2, List<SQLite3Column> columns,
                             List<List<String>> t1Data, List<List<String>> t2Data,
                             Set<String> t1Partitions, Set<String> t2Partitions) {
        if (!LOGGING_ENABLED) return;
        
        // Machine-readable schema metrics
        int numCols = columns.size();
        int numInteger = 0, numReal = 0, numText = 0, numNulls = 0, totalValues = 0;
        for (SQLite3Column col : columns) {
            String type = col.getType().toString();
            if (type.contains("INT")) numInteger++;
            else if (type.contains("REAL")) numReal++;
            else if (type.contains("TEXT")) numText++;
        }
        
        // Count NULLs in data
        for (List<String> row : t1Data) {
            for (String val : row) {
                totalValues++;
                if (val == null || val.equals("NULL") || val.equals("<NULL>")) numNulls++;
            }
        }
        for (List<String> row : t2Data) {
            for (String val : row) {
                totalValues++;
                if (val == null || val.equals("NULL") || val.equals("<NULL>")) numNulls++;
            }
        }
        
        logBuffer.append(String.format("METRICS_SCHEMA|num_cols=%d|type_int=%d|type_real=%d|type_text=%d|null_count=%d|total_values=%d|\n",
            numCols, numInteger, numReal, numText, numNulls, totalValues));
        logBuffer.append(String.format("METRICS_TABLES|t1_rows=%d|t2_rows=%d|\n", t1Data.size(), t2Data.size()));
        
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
     * Log constraint verification (now separate from mutation pipeline).
     */
    /**
     * Helper method to count occurrences of a pattern in a string.
     */
    private int countOccurrences(String str, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(',', index)) != -1) {
            if (index > str.indexOf("ORDER BY") && index < str.indexOf(" OVER")) {
                count++;
            }
            index++;
        }
        return count;
    }
    
    public void logConstraintVerification(Map<String, Boolean> constraints) {
        if (!LOGGING_ENABLED) return;
        
        // Machine-readable constraint metrics
        logBuffer.append(String.format("METRICS_CONSTRAINTS|C0=%s|C1=%s|C2=%s|C3=%s|C4=%s|C5=%s|\n",
            constraints.get("C0"), constraints.get("C1"), constraints.get("C2"),
            constraints.get("C3"), constraints.get("C4"), constraints.get("C5")));
        
        logBuffer.append("┌───────────────────────────────────────────────────────────────────┐\n");
        logBuffer.append("│ STEP 3B: Constraint Verification                                  │\n");
        logBuffer.append("└───────────────────────────────────────────────────────────────────┘\n\n");
        
        logBuffer.append("📋 Constraint Verification:\n");
        for (Map.Entry<String, Boolean> entry : constraints.entrySet()) {
            logBuffer.append("   ").append(entry.getKey()).append(": ");
            logBuffer.append(entry.getValue() ? "✓ PASS" : "✗ FAIL").append("\n");
        }
        
        logBuffer.append("\n─────────────────────────────────────────────────────────────────────\n\n");
    }
    
    /**
     * Log complete mutation pipeline (replaces individual mutation logs).
     * Shows end-to-end transformation from base window function to final mutated query.
     * 
     * CORRECTED ORDER: Window Spec → Identity → CASE
     * This ensures identity mutations target the window function, not the CASE wrapper.
     */
    public void logMutationPipeline(
            String baseWindowFunction,
            String windowSpecMutation,
            boolean windowSpecMutated,
            String afterWindowSpecMutation,
            String identityMutationType,
            String afterIdentityMutation,
            String caseMutationType,
            String afterCaseMutation,
            String finalWindowFunction) {
        if (!LOGGING_ENABLED) return;
        
        // Machine-readable mutation metrics
        logBuffer.append(String.format("METRICS_MUTATIONS|window_spec=%s|identity=%s|case_when=%s|\n",
            windowSpecMutated ? windowSpecMutation : "None",
            identityMutationType,
            caseMutationType));
        
        // Extract query characteristics from baseWindowFunction
        String funcType = "UNKNOWN";
        if (baseWindowFunction.contains("SUM(")) funcType = "SUM";
        else if (baseWindowFunction.contains("AVG(")) funcType = "AVG";
        else if (baseWindowFunction.contains("COUNT(")) funcType = "COUNT";
        else if (baseWindowFunction.contains("MIN(")) funcType = "MIN";
        else if (baseWindowFunction.contains("MAX(")) funcType = "MAX";
        else if (baseWindowFunction.contains("ROW_NUMBER(")) funcType = "ROW_NUMBER";
        else if (baseWindowFunction.contains("RANK(")) funcType = "RANK";
        else if (baseWindowFunction.contains("DENSE_RANK(")) funcType = "DENSE_RANK";
        
        int numOrderCols = countOccurrences(baseWindowFunction, "ORDER BY.*?,") + 1;
        if (!baseWindowFunction.contains("ORDER BY")) numOrderCols = 0;
        
        boolean hasFrame = baseWindowFunction.contains("ROWS") || baseWindowFunction.contains("RANGE");
        String frameType = "NONE";
        if (baseWindowFunction.contains("ROWS")) frameType = "ROWS";
        else if (baseWindowFunction.contains("RANGE")) frameType = "RANGE";
        
        logBuffer.append(String.format("METRICS_QUERY|func_type=%s|order_by_cols=%d|has_frame=%s|frame_type=%s|\n",
            funcType, numOrderCols, hasFrame, frameType));
        
        logBuffer.append("┌───────────────────────────────────────────────────────────────────┐\n");
        logBuffer.append("│ STEP 3: Mutation Pipeline (End-to-End Query Transformation)      │\n");
        logBuffer.append("└───────────────────────────────────────────────────────────────────┘\n\n");
        
        logBuffer.append("🔹 BASE WINDOW FUNCTION:\n");
        logBuffer.append("   ").append(baseWindowFunction).append("\n\n");
        
        // Phase 1: Window Spec Mutations
        logBuffer.append("📍 PHASE 1: Window Spec Mutations\n");
        if (windowSpecMutated) {
            logBuffer.append("   ✓ Applied: ").append(windowSpecMutation).append("\n");
            logBuffer.append("   Result:    ").append(afterWindowSpecMutation).append("\n");
        } else {
            logBuffer.append("   ✗ Not Applied\n");
        }
        logBuffer.append("\n");
        
        // Stage 1: Identity Wrapper Mutations (NOW BEFORE CASE!)
        logBuffer.append("📍 STAGE 1: Identity Wrapper Mutations\n");
        if (!identityMutationType.equals("None")) {
            logBuffer.append("   ✓ Applied: ").append(identityMutationType).append("\n");
            logBuffer.append("   Result:    ").append(afterIdentityMutation).append("\n");
        } else {
            logBuffer.append("   ✗ Not Applied\n");
        }
        logBuffer.append("\n");
        
        // Phase 3: CASE WHEN Mutations (NOW AFTER IDENTITY!)
        logBuffer.append("📍 PHASE 3: CASE WHEN Mutations\n");
        logBuffer.append("   ✓ Applied: ").append(caseMutationType).append("\n");
        logBuffer.append("   Result:    ").append(afterCaseMutation).append("\n\n");
        
        // Summary
        logBuffer.append("🎯 FINAL QUERY:\n");
        logBuffer.append("   ").append(finalWindowFunction).append("\n\n");
        
        logBuffer.append("📊 MUTATION SUMMARY:\n");
        logBuffer.append("   • Window Spec:  ").append(windowSpecMutated ? "✓ " + windowSpecMutation : "✗ None").append("\n");
        logBuffer.append("   • Identity:     ").append(!identityMutationType.equals("None") ? "✓ " + identityMutationType : "✗ None").append("\n");
        logBuffer.append("   • CASE WHEN:    ✓ ").append(caseMutationType).append("\n");
        
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
        
        // Machine-readable comparator metrics
        boolean layer1Pass = (expectedCardinality == actualCardinality);
        boolean layer2Pass = true; // normalization always succeeds if layer1 passes
        boolean layer3Pass = passed;
        
        logBuffer.append(String.format("METRICS_COMPARATOR|layer1=%s|layer2=%s|layer3=%s|overall=%s|\n",
            layer1Pass, layer2Pass, layer3Pass, passed));
        
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
                
                // Machine-readable timing metric
                writer.write("METRICS_TIMING|duration_ms=" + duration + "|\n");
                writer.write("METRICS_END|\n");
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

