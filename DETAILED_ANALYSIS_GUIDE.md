# Ultra-Detailed MRUP Log Analysis Guide

## Overview

This guide explains how to get **ultra-detailed** analysis of MRUP test cases, showing every step from table creation to result comparison.

---

## Quick Start

```bash
# 1. Generate MRUP logs
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 5 sqlite3 --oracle MRUP

# 2. Analyze any log file in detail
python3 analyze_mrup_log_detailed.py mrup_logs/mrup_20251210_111608_235.log
```

This generates a detailed analysis file: `mrup_20251210_111608_235_DETAILED_ANALYSIS.txt`

---

## What You Get

The detailed analysis shows:

### **STEP 1: CREATE TABLES**
- Schema definition
- **Actual SQL CREATE TABLE statements** you can run

```sql
CREATE TABLE t1 (dept TEXT, salary INT, age INT, c0 REAL);
CREATE TABLE t61 (dept TEXT, salary INT, age INT, c0 REAL);
```

### **STEP 2: INSERT DATA**
- **Actual SQL INSERT statements** for every row
- Proper NULL handling
- Formatted for direct execution

```sql
INSERT INTO t1 VALUES ('Finance', 50000, 21, 32.456);
INSERT INTO t1 VALUES ('Engineerin', 45000, 49, 36.964);
...
```

- **Partition Verification**:
  - t1 partitions: [Engineering, Finance]
  - t2 partitions: [Sales, Marketing]
  - Overlap: NONE ✓
  - Status: DISJOINT ✓

### **STEP 3: WINDOW FUNCTION GENERATION**
- Complete window function
- Breakdown of components:
  - 📌 PARTITION BY: dept
  - 📌 ORDER BY: salary DESC NULLS LAST, age DESC NULLS FIRST
  - 📌 FRAME: ROWS 1 PRECEDING

### **STEP 4: GENERATED QUERIES**
- Q1 (window function on t1)
- Q2 (window function on t2)
- Q_union (window function on t1 UNION ALL t2)

### **STEP 5: RESULT COMPARISON** (3 Layers)

#### **Layer 1: Cardinality Check**
Shows detailed breakdown:
```
Q1 result count:      5 rows
Q2 result count:      5 rows
─────────────────────────────────
Expected (Q1 + Q2):   10 rows
Actual (Q_union):     10 rows

Result:  ✓ PASS
```

#### **Layer 2: MRUP Normalization**
Explains the sorting algorithm:
```
🔧 MRUP Normalization Algorithm:
   1. Sort by partition key (dept)
   2. Within each partition, sort by window ORDER BY keys
   3. Use wf_result as tie-breaker for deterministic ordering

📊 Sorting Keys (in order):
   1. Partition key: dept
   2. Window ORDER BY: salary DESC NULLS LAST
   3. Window ORDER BY: age DESC NULLS FIRST
   4. Tie-breaker: wf_result

   Result: ✓ All result sets normalized
```

#### **Layer 3: Per-Partition Comparison**
Shows partition-by-partition matching:
```
📊 Partitions to compare: 4

   Partition: Engineering
      Expected rows: 2
      Actual rows:   2
      Match: ✓ YES

   Partition: Finance
      Expected rows: 3
      Actual rows:   3
      Match: ✓ YES

   Partition: Marketing
      Expected rows: 2
      Actual rows:   2
      Match: ✓ YES

   Partition: Sales
      Expected rows: 3
      Actual rows:   3
      Match: ✓ YES

   Overall Result: ✓ PASS - All partitions match!
```

---

## Understanding the 3-Layer Comparison

### Layer 1: Cardinality Check
**Purpose**: Verify total row count matches

**What it checks**:
- Q1 has `n1` rows
- Q2 has `n2` rows
- Q_union should have exactly `n1 + n2` rows

**Why it matters**: If cardinality doesn't match, something is fundamentally wrong (rows were lost or duplicated).

### Layer 2: MRUP Normalization
**Purpose**: Sort results in a way that preserves window function semantics

**What it does**:
1. **Sort by partition key** (dept) - groups rows by partition
2. **Sort by window ORDER BY keys** - within each partition, sort according to the window function's ORDER BY
3. **Sort by wf_result** - tie-breaker for deterministic comparison

**Why it matters**: Window functions depend on row order within partitions. We can't just sort by all columns globally - that would break window semantics. MRUP normalization preserves the logical order.

### Layer 3: Per-Partition Comparison
**Purpose**: Verify the MRUP metamorphic relation holds for each partition

**What it checks**:
- For each partition P:
  - If P is in t1: Q_union[P] should equal Q1[P]
  - If P is in t2: Q_union[P] should equal Q2[P]
  - Partitions should never merge (disjoint property)

**Why it matters**: This is the core of MRUP. Since partitions are disjoint, the window function should produce identical results whether we run it on separate tables or the union.

---

## Example: How to Verify Correctness

Let's say you want to manually verify a test case:

### Step 1: Run the detailed analysis
```bash
python3 analyze_mrup_log_detailed.py mrup_logs/mrup_20251210_111608_235.log
```

