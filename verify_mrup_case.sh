#!/bin/bash

# Quick script to convert and verify a MRUP log file
# Usage: ./verify_mrup_case.sh <log_file>

set -e

if [ $# -eq 0 ]; then
    echo "Usage: $0 <mrup_log_file>"
    echo ""
    echo "Example:"
    echo "  $0 mrup_logs/mrup_20251210_075620_708.log"
    echo ""
    echo "Or to verify the latest log:"
    echo "  $0 latest"
    exit 1
fi

LOG_FILE="$1"

# Handle "latest" shortcut
if [ "$LOG_FILE" = "latest" ]; then
    LOG_FILE=$(ls -t mrup_logs/*.log 2>/dev/null | head -1)
    if [ -z "$LOG_FILE" ]; then
        echo "Error: No log files found in mrup_logs/"
        exit 1
    fi
    echo "Using latest log: $LOG_FILE"
fi

if [ ! -f "$LOG_FILE" ]; then
    echo "Error: Log file not found: $LOG_FILE"
    exit 1
fi

echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║           MRUP Manual Verification Workflow                       ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""

# Step 1: Convert log to SQL
echo "Step 1: Converting log to SQL script..."
python3 log_to_sql.py "$LOG_FILE"
echo ""

# Extract output filename
TEST_ID=$(basename "$LOG_FILE" .log)
SQL_FILE="reproduction_${TEST_ID}.sql"

if [ ! -f "$SQL_FILE" ]; then
    echo "Error: SQL file not generated: $SQL_FILE"
    exit 1
fi

# Step 2: Run in SQLite3
echo "Step 2: Running SQL script in SQLite3..."
echo "───────────────────────────────────────────────────────────────────"
sqlite3 < "$SQL_FILE"
echo "───────────────────────────────────────────────────────────────────"
echo ""

# Step 3: Summary
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║                    Verification Complete                          ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo ""
echo "Files generated:"
echo "  - SQL script: $SQL_FILE"
echo ""
echo "Manual verification checklist:"
echo "  ☐ Check that t1 and t2 have disjoint partitions"
echo "  ☐ Compare Q1 results (window function on t1)"
echo "  ☐ Compare Q2 results (window function on t2)"
echo "  ☐ Verify Q_union = Q1 ∪ Q2 (same rows, same wf_result)"
echo ""
echo "To re-run just the SQL:"
echo "  sqlite3 < $SQL_FILE"
echo ""

