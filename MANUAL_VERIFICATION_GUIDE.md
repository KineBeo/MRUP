# MRUP Oracle - Manual Verification Guide

## Overview

This guide explains how to manually verify MRUP test cases by converting log files into standalone SQL scripts that can be run in SQLite3.

---

## Quick Start

### 1. Generate MRUP Logs (with detailed results)

```bash
cd /home/kienbeovl/Desktop/DBMS_Oracles/MRUP

# Run with logging enabled
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 10 sqlite3 --oracle MRUP
```

This creates log files in `mrup_logs/` directory with detailed results showing:
- Table schemas and data
- Disjoint partition verification
- Generated window function
- Q1, Q2, and Q_union queries
- **H(t1), H(t2), H(t1) ∪ H(t2), and H(t_union) results**

### 2. Convert Log to SQL Script

```bash
# Use the Python script to convert any log file
python3 log_to_sql.py mrup_logs/mrup_20251210_075620_708.log
```

**Output**: `reproduction_mrup_20251210_075620_708.sql`

### 3. Run in SQLite3

```bash
# Method 1: Direct execution
sqlite3 < reproduction_mrup_20251210_075620_708.sql

# Method 2: Interactive
sqlite3
.read reproduction_mrup_20251210_075620_708.sql
```

---

## What the Script Does

The generated SQL script performs these steps:

### Step 1: Create Tables
```sql
DROP TABLE IF EXISTS t0;
DROP TABLE IF EXISTS t92;

CREATE TABLE t0 (dept TEXT, salary INT, age INT);
CREATE TABLE t92 (dept TEXT, salary INT, age INT);
```

### Step 2-3: Insert Data
```sql
-- t1 data (Set A partitions: Finance, Engineering, HR)
INSERT INTO t0 VALUES ('Finance', 20000, 52);
INSERT INTO t0 VALUES ('Finance', 75000, 40);
...

-- t2 data (Set B partitions: Marketing, Sales, Operations)
INSERT INTO t92 VALUES ('Marketing', 85000, 26);
INSERT INTO t92 VALUES ('Sales', 65000, 29);
...
```

### Step 4: Verify Table Contents
Shows the data in both tables, sorted for easy verification.

### Step 5: Execute Window Function Queries
Runs three queries:
- **Q1**: Window function on t1
- **Q2**: Window function on t2
- **Q_union**: Window function on (t1 UNION ALL t2)

### Step 6: Manual Verification Guide
Provides instructions for verifying the MRUP metamorphic relation.

---

## Example Output

When you run the script, you'll see:

```
═══════════════════════════════════════════════════════════════════
TABLE CONTENTS
═══════════════════════════════════════════════════════════════════

--- Table t1 (t0) - 7 rows ---
dept        salary  age
----------  ------  ---
Engineerin  60000   33 
Finance     20000   52 
Finance     45000   30 
Finance     70000   61 
Finance     75000   40 
Finance     80000   45 
HR          35000   56 

--- Table t2 (t92) - 5 rows ---
dept       salary  age
---------  ------  ---
Marketing  20000   56 
Marketing  25000   31 
Marketing  85000   26 
Sales      65000   29 
Sales      70000   55 

═══════════════════════════════════════════════════════════════════
WINDOW FUNCTION QUERIES
═══════════════════════════════════════════════════════════════════

Window Function: (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age 
                  ROWS 1 PRECEDING EXCLUDE TIES) AS wf_result

--- Q1: Window function on t1 ---
dept        salary  age  wf_result
----------  ------  ---  ---------
Engineerin  60000   33   1        
Finance     80000   45   1        
Finance     75000   40   2        
Finance     70000   61   2        
Finance     45000   30   2        
Finance     20000   52   2        
HR          35000   56   1        

--- Q2: Window function on t2 ---
dept       salary  age  wf_result
---------  ------  ---  ---------
Marketing  85000   26   1        
Marketing  25000   31   2        
Marketing  20000   56   2        
Sales      70000   55   1        
Sales      65000   29   2        

--- Q_union: Window function on (t1 UNION ALL t2) ---
dept        salary  age  wf_result
----------  ------  ---  ---------
Engineerin  60000   33   1        
Finance     80000   45   1        
Finance     75000   40   2        
Finance     70000   61   2        
Finance     45000   30   2        
Finance     20000   52   2        
HR          35000   56   1        
Marketing   85000   26   1        
Marketing   25000   31   2        
Marketing   20000   56   2        
Sales       70000   55   1        
Sales       65000   29   2        
```

---

## Manual Verification Steps

### 1. Verify Disjoint Partitions

Check that t1 and t2 have **no overlapping** `dept` values:
- **t1 partitions**: Engineerin, Finance, HR (Set A)
- **t2 partitions**: Marketing, Sales (Set B)
- **Overlap**: NONE ✓

This is **critical** for MRUP to work correctly!

### 2. Compare Q1 and Q2 Results

Look at the window function results for each table:

**Q1 (7 rows)**:
- Engineerin: 1 row, wf_result = 1
- Finance: 5 rows, wf_result = 1, 2, 2, 2, 2
- HR: 1 row, wf_result = 1

**Q2 (5 rows)**:
- Marketing: 3 rows, wf_result = 1, 2, 2
- Sales: 2 rows, wf_result = 1, 2

### 3. Compute Expected Result

**H(t1) ∪ H(t2)** should be the simple union of Q1 and Q2 results:
- All 7 rows from Q1
- All 5 rows from Q2
- **Total: 12 rows**

### 4. Compare with Q_union

**Q_union** should produce **exactly the same 12 rows** as H(t1) ∪ H(t2).