### Step 2: Check the output file
```bash
cat mrup_20251210_111608_235_DETAILED_ANALYSIS.txt
```

### Step 3: Verify each layer

**Layer 1**: Check cardinality
- Q1: 5 rows
- Q2: 5 rows
- Expected: 10 rows
- Actual: 10 rows
- ✓ PASS

**Layer 2**: Understand the sorting
- Partition key: dept
- ORDER BY: salary DESC, age DESC
- Tie-breaker: wf_result
- This ensures we compare apples to apples

**Layer 3**: Check each partition
- Engineering: 2 rows match ✓
- Finance: 3 rows match ✓
- Marketing: 2 rows match ✓
- Sales: 3 rows match ✓

### Step 4: (Optional) Run SQL manually
Copy the CREATE TABLE and INSERT statements from the analysis and run them in SQLite3 to verify:

```bash
sqlite3 << 'EOF'
CREATE TABLE t1 (dept TEXT, salary INT, age INT, c0 REAL);
INSERT INTO t1 VALUES ('Finance', 50000, 21, 32.456);
...
SELECT dept, salary, age, c0, COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST ROWS 1 PRECEDING) AS wf_result FROM t1;
EOF
```

---

## When to Use This

### During Development
- **Verify oracle correctness**: Make sure the 3-layer comparison is working as expected
- **Debug false positives**: See exactly why a test failed
- **Understand MRUP**: Learn how the metamorphic relation works

### For Bug Reports
- **Minimal reproducers**: Extract the exact SQL statements
- **Clear evidence**: Show the layer-by-layer comparison
- **Share with others**: Anyone can verify the bug

### For Documentation
- **Examples**: Generate examples for papers/documentation
- **Test archive**: Keep a library of interesting test cases
- **Teaching**: Explain MRUP to others

---

## Advanced Usage

### Analyze Multiple Logs
```bash
for log in mrup_logs/*.log; do
    python3 analyze_mrup_log_detailed.py "$log"
done
```

### Find Failed Tests
```bash
grep -l "❌ MRUP TEST FAILED" mrup_logs/*.log | while read log; do
    python3 analyze_mrup_log_detailed.py "$log"
done
```

### Compare Different Window Functions
```bash
# Find logs with specific window functions
grep -l "ROW_NUMBER" mrup_logs/*.log | head -1 | xargs python3 analyze_mrup_log_detailed.py
grep -l "AVG" mrup_logs/*.log | head -1 | xargs python3 analyze_mrup_log_detailed.py
```

---

## Files

- **`analyze_mrup_log_detailed.py`**: The detailed analysis script
- **`mrup_logs/*.log`**: Original MRUP log files
- **`*_DETAILED_ANALYSIS.txt`**: Generated detailed analysis files

---

## Summary

The detailed analysis tool provides:

1. ✅ **Exact SQL statements** for table creation and data insertion
2. ✅ **Step-by-step breakdown** of window function generation
3. ✅ **Layer 1 details**: Cardinality check with row counts
4. ✅ **Layer 2 details**: MRUP normalization algorithm explanation
5. ✅ **Layer 3 details**: Per-partition comparison results
6. ✅ **Human-readable format** for manual verification
7. ✅ **Reproducible test cases** you can run in SQLite3

This gives you **complete transparency** into how the MRUP Oracle works and makes it easy to verify correctness!

---

## Example Output Structure

```
╔═══════════════════════════════════════════════════════════════════╗
║           ULTRA-DETAILED MRUP LOG ANALYSIS                        ║
╚═══════════════════════════════════════════════════════════════════╝

STEP 1: CREATE TABLES
  - Schema definition
  - SQL CREATE TABLE statements

STEP 2: INSERT DATA
  - SQL INSERT statements for t1
  - SQL INSERT statements for t2
  - Partition verification

STEP 3: WINDOW FUNCTION GENERATION
  - Complete window function
  - Component breakdown (PARTITION BY, ORDER BY, FRAME)

STEP 4: GENERATED QUERIES
  - Q1, Q2, Q_union

STEP 5: RESULT COMPARISON
  ┌─ LAYER 1: CARDINALITY CHECK ─┐
  │ - Q1 count, Q2 count          │
  │ - Expected vs Actual          │
  │ - Pass/Fail                   │
  └───────────────────────────────┘
  
  ┌─ LAYER 2: MRUP NORMALIZATION ─┐
  │ - Sorting algorithm           │
  │ - Sorting keys in order       │
  │ - Normalization status        │
  └───────────────────────────────┘
  
  ┌─ LAYER 3: PER-PARTITION COMPARISON ─┐
  │ - Partition-by-partition results    │
  │ - Expected vs Actual for each       │
  │ - Overall match status              │
  └─────────────────────────────────────┘

FINAL RESULT: ✅ PASS or ❌ FAIL
```

---

**Status**: ✅ DETAILED ANALYSIS TOOL COMPLETE

You now have full visibility into every step of the MRUP Oracle!

