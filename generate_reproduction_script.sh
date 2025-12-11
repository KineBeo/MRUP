#!/bin/bash

# Script to generate a standalone SQL reproduction script from MRUP log file
# Usage: ./generate_reproduction_script.sh <log_file>

if [ $# -eq 0 ]; then
    echo "Usage: $0 <mrup_log_file>"
    echo "Example: $0 mrup_logs/mrup_20251210_075620_708.log"
    exit 1
fi

LOG_FILE="$1"

if [ ! -f "$LOG_FILE" ]; then
    echo "Error: Log file not found: $LOG_FILE"
    exit 1
fi

# Extract test case ID from filename
TEST_ID=$(basename "$LOG_FILE" .log)
OUTPUT_FILE="reproduction_${TEST_ID}.sql"

echo "Generating SQL reproduction script from: $LOG_FILE"
echo "Output: $OUTPUT_FILE"

# Start generating the SQL script
cat > "$OUTPUT_FILE" << 'EOF_HEADER'
-- ═══════════════════════════════════════════════════════════════════
-- MRUP Oracle - Manual Verification Script
-- ═══════════════════════════════════════════════════════════════════
-- This script allows you to manually verify the MRUP metamorphic relation:
--   H(t1 ∪ t2) = H(t1) ∪ H(t2)
--
-- Instructions:
--   1. Run this script in SQLite3: sqlite3 < reproduction_script.sql
--   2. Compare the results of Q1, Q2, Q_union
--   3. Verify that H(t_union) = H(t1) ∪ H(t2)
-- ═══════════════════════════════════════════════════════════════════

.mode column
.headers on
.nullvalue <NULL>

EOF_HEADER

# Extract table names from the log
T1_NAME=$(grep -oP "Table t1 \(\K[^)]+(?=\))" "$LOG_FILE" | head -1)
T2_NAME=$(grep -oP "Table t2 \(\K[^)]+(?=\))" "$LOG_FILE" | head -1)

# Extract schema
SCHEMA=$(grep -A 1 "📋 Schema" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')

# Add header info
cat >> "$OUTPUT_FILE" << EOF

-- ───────────────────────────────────────────────────────────────────
-- Test Case Information
-- ───────────────────────────────────────────────────────────────────
EOF

grep "Test Case ID:" "$LOG_FILE" | sed 's/^/-- /' >> "$OUTPUT_FILE"
grep "Timestamp:" "$LOG_FILE" | sed 's/^/-- /' >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

-- ───────────────────────────────────────────────────────────────────
-- STEP 1: Create Tables
-- ───────────────────────────────────────────────────────────────────

DROP TABLE IF EXISTS ${T1_NAME};
DROP TABLE IF EXISTS ${T2_NAME};

CREATE TABLE ${T1_NAME} (${SCHEMA});
CREATE TABLE ${T2_NAME} (${SCHEMA});

-- ───────────────────────────────────────────────────────────────────
-- STEP 2: Insert Data into t1 (${T1_NAME})
-- ───────────────────────────────────────────────────────────────────

EOF

# Extract t1 data and convert to INSERT statements
awk '/📊 Table t1/,/Total rows:/ {
    if ($0 ~ /^[[:space:]]*[^-│┌└📊Total]/) {
        gsub(/^[[:space:]]+/, "");
        gsub(/[[:space:]]+$/, "");
        if (NF >= 3 && $0 !~ /dept.*salary.*age/) {
            dept=$1; salary=$2; age=$3;
            # Handle NULL values
            if (dept == "") dept="NULL"; else dept="'"'"'" dept "'"'"'";
            if (salary == "") salary="NULL";
            if (age == "") age="NULL";
            print "INSERT INTO '"${T1_NAME}"' VALUES (" dept ", " salary ", " age ");";
        }
    }
}' "$LOG_FILE" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

-- ───────────────────────────────────────────────────────────────────
-- STEP 3: Insert Data into t2 (${T2_NAME})
-- ───────────────────────────────────────────────────────────────────

EOF

# Extract t2 data and convert to INSERT statements
awk '/📊 Table t2/,/Total rows:/ {
    if ($0 ~ /^[[:space:]]*[^-│┌└📊Total]/) {
        gsub(/^[[:space:]]+/, "");
        gsub(/[[:space:]]+$/, "");
        if (NF >= 3 && $0 !~ /dept.*salary.*age/) {
            dept=$1; salary=$2; age=$3;
            # Handle NULL values
            if (dept == "") dept="NULL"; else dept="'"'"'" dept "'"'"'";
            if (salary == "") salary="NULL";
            if (age == "") age="NULL";
            print "INSERT INTO '"${T2_NAME}"' VALUES (" dept ", " salary ", " age ");";
        }
    }
}' "$LOG_FILE" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