Looking at the output above:
- ✓ Q_union has 12 rows (7 + 5)
- ✓ All Q1 rows appear in Q_union with same wf_result
- ✓ All Q2 rows appear in Q_union with same wf_result
- ✓ No extra or missing rows

**Conclusion**: ✅ MRUP metamorphic relation holds!

---

## Understanding the Results

### Why This Works

The MRUP metamorphic relation states:
```
H(t1 ∪ t2) = H(t1) ∪ H(t2)
```

Where:
- `H()` is the window function
- `t1 ∪ t2` is the union of two tables

This works because:
1. **Disjoint Partitions**: t1 and t2 have no overlapping partition key values
2. **Per-Partition Computation**: Window functions operate independently on each partition
3. **No Cross-Partition Effects**: Rows from t1 don't affect window function results for t2, and vice versa

### Example Breakdown

For the **Finance** partition (only in t1):
```
Q1 results:
  Finance, 80000, 45, wf_result=1
  Finance, 75000, 40, wf_result=2
  Finance, 70000, 61, wf_result=2
  Finance, 45000, 30, wf_result=2
  Finance, 20000, 52, wf_result=2

Q_union results (same partition):
  Finance, 80000, 45, wf_result=1  ✓ matches
  Finance, 75000, 40, wf_result=2  ✓ matches
  Finance, 70000, 61, wf_result=2  ✓ matches
  Finance, 45000, 30, wf_result=2  ✓ matches
  Finance, 20000, 52, wf_result=2  ✓ matches
```

For the **Marketing** partition (only in t2):
```
Q2 results:
  Marketing, 85000, 26, wf_result=1
  Marketing, 25000, 31, wf_result=2
  Marketing, 20000, 56, wf_result=2

Q_union results (same partition):
  Marketing, 85000, 26, wf_result=1  ✓ matches
  Marketing, 25000, 31, wf_result=2  ✓ matches
  Marketing, 20000, 56, wf_result=2  ✓ matches
```

---

## When Would This Fail?

The MRUP relation would be **violated** if:

### 1. Non-Disjoint Partitions
If t1 and t2 had overlapping partition values:
```
t1: Finance, Engineering
t2: Finance, Marketing  ← Finance appears in both!
```

Then `H(t1 ∪ t2)` would merge the Finance rows from both tables into one partition, producing different window function results.

### 2. SQLite3 Bug
If SQLite3 has a bug in window function handling, you might see:
- Different wf_result values for the same row
- Missing or extra rows in Q_union
- Incorrect ordering within partitions

### 3. Incorrect Window Function
If the window function is not deterministic or uses non-disjoint columns in PARTITION BY.

---

## Troubleshooting

### Issue: "no such table: t0"
**Solution**: Make sure you're running the script from the correct directory.

### Issue: "syntax error near OVER"
**Solution**: Your SQLite3 version might be too old. Window functions require SQLite 3.25.0+.
```bash
sqlite3 --version
```

### Issue: Different results than expected
**Solution**: This might be a real bug! Check:
1. Are partitions truly disjoint?
2. Is the window function deterministic?
3. Compare with the expected results in the MRUP log file

---

## Advanced Usage

### Generate Multiple Test Cases

```bash
# Generate 100 test cases
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --timeout-seconds 60 sqlite3 --oracle MRUP

# Convert all logs to SQL
for log in mrup_logs/*.log; do
    python3 log_to_sql.py "$log"
done

# Run all SQL scripts
for sql in reproduction_*.sql; do
    echo "Testing: $sql"
    sqlite3 < "$sql" > "${sql%.sql}_output.txt"
done
```

### Compare with Different SQLite Versions

```bash
# Test with system SQLite3
sqlite3 < reproduction_mrup_20251210_075620_708.sql > output_system.txt

# Test with custom build
/path/to/custom/sqlite3 < reproduction_mrup_20251210_075620_708.sql > output_custom.txt

# Compare
diff output_system.txt output_custom.txt
```

---

## Files

### Generated Files

1. **`mrup_logs/mrup_*.log`**: Detailed test case logs from MRUP Oracle
2. **`reproduction_*.sql`**: Standalone SQL scripts for manual verification
3. **`log_to_sql.py`**: Python script to convert logs to SQL

### Key Scripts

- **`log_to_sql.py`**: Main conversion script
  ```bash
  python3 log_to_sql.py <log_file>
  ```

---

## Summary

This manual verification workflow allows you to:

1. ✅ **Inspect** actual data and queries used in MRUP tests
2. ✅ **Reproduce** test cases in any SQLite3 environment
3. ✅ **Verify** that the MRUP metamorphic relation holds
4. ✅ **Debug** false positives or investigate potential bugs
5. ✅ **Share** reproducible test cases with others

The combination of detailed logging and standalone SQL scripts provides complete transparency into the MRUP Oracle's testing process!

---

## Example Workflow

```bash
# 1. Generate test cases with logging
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 5 sqlite3 --oracle MRUP

# 2. Pick an interesting log file
ls -lh mrup_logs/ | head -5

# 3. Convert to SQL
python3 log_to_sql.py mrup_logs/mrup_20251210_075620_708.log

# 4. Run and verify
sqlite3 < reproduction_mrup_20251210_075620_708.sql

# 5. Check the results manually
# - Compare Q1 + Q2 with Q_union
# - Verify partition-by-partition
# - Confirm wf_result values match
```

---

**Status**: ✅ MANUAL VERIFICATION SYSTEM COMPLETE

All tools are in place for comprehensive manual verification of MRUP test cases!

