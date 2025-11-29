#!/bin/bash

# Test script for MRUP Oracle
# This script runs SQLancer with the MRUP oracle for window function testing

echo "=========================================="
echo "Testing MRUP Oracle for SQLite3"
echo "=========================================="
echo ""
echo "MRUP (MR-UNION-PARTITION) Oracle tests window functions using metamorphic relations:"
echo "1. Generate two base tables t1 and t2"
echo "2. Create t_union = t1 UNION ALL t2"
echo "3. Generate random window function query"
echo "4. Execute window query on t1, t2, and t_union"
echo "5. Compare: Q_union should equal Q1 UNION ALL Q2"
echo ""
echo "Starting test with 100 queries, timeout 60 seconds..."
echo ""

# Run SQLancer with MRUP oracle
java -jar target/sqlancer-*.jar \
    --num-queries 100 \
    --num-threads 1 \
    --timeout-seconds 60 \
    --log-each-select \
    sqlite3 \
    --oracle MRUP

echo ""
echo "=========================================="
echo "Test completed!"
echo "=========================================="