-- ───────────────────────────────────────────────────────────────────
-- STEP 4: Verify Table Contents
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "TABLE CONTENTS"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "--- Table t1 (${T1_NAME}) ---"
SELECT * FROM ${T1_NAME};

.print ""
.print "--- Table t2 (${T2_NAME}) ---"
SELECT * FROM ${T2_NAME};

-- ───────────────────────────────────────────────────────────────────
-- STEP 5: Execute Window Function Queries
-- ───────────────────────────────────────────────────────────────────

EOF

# Extract Q1, Q2, Q_union queries
Q1=$(grep -A 1 "📝 Q1" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')
Q2=$(grep -A 1 "📝 Q2" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')
Q_UNION=$(grep -A 1 "📝 Q_union" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')

cat >> "$OUTPUT_FILE" << EOF

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "WINDOW FUNCTION QUERIES"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "--- Q1: Window function on t1 ---"
${Q1};

.print ""
.print "--- Q2: Window function on t2 ---"
${Q2};

.print ""
.print "--- Q_union: Window function on (t1 UNION ALL t2) ---"
${Q_UNION};

-- ───────────────────────────────────────────────────────────────────
-- STEP 6: Manual Verification
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "MANUAL VERIFICATION"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "Expected behavior (MRUP metamorphic relation):"
.print "  H(t_union) should equal H(t1) ∪ H(t2)"
.print ""
.print "To verify:"
.print "  1. Compare the results of Q1 and Q2 above"
.print "  2. Mentally compute H(t1) ∪ H(t2) (union of Q1 and Q2 results)"
.print "  3. Compare with Q_union results"
.print "  4. They should match exactly (same rows, same order after sorting by partition)"
.print ""

-- ───────────────────────────────────────────────────────────────────
-- STEP 7: Expected Results (from MRUP log)
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "EXPECTED RESULTS (from MRUP Oracle log)"
.print "═══════════════════════════════════════════════════════════════════"
.print ""

EOF

# Extract expected results from log
awk '/H\(t1\) - Window function on t1:/,/Total:/ {
    if ($0 ~ /Row [0-9]+:/) {
        print ".print \"" $0 "\"";
    } else if ($0 ~ /Total:/) {
        print ".print \"" $0 "\"";
    }
}' "$LOG_FILE" >> "$OUTPUT_FILE"

echo '.print ""' >> "$OUTPUT_FILE"

awk '/H\(t2\) - Window function on t2:/,/Total:/ {
    if ($0 ~ /Row [0-9]+:/) {
        print ".print \"" $0 "\"";
    } else if ($0 ~ /Total:/) {
        print ".print \"" $0 "\"";
    }
}' "$LOG_FILE" >> "$OUTPUT_FILE"

echo '.print ""' >> "$OUTPUT_FILE"

awk '/Expected: H\(t1\) ∪ H\(t2\):/,/Total:/ {
    if ($0 ~ /Row [0-9]+:/) {
        print ".print \"" $0 "\"";
    } else if ($0 ~ /Total:/) {
        print ".print \"" $0 "\"";
    }
}' "$LOG_FILE" >> "$OUTPUT_FILE"

echo '.print ""' >> "$OUTPUT_FILE"

awk '/Actual: H\(t_union\)/,/Total:/ {
    if ($0 ~ /Row [0-9]+:/) {
        print ".print \"" $0 "\"";
    } else if ($0 ~ /Total:/) {
        print ".print \"" $0 "\"";
    }
}' "$LOG_FILE" >> "$OUTPUT_FILE"

cat >> "$OUTPUT_FILE" << EOF

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "END OF REPRODUCTION SCRIPT"
.print "═══════════════════════════════════════════════════════════════════"
EOF

echo "✅ SQL reproduction script generated: $OUTPUT_FILE"
echo ""
echo "To run manually:"
echo "  sqlite3 < $OUTPUT_FILE"
echo ""
echo "Or interactively:"
echo "  sqlite3"
echo "  .read $OUTPUT_FILE"

