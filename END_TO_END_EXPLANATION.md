# MRUP Oracle: Complete End-to-End Explanation

## Overview

This document provides a **complete step-by-step explanation** of how the MRUP (Metamorphic Relation UNION-PARTITION) Oracle works, from table creation to result comparison.

---

## Table of Contents

1. [What is MRUP?](#what-is-mrup)
2. [Step 1: Create Schema](#step-1-create-schema)
3. [Step 2: Generate Disjoint Partition Data](#step-2-generate-disjoint-partition-data)
4. [Step 3: Generate Window Function Query](#step-3-generate-window-function-query)
5. [Step 4: Execute Queries](#step-4-execute-queries)
6. [Step 5: Compare Results (3-Layer Comparator)](#step-5-compare-results-3-layer-comparator)
7. [Complete Example Walkthrough](#complete-example-walkthrough)
8. [Why This Works](#why-this-works)

---

## What is MRUP?

### The Metamorphic Relation

MRUP is based on a **metamorphic relation** for window functions:

```
H(t1 ∪ t2) = H(t1) ∪ H(t2)
```

**Where:**
- `t1` and `t2` are two tables with the **same schema**
- `t1` and `t2` have **disjoint partitions** (no partition value appears in both tables)
- `H()` is a window function query with `PARTITION BY`
- `∪` means UNION ALL (concatenation)

**In plain English:**
> If we run a window function on the union of two tables with disjoint partitions, we should get the same result as running the window function on each table separately and then combining the results.

### Why This is Powerful

This relation allows us to test window functions **without knowing the correct answer**. We just need to verify that the metamorphic relation holds!

---

## Step 1: Create Schema

### Goal
Create two tables (`t1` and `t2`) with **identical schema**.

### Schema Design

The schema is designed specifically for MRUP testing:

```sql
CREATE TABLE t1 (
    dept TEXT,      -- Partition key (will be used in PARTITION BY)
    salary INT,     -- Orderable column (can be used in ORDER BY)
    age INT,        -- Orderable column (can be used in ORDER BY)
    c0 REAL,        -- Additional column (can be used in window function)
    c1 TEXT,        -- Additional column
    ...
);

CREATE TABLE t2 (
    dept TEXT,      -- Same schema as t1
    salary INT,
    age INT,
    c0 REAL,
    c1 TEXT,
    ...
);
```

### Key Points

1. **`dept` column**: Always TEXT, always the partition key
2. **`salary` and `age`**: Always INT, can be used in ORDER BY
3. **Additional columns**: Can be REAL, TEXT, INT (for variety)
4. **Same schema**: Both tables have identical structure

### Implementation

**File**: `src/sqlancer/sqlite3/gen/SQLite3MRUPTablePairGenerator.java`

```java
public static SQLite3Table[] generateMRUPTablePair(SQLite3GlobalState globalState) {
    // Generate schema
    List<SQLite3Column> columns = new ArrayList<>();
    
    // 1. Partition key column (dept)
    columns.add(new SQLite3Column("dept", SQLite3DataType.TEXT, ...));
    
    // 2. Orderable columns (salary, age)
    columns.add(new SQLite3Column("salary", SQLite3DataType.INT, ...));
    columns.add(new SQLite3Column("age", SQLite3DataType.INT, ...));
    
    // 3. Additional random columns
    for (int i = 0; i < numAdditionalColumns; i++) {
        SQLite3DataType type = Randomly.fromOptions(
            SQLite3DataType.INT, 
            SQLite3DataType.REAL, 
            SQLite3DataType.TEXT
        );
        columns.add(new SQLite3Column("c" + i, type, ...));
    }
    
    // Create both tables with same schema
    SQLite3Table t1 = createTable(globalState, columns);
    SQLite3Table t2 = createTable(globalState, columns);
    
    return new SQLite3Table[] { t1, t2 };
}
```

---

## Step 2: Generate Disjoint Partition Data

### Goal
Populate `t1` and `t2` with data such that they have **disjoint partitions**.

### What is "Disjoint Partitions"?

**Disjoint partitions** means:
- Every partition value (dept) appears in **either** `t1` **or** `t2`, but **never both**
- No overlap in partition values

**Example:**
```
t1 partitions: [Engineering, Finance]
t2 partitions: [Sales, Marketing]
Overlap: NONE ✓
```

**Bad Example (NOT disjoint):**
```
t1 partitions: [Engineering, Finance]
t2 partitions: [Finance, Marketing]  ← Finance appears in both!
Overlap: [Finance] ✗
```

### Why Disjoint Partitions?

For the MRUP relation to hold:
- Window functions with `PARTITION BY dept` treat each partition **independently**
- If partitions are disjoint, running on `t1 ∪ t2` is equivalent to running on `t1` and `t2` separately
- This is the **key insight** that makes MRUP work!

### Algorithm

**File**: `src/sqlancer/sqlite3/gen/SQLite3MRUPTablePairGenerator.java`

```java
// Step 1: Generate all possible partition values
Set<String> allPartitions = new HashSet<>();
String[] deptNames = {"Engineering", "Finance", "Sales", "Marketing", "Operations", "HR"};
for (String dept : deptNames) {
    allPartitions.add(dept);
}

// Step 2: Randomly split partitions between t1 and t2
Set<String> t1Partitions = new HashSet<>();
Set<String> t2Partitions = new HashSet<>();

for (String partition : allPartitions) {
    if (Randomly.getBoolean()) {
        t1Partitions.add(partition);
    } else {
        t2Partitions.add(partition);
    }
}

// Step 3: Handle NULL partition (can only be in one table)
boolean nullInT1 = Randomly.getBoolean();
if (nullInT1) {
    t1Partitions.add(null);  // NULL partition in t1
} else {
    t2Partitions.add(null);  // NULL partition in t2
}

// Step 4: Verify disjoint property
Set<String> overlap = new HashSet<>(t1Partitions);
overlap.retainAll(t2Partitions);
if (!overlap.isEmpty()) {
    throw new SQLException("Partition overlap detected: " + overlap);
}
```

### Data Generation

For each table, generate rows with partition values from its assigned set:

```java
// Generate rows for t1
for (int i = 0; i < numRows; i++) {
    List<String> row = new ArrayList<>();
    
    // 1. Pick a partition value from t1Partitions
    String dept = Randomly.fromSet(t1Partitions);
    row.add(dept);
    
    // 2. Generate random values for other columns
    row.add(String.valueOf(Randomly.getInteger(20000, 100000)));  // salary
    row.add(String.valueOf(Randomly.getInteger(20, 65)));         // age
    row.add(String.valueOf(Randomly.getDouble()));                // c0 (REAL)
    // ... more columns
    
    // 3. Insert into t1
    insertRow(t1, row);
}

// Generate rows for t2 (same process, but use t2Partitions)
for (int i = 0; i < numRows; i++) {
    // ... same as above, but pick from t2Partitions
}
```

### Example Result

**Table t1:**
```
dept         salary  age  c0
-----------  ------  ---  ------
Engineering  50000   45   32.456
Engineering  60000   34   84.061
Finance      45000   52   36.964
Finance      70000   28   12.345
```

**Table t2:**
```
dept         salary  age  c0
-----------  ------  ---  ------
Sales        80000   39   63.186
Sales        55000   44   40.461
Marketing    65000   31   24.279
Marketing    90000   52   33.310
```

**Verification:**
```
t1 partitions: [Engineering, Finance]
t2 partitions: [Sales, Marketing]
Overlap: NONE ✓
Status: DISJOINT ✓
```

---

## Step 3: Generate Window Function Query

### Goal
Generate a valid window function query that follows **strict constraints** to ensure MRUP holds.

### Constraints (C0-C5)

We enforce **6 critical constraints**:

#### **C0: PARTITION BY is MANDATORY**
```sql
-- ✓ VALID
COUNT(*) OVER (PARTITION BY dept ORDER BY salary)

-- ✗ INVALID (no PARTITION BY)
COUNT(*) OVER (ORDER BY salary)
```

**Why?** Without `PARTITION BY`, the window function treats the entire table as one partition, which breaks the MRUP relation.

#### **C1: PARTITION BY only uses 'dept'**
```sql
-- ✓ VALID
COUNT(*) OVER (PARTITION BY dept ORDER BY salary)

-- ✗ INVALID (partition by multiple columns)
COUNT(*) OVER (PARTITION BY dept, salary ORDER BY age)
```

**Why?** We designed the data to have disjoint `dept` values. Partitioning by other columns would break this property.

#### **C2: ORDER BY only uses salary/age**
```sql
-- ✓ VALID
COUNT(*) OVER (PARTITION BY dept ORDER BY salary, age)

-- ✗ INVALID (order by dept)
COUNT(*) OVER (PARTITION BY dept ORDER BY dept)
```

**Why?** `dept` is the partition key, so ordering by it within a partition is meaningless. We restrict ORDER BY to `salary` and `age` for simplicity.

#### **C3: No FRAME for ranking functions**
```sql
-- ✓ VALID (ranking function, no frame)
ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary)

-- ✗ INVALID (ranking function with frame)
ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary ROWS 1 PRECEDING)
```

**Why?** Ranking functions (`ROW_NUMBER`, `RANK`, `DENSE_RANK`) don't support frame clauses.

#### **C4: RANGE only with single ORDER BY**
```sql
-- ✓ VALID
SUM(salary) OVER (PARTITION BY dept ORDER BY salary RANGE 1000 PRECEDING)

-- ✗ INVALID (RANGE with multiple ORDER BY)
SUM(salary) OVER (PARTITION BY dept ORDER BY salary, age RANGE 1000 PRECEDING)
```

**Why?** SQLite's `RANGE` clause only works with a single ORDER BY column.

#### **C5: Only deterministic functions**
```sql
-- ✓ VALID
ROW_NUMBER(), RANK(), DENSE_RANK(), SUM(), AVG(), COUNT(), MIN(), MAX()

-- ✗ INVALID
RANDOM(), NTILE(), LAG(), LEAD(), FIRST_VALUE(), LAST_VALUE()
```

**Why?** Non-deterministic functions or functions that depend on physical row order can produce different results on `t1 ∪ t2` vs `t1` and `t2` separately.

### Generation Algorithm

**File**: `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`

```java
// Step 1: Choose window function type (C5)
String functionType = Randomly.fromOptions(
    "ROW_NUMBER", "RANK", "DENSE_RANK",  // Ranking functions
    "SUM", "AVG", "COUNT", "MIN", "MAX"   // Aggregate functions
);

// Step 2: Build function call
StringBuilder sb = new StringBuilder();
if (functionType.equals("ROW_NUMBER") || functionType.equals("RANK") || functionType.equals("DENSE_RANK")) {
    sb.append(functionType).append("()");
} else {
    // Aggregate function needs an argument
    SQLite3Column argColumn = Randomly.fromList(columns);
    sb.append(functionType).append("(").append(argColumn.getName()).append(")");
}

// Step 3: Add OVER clause
sb.append(" OVER (");

// Step 4: Add PARTITION BY (C0, C1)
SQLite3Column deptColumn = findColumnByName(columns, "dept");
sb.append("PARTITION BY ").append(deptColumn.getName()).append(" ");

// Step 5: Add ORDER BY (C2)
List<SQLite3Column> orderableColumns = findOrderableColumns(columns);  // Only salary, age
if (Randomly.getBoolean() && !orderableColumns.isEmpty()) {
    sb.append("ORDER BY ");
    int numOrderBy = Randomly.getInteger(1, Math.min(2, orderableColumns.size()));
    for (int i = 0; i < numOrderBy; i++) {
        if (i > 0) sb.append(", ");
        SQLite3Column col = Randomly.fromList(orderableColumns);
        sb.append(col.getName()).append(" ");
        sb.append(Randomly.fromOptions("ASC", "DESC")).append(" ");
        sb.append(Randomly.fromOptions("NULLS FIRST", "NULLS LAST"));
    }
}

// Step 6: Add FRAME clause (C3, C4)
boolean isRankingFunction = functionType.equals("ROW_NUMBER") || 
                            functionType.equals("RANK") || 
                            functionType.equals("DENSE_RANK");
if (!isRankingFunction && Randomly.getBoolean()) {
    // Only add frame for aggregate functions
    String frameType = Randomly.fromOptions("ROWS", "RANGE");
    
    // C4: RANGE only with single ORDER BY
    if (frameType.equals("RANGE") && numOrderBy > 1) {
        frameType = "ROWS";  // Fall back to ROWS
    }
    
    sb.append(frameType).append(" ");
    sb.append(Randomly.fromOptions(
        "UNBOUNDED PRECEDING",
        "1 PRECEDING",
        "CURRENT ROW",
        "BETWEEN 1 PRECEDING AND CURRENT ROW"
    ));
}

sb.append(")");

String windowFunction = sb.toString();
```

### Example Generated Window Functions

```sql
-- Example 1: Ranking function
ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST)

-- Example 2: Aggregate function with frame
SUM(salary) OVER (PARTITION BY dept ORDER BY age ASC NULLS FIRST ROWS 2 PRECEDING)

-- Example 3: Aggregate function without ORDER BY
COUNT(*) OVER (PARTITION BY dept)

-- Example 4: Multiple ORDER BY columns
AVG(age) OVER (PARTITION BY dept ORDER BY salary DESC, age ASC ROWS UNBOUNDED PRECEDING)
```

---

## Step 4: Execute Queries

### Goal
Execute the window function query on three different data sources:
1. `t1` alone → `H(t1)`
2. `t2` alone → `H(t2)`
3. `t1 ∪ t2` (union) → `H(t_union)`

### Query Templates

**Q1: Window function on t1**
```sql
SELECT dept, salary, age, c0, ..., 
       <window_function> AS wf_result 
FROM t1
```

**Q2: Window function on t2**
```sql
SELECT dept, salary, age, c0, ..., 
       <window_function> AS wf_result 
FROM t2
```

**Q_union: Window function on t1 UNION ALL t2**
```sql
SELECT dept, salary, age, c0, ..., 
       <window_function> AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union
```

### Example

Given window function:
```sql
COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST)
```

**Q1:**
```sql
SELECT dept, salary, age, c0, 
       COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST) AS wf_result 
FROM t1
```

**Q2:**
```sql
SELECT dept, salary, age, c0, 
       COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST) AS wf_result 
FROM t2
```

**Q_union:**
```sql
SELECT dept, salary, age, c0, 
       COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST) AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union
```

### Execution

**File**: `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`

```java
// Execute Q1
String q1 = buildQuery(t1, windowFunction);
List<List<String>> results1 = executeQuery(q1);

// Execute Q2
String q2 = buildQuery(t2, windowFunction);
List<List<String>> results2 = executeQuery(q2);

// Execute Q_union
String qUnion = buildUnionQuery(t1, t2, windowFunction);
List<List<String>> resultsUnion = executeQuery(qUnion);
```

### Example Results

**H(t1) - Result from Q1:**
```
dept         salary  age  c0       wf_result
-----------  ------  ---  -------  ---------
Engineering  60000   24   84.061   1
Engineering  45000   49   36.964   2
Finance      50000   51   33.813   1
Finance      50000   21   32.456   2
Finance      20000   39   4.113    3
```

**H(t2) - Result from Q2:**
```
dept         salary  age  c0       wf_result
-----------  ------  ---  -------  ---------
Sales        80000   26   63.186   1
Sales        75000   39   NULL     2
Sales        65000   57   40.461   3
Marketing    90000   37   33.310   1
Marketing    30000   28   24.279   2
```

**H(t_union) - Result from Q_union:**
```
dept         salary  age  c0       wf_result
-----------  ------  ---  -------  ---------
Engineering  60000   24   84.061   1
Engineering  45000   49   36.964   2
Finance      50000   51   33.813   1
Finance      50000   21   32.456   2
Finance      20000   39   4.113    3
Sales        80000   26   63.186   1
Sales        75000   39   NULL     2
Sales        65000   57   40.461   3
Marketing    90000   37   33.310   1
Marketing    30000   28   24.279   2
```

---

## Step 5: Compare Results (3-Layer Comparator)

### Goal
Verify that `H(t_union) = H(t1) ∪ H(t2)` using a **3-layer comparison architecture**.

### Why 3 Layers?

We can't just do a naive comparison because:
1. **Row order matters** - SQLite may return rows in different orders
2. **Type handling matters** - Need to compare values by their actual types, not string representation
3. **Partition semantics matter** - Need to compare partition-by-partition

### Layer 1: Cardinality Check

**Purpose**: Quick sanity check - verify total row count matches.

**Algorithm**:
```java
int expectedCardinality = results1.size() + results2.size();
int actualCardinality = resultsUnion.size();

if (actualCardinality != expectedCardinality) {
    throw new AssertionError("Cardinality mismatch!");
}
```

**Example**:
```
Q1 result count:      5 rows
Q2 result count:      5 rows
─────────────────────────────────
Expected (Q1 + Q2):   10 rows
Actual (Q_union):     10 rows

Result: ✓ PASS
```

**Why this matters**: If cardinality doesn't match, something is fundamentally wrong (rows were lost or duplicated). No need to proceed to more expensive comparisons.

### Layer 2: MRUP Normalization

**Purpose**: Sort results in a semantically consistent order that preserves window function behavior.

**Why we need this**:
- SQLite may return results in different physical orders
- We need to compare results in a consistent order
- But we can't just sort by all columns - that would break window semantics!

**Algorithm**:

```java
void normalizeForMRUP(List<List<String>> results, WindowSpec spec) {
    results.sort((row1, row2) -> {
        // 1. First, sort by partition key (dept)
        int cmp = compareValue(
            row1.get(spec.partitionColumnIndex),
            row2.get(spec.partitionColumnIndex),
            false,  // ASC
            false   // NULLS LAST
        );
        if (cmp != 0) return cmp;
        
        // 2. Then, sort by window ORDER BY keys
        for (int i = 0; i < spec.orderByColumnIndices.size(); i++) {
            int colIdx = spec.orderByColumnIndices.get(i);
            boolean desc = spec.orderByDirections.get(i).equals("DESC");
            boolean nullsFirst = spec.nullsHandling.get(i).equals("NULLS FIRST");
            
            cmp = compareValue(
                row1.get(colIdx),
                row2.get(colIdx),
                desc,
                nullsFirst
            );
            if (cmp != 0) return cmp;
        }
        
        // 3. Finally, use wf_result as tie-breaker
        cmp = compareValue(
            row1.get(spec.wfResultIndex),
            row2.get(spec.wfResultIndex),
            false,  // ASC
            false   // NULLS LAST
        );
        return cmp;
    });
}
```

**Sorting Keys (in priority order)**:
1. **Partition key** (dept) - Always ASC, NULLS LAST
2. **Window ORDER BY keys** - Respect ASC/DESC and NULLS FIRST/LAST from window function
3. **Window function result** (wf_result) - Tie-breaker, ASC, NULLS LAST

**Example**:

For window function:
```sql
COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST)
```

Sorting keys:
1. `dept` (ASC, NULLS LAST)
2. `salary` (DESC, NULLS LAST)
3. `age` (DESC, NULLS FIRST)
4. `wf_result` (ASC, NULLS LAST)

**Before Normalization**:
```
Expected [H(t1) ∪ H(t2)]:
  Row 1: [Finance, 50000, 51, 33.813, 1]
  Row 2: [Engineering, 60000, 24, 84.061, 1]
  Row 3: [Finance, 20000, 39, 4.113, 3]
  Row 4: [Engineering, 45000, 49, 36.964, 2]
  Row 5: [Finance, 50000, 21, 32.456, 2]
  ...
```

**After Normalization**:
```
Expected [H(t1) ∪ H(t2)] - Normalized:
  Row 1: [Engineering, 60000, 24, 84.061, 1]
  Row 2: [Engineering, 45000, 49, 36.964, 2]
  Row 3: [Finance, 50000, 51, 33.813, 1]
  Row 4: [Finance, 50000, 21, 32.456, 2]
  Row 5: [Finance, 20000, 39, 4.113, 3]
  ...
```

**Key Point**: This sorting happens **in memory** on the Java List objects. The database is NOT touched!

### Layer 3: Per-Partition Comparison

**Purpose**: Compare results partition-by-partition with type-aware value comparison.

**Algorithm**:

```java
boolean comparePerPartition(
    List<List<String>> results1,
    List<List<String>> results2,
    List<List<String>> resultsUnion,
    WindowSpec spec
) {
    // Step 1: Combine H(t1) and H(t2) to get expected
    List<List<String>> expected = new ArrayList<>();
    expected.addAll(results1);
    expected.addAll(results2);
    
    // Step 2: Group by partition
    Map<String, List<List<String>>> expectedByPartition = groupByPartition(expected, spec);
    Map<String, List<List<String>>> actualByPartition = groupByPartition(resultsUnion, spec);
    
    // Step 3: Compare each partition
    for (String partition : expectedByPartition.keySet()) {
        List<List<String>> expectedRows = expectedByPartition.get(partition);
        List<List<String>> actualRows = actualByPartition.get(partition);
        
        // Compare row counts
        if (expectedRows.size() != actualRows.size()) {
            reportMismatch(partition, expectedRows, actualRows);
            return false;
        }
        
        // Compare row-by-row
        for (int i = 0; i < expectedRows.size(); i++) {
            List<String> expectedRow = expectedRows.get(i);
            List<String> actualRow = actualRows.get(i);
            
            // Compare each column with type-aware comparison
            for (int j = 0; j < expectedRow.size(); j++) {
                String expectedVal = expectedRow.get(j);
                String actualVal = actualRow.get(j);
                
                if (!valuesMatch(expectedVal, actualVal, spec.orderByColumnTypes.get(j))) {
                    reportMismatch(partition, expectedRows, actualRows);
                    return false;
                }
            }
        }
    }
    
    return true;  // All partitions match!
}
```

**Type-Aware Value Comparison**:

```java
boolean valuesMatch(String val1, String val2, String type) {
    // Handle NULL
    if (val1 == null || val1.equals("NULL")) {
        return val2 == null || val2.equals("NULL");
    }
    if (val2 == null || val2.equals("NULL")) {
        return false;
    }
    
    // Compare by type
    if (type.equals("INT") || type.equals("REAL")) {
        // Numeric comparison
        try {
            double d1 = Double.parseDouble(val1);
            double d2 = Double.parseDouble(val2);
            return Math.abs(d1 - d2) < 0.0001;  // Epsilon for floating point
        } catch (NumberFormatException e) {
            return false;
        }
    } else {
        // String comparison
        return val1.equals(val2);
    }
}
```

**Example**:

```
📊 Partitions to compare: 4

Partition: Engineering
   Expected rows: 2
   Actual rows:   2
   Row-by-row comparison:
      Row 1: [Engineering, 60000, 24, 84.061, 1] == [Engineering, 60000, 24, 84.061, 1] ✓
      Row 2: [Engineering, 45000, 49, 36.964, 2] == [Engineering, 45000, 49, 36.964, 2] ✓
   Match: ✓ YES

Partition: Finance
   Expected rows: 3
   Actual rows:   3
   Row-by-row comparison:
      Row 1: [Finance, 50000, 51, 33.813, 1] == [Finance, 50000, 51, 33.813, 1] ✓
      Row 2: [Finance, 50000, 21, 32.456, 2] == [Finance, 50000, 21, 32.456, 2] ✓
      Row 3: [Finance, 20000, 39, 4.113, 3] == [Finance, 20000, 39, 4.113, 3] ✓
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

### Summary of 3 Layers

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Cardinality Check                                 │
│ ✓ Fast sanity check                                        │
│ ✓ Catches major issues (missing/extra rows)                │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: MRUP Normalization                                │
│ ✓ Sorts results in semantically consistent order           │
│ ✓ Preserves window function behavior                       │
│ ✓ Handles different physical orders from SQLite            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Per-Partition Comparison                          │
│ ✓ Compares partition-by-partition                          │
│ ✓ Type-aware value comparison                              │
│ ✓ Detailed mismatch reporting                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Complete Example Walkthrough

Let's walk through a complete example from start to finish.

### Step 1: Create Schema

```sql
CREATE TABLE t1 (dept TEXT, salary INT, age INT, c0 REAL);
CREATE TABLE t2 (dept TEXT, salary INT, age INT, c0 REAL);
```

### Step 2: Generate Disjoint Partition Data

**Table t1:**
```sql
INSERT INTO t1 VALUES ('Engineering', 60000, 24, 84.061);
INSERT INTO t1 VALUES ('Engineering', 45000, 49, 36.964);
INSERT INTO t1 VALUES ('Finance', 50000, 51, 33.813);
INSERT INTO t1 VALUES ('Finance', 50000, 21, 32.456);
INSERT INTO t1 VALUES ('Finance', 20000, 39, 4.113);
```

**Table t2:**
```sql
INSERT INTO t2 VALUES ('Sales', 80000, 26, 63.186);
INSERT INTO t2 VALUES ('Sales', 75000, 39, NULL);
INSERT INTO t2 VALUES ('Sales', 65000, 57, 40.461);
INSERT INTO t2 VALUES ('Marketing', 90000, 37, 33.310);
INSERT INTO t2 VALUES ('Marketing', 30000, 28, 24.279);
```

**Verification:**
```
t1 partitions: [Engineering, Finance]
t2 partitions: [Sales, Marketing]
Overlap: NONE ✓
Status: DISJOINT ✓
```

### Step 3: Generate Window Function Query

**Generated window function:**
```sql
COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST ROWS 1 PRECEDING)
```

**Constraint verification:**
- [C0] PARTITION BY is MANDATORY: ✓ PASS
- [C1] PARTITION BY only uses 'dept': ✓ PASS
- [C2] ORDER BY only uses salary/age: ✓ PASS
- [C3] No FRAME for ranking functions: ✓ PASS (COUNT is aggregate, not ranking)
- [C4] RANGE only with single ORDER BY: ✓ PASS (using ROWS, not RANGE)
- [C5] Only deterministic functions: ✓ PASS (COUNT is deterministic)

### Step 4: Execute Queries

**Q1:**
```sql
SELECT dept, salary, age, c0, 
       COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST ROWS 1 PRECEDING) AS wf_result 
FROM t1
```

**Result H(t1):**
```
dept         salary  age  c0       wf_result
-----------  ------  ---  -------  ---------
Engineering  60000   24   84.061   1
Engineering  45000   49   36.964   2
Finance      50000   51   33.813   1
Finance      50000   21   32.456   2
Finance      20000   39   4.113    2
```

**Q2:**
```sql
SELECT dept, salary, age, c0, 
       COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST ROWS 1 PRECEDING) AS wf_result 
FROM t2
```

**Result H(t2):**
```
dept         salary  age  c0       wf_result
-----------  ------  ---  -------  ---------
Sales        80000   26   63.186   1
Sales        75000   39   NULL     2
Sales        65000   57   40.461   2
Marketing    90000   37   33.310   1
Marketing    30000   28   24.279   2
```

**Q_union:**
```sql
SELECT dept, salary, age, c0, 
       COUNT(salary) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST ROWS 1 PRECEDING) AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union
```

**Result H(t_union):**
```
dept         salary  age  c0       wf_result
-----------  ------  ---  -------  ---------
Engineering  60000   24   84.061   1
Engineering  45000   49   36.964   2
Finance      50000   51   33.813   1
Finance      50000   21   32.456   2
Finance      20000   39   4.113    2
Sales        80000   26   63.186   1
Sales        75000   39   NULL     2
Sales        65000   57   40.461   2
Marketing    90000   37   33.310   1
Marketing    30000   28   24.279   2
```

### Step 5: Compare Results

**Layer 1: Cardinality Check**
```
Q1 result count:      5 rows
Q2 result count:      5 rows
─────────────────────────────────
Expected (Q1 + Q2):   10 rows
Actual (Q_union):     10 rows

Result: ✓ PASS
```

**Layer 2: MRUP Normalization**
```
Sorting Keys:
   1. Partition key: dept
   2. Window ORDER BY: salary DESC NULLS LAST
   3. Window ORDER BY: age DESC NULLS FIRST
   4. Tie-breaker: wf_result

Expected result order changed: NO (already in MRUP order)
Actual result order changed:   NO (already in MRUP order)

Result: ✓ All result sets normalized
```

**Layer 3: Per-Partition Comparison**
```
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

**Final Result:**
```
═══════════════════════════════════════════════════════════════════
✅ MRUP TEST PASSED
═══════════════════════════════════════════════════════════════════

✓ All layers passed:
  ✓ Layer 1: Cardinality matches
  ✓ Layer 2: Results normalized correctly
  ✓ Layer 3: All partitions match exactly

The MRUP metamorphic relation holds: H(t1 ∪ t2) = H(t1) ∪ H(t2)
```

---

## Why This Works

### The Mathematical Foundation

The MRUP relation works because of the **partition independence property** of window functions:

**Theorem**: For a window function `H` with `PARTITION BY P`:
```
If partitions(t1) ∩ partitions(t2) = ∅  (disjoint)
Then H(t1 ∪ t2) = H(t1) ∪ H(t2)
```

**Proof sketch**:
1. Window functions with `PARTITION BY P` process each partition independently
2. If partitions are disjoint, then:
   - Rows from `t1` never interact with rows from `t2` during window function evaluation
   - Running `H` on `t1 ∪ t2` is equivalent to running `H` on `t1` and `t2` separately
3. Therefore, the results must be identical (modulo row order)

### Why Constraints Matter

Each constraint ensures the MRUP relation holds:

- **C0 (PARTITION BY mandatory)**: Without partitioning, rows from `t1` and `t2` would interact
- **C1 (PARTITION BY dept only)**: Ensures we partition by the disjoint column
- **C2 (ORDER BY salary/age only)**: Simplifies testing, avoids edge cases
- **C3 (No FRAME for ranking)**: Prevents syntax errors
- **C4 (RANGE with single ORDER BY)**: Prevents syntax errors
- **C5 (Deterministic functions)**: Ensures reproducible results

### Why 3-Layer Comparison

Each layer serves a specific purpose:

1. **Layer 1 (Cardinality)**: Fast fail for major issues
2. **Layer 2 (Normalization)**: Handles different physical orders from SQLite
3. **Layer 3 (Per-Partition)**: Detailed comparison with type awareness

Without Layer 2, we'd get false positives when SQLite returns rows in different orders.
Without Layer 3, we'd miss subtle type-related bugs.

---

## Summary

The MRUP Oracle works by:

1. ✅ **Creating two tables** with identical schema
2. ✅ **Generating disjoint partition data** (no partition overlap)
3. ✅ **Generating valid window function queries** (following 6 strict constraints)
4. ✅ **Executing queries** on `t1`, `t2`, and `t1 ∪ t2`
5. ✅ **Comparing results** using a 3-layer architecture:
   - Layer 1: Cardinality check
   - Layer 2: MRUP normalization (semantic sorting)
   - Layer 3: Per-partition comparison (type-aware)

This approach allows us to **test window functions without knowing the correct answer**, relying instead on the metamorphic relation `H(t1 ∪ t2) = H(t1) ∪ H(t2)`.

---

## Files Reference

- **Schema & Data Generation**: `src/sqlancer/sqlite3/gen/SQLite3MRUPTablePairGenerator.java`
- **Window Function Generation**: `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`
- **Query Execution**: `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`
- **3-Layer Comparator**: `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`
- **Logging**: `src/sqlancer/sqlite3/oracle/SQLite3MRUPTestCaseLogger.java`
- **Analysis Tool**: `analyze_mrup_log_detailed.py`

---

**End of End-to-End Explanation**

You now have a complete understanding of how the MRUP Oracle works from start to finish! 🎉

