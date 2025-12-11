#!/bin/bash

# Script to generate a standalone SQL reproduction script from MRUP log file
# Usage: ./generate_reproduction_script_v2.sh <log_file>

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

# Extract table names from the log
T1_NAME=$(grep -oP "Table t1 \(\K[^)]+(?=\))" "$LOG_FILE" | head -1)
T2_NAME=$(grep -oP "Table t2 \(\K[^)]+(?=\))" "$LOG_FILE" | head -1)

# Extract schema
SCHEMA=$(grep -A 1 "📋 Schema" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')

# Extract Q1, Q2, Q_union queries
Q1=$(grep -A 1 "📝 Q1" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')
Q2=$(grep -A 1 "📝 Q2" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')
Q_UNION=$(grep -A 1 "📝 Q_union" "$LOG_FILE" | tail -1 | sed 's/^[[:space:]]*//')

# Start generating the SQL script
cat > "$OUTPUT_FILE" << EOF
-- ═══════════════════════════════════════════════════════════════════
-- MRUP Oracle - Manual Verification Script
-- ═══════════════════════════════════════════════════════════════════
-- Test Case: $TEST_ID
-- Generated from: $LOG_FILE
--
-- This script allows you to manually verify the MRUP metamorphic relation:
--   H(t1 ∪ t2) = H(t1) ∪ H(t2)
--
-- Instructions:
--   1. Run this script: sqlite3 < $OUTPUT_FILE
--   2. Compare Q1, Q2, and Q_union results
--   3. Verify that H(t_union) = H(t1) ∪ H(t2)
-- ═══════════════════════════════════════════════════════════════════

.mode column
.headers on
.nullvalue <NULL>

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

# Extract t1 data rows (skip header and separator lines)
python3 << 'PYTHON_SCRIPT' >> "$OUTPUT_FILE"
import sys
import re

log_file = sys.argv[1]
t1_name = sys.argv[2]

with open(log_file, 'r') as f:
    content = f.read()

# Find t1 table section
t1_match = re.search(r'📊 Table t1.*?:\n(.*?)Total rows:', content, re.DOTALL)
if t1_match:
    table_content = t1_match.group(1)
    lines = table_content.strip().split('\n')
    
    for line in lines:
        line = line.strip()
        # Skip empty lines, separator lines, and header lines
        if not line or '---' in line or 'dept' in line.lower() and 'salary' in line.lower():
            continue
        
        # Parse data row
        parts = line.split()
        if len(parts) >= 3:
            dept, salary, age = parts[0], parts[1], parts[2]
            # Handle NULL values and quote strings
            if dept.upper() == 'NULL':
                dept_val = 'NULL'
            else:
                dept_val = f"'{dept}'"
            
            if salary.upper() == 'NULL':
                salary_val = 'NULL'
            else:
                salary_val = salary
                
            if age.upper() == 'NULL':
                age_val = 'NULL'
            else:
                age_val = age
            
            print(f"INSERT INTO {t1_name} VALUES ({dept_val}, {salary_val}, {age_val});")
PYTHON_SCRIPT

python3 - "$LOG_FILE" "$T1_NAME" << 'PYTHON_SCRIPT' >> "$OUTPUT_FILE"
import sys
import re

log_file = sys.argv[1]
t1_name = sys.argv[2]

with open(log_file, 'r') as f:
    content = f.read()

# Find t1 table section
t1_match = re.search(r'📊 Table t1.*?:\n(.*?)Total rows:', content, re.DOTALL)
if t1_match:
    table_content = t1_match.group(1)
    lines = table_content.strip().split('\n')
    
    for line in lines:
        line = line.strip()
        # Skip empty lines, separator lines, and header lines
        if not line or '---' in line or 'dept' in line.lower() and 'salary' in line.lower():
            continue
        
        # Parse data row
        parts = line.split()
        if len(parts) >= 3:
            dept, salary, age = parts[0], parts[1], parts[2]
            # Handle NULL values and quote strings
            if dept.upper() == 'NULL':
                dept_val = 'NULL'
            else:
                dept_val = f"'{dept}'"
            
            if salary.upper() == 'NULL':
                salary_val = 'NULL'
            else:
                salary_val = salary
                
            if age.upper() == 'NULL':
                age_val = 'NULL'
            else:
                age_val = age
            
            print(f"INSERT INTO {t1_name} VALUES ({dept_val}, {salary_val}, {age_val});")
PYTHON_SCRIPT

cat >> "$OUTPUT_FILE" << EOF

-- ───────────────────────────────────────────────────────────────────
-- STEP 3: Insert Data into t2 (${T2_NAME})
-- ───────────────────────────────────────────────────────────────────

EOF

# Extract t2 data rows
python3 - "$LOG_FILE" "$T2_NAME" << 'PYTHON_SCRIPT' >> "$OUTPUT_FILE"
import sys
import re

log_file = sys.argv[1]
t2_name = sys.argv[2]

with open(log_file, 'r') as f:
    content = f.read()

# Find t2 table section
t2_match = re.search(r'📊 Table t2.*?:\n(.*?)Total rows:', content, re.DOTALL)
if t2_match:
    table_content = t2_match.group(1)
    lines = table_content.strip().split('\n')
    
    for line in lines:
        line = line.strip()
        # Skip empty lines, separator lines, and header lines
        if not line or '---' in line or 'dept' in line.lower() and 'salary' in line.lower():
            continue
        
        # Parse data row
        parts = line.split()
        if len(parts) >= 3:
            dept, salary, age = parts[0], parts[1], parts[2]
            # Handle NULL values and quote strings
            if dept.upper() == 'NULL':
                dept_val = 'NULL'
            else:
                dept_val = f"'{dept}'"
            
            if salary.upper() == 'NULL':
                salary_val = 'NULL'
            else:
                salary_val = salary
                
            if age.upper() == 'NULL':
                age_val = 'NULL'
            else:
                age_val = age
            
            print(f"INSERT INTO {t2_name} VALUES ({dept_val}, {salary_val}, {age_val});")
PYTHON_SCRIPT

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
SELECT * FROM ${T1_NAME} ORDER BY dept, salary, age;

.print ""
.print "--- Table t2 (${T2_NAME}) ---"
SELECT * FROM ${T2_NAME} ORDER BY dept, salary, age;

-- ───────────────────────────────────────────────────────────────────
-- STEP 5: Execute Window Function Queries
-- ───────────────────────────────────────────────────────────────────

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
-- STEP 6: Expected Results (from MRUP Oracle log)
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "EXPECTED RESULTS (from MRUP Oracle)"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "The MRUP Oracle verified that:"
.print "  H(t_union) = H(t1) ∪ H(t2)"
.print ""
.print "You should manually verify that the Q_union results above match"
.print "the union of Q1 and Q2 results (when sorted by partition key)."
.print ""

.print "═══════════════════════════════════════════════════════════════════"
.print "END OF REPRODUCTION SCRIPT"
.print "═══════════════════════════════════════════════════════════════════"
EOF

echo "✅ SQL reproduction script generated: $OUTPUT_FILE"
echo ""
echo "To run:"
echo "  sqlite3 < $OUTPUT_FILE"

