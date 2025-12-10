# Phase 3: Smart Comparator Design & Analysis

## 🎯 Goal

Design a **smart, exact comparator** that:
1. **Reduces false positives** from ~10-20% to <5%
2. **Preserves MRUP metamorphic relation semantics**
3. **Handles all edge cases** (NULL, duplicates, ordering, partitions)
4. **Is mathematically correct** for window function semantics

---

## 📊 Current State Analysis

### What We Have Now

#### 1. **Current Comparison Logic** (Lines 487-546 in SQLite3MRUPOracle.java)

```java
// ❌ PROBLEM 1: Naive lexicographic sorting by ALL columns
private void sortResults(List<List<String>> results) {
    results.sort((row1, row2) -> {
        for (int i = 0; i < Math.min(row1.size(), row2.size()); i++) {
            int cmp = row1.get(i).compareTo(row2.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(row1.size(), row2.size());
    });
}

// ❌ PROBLEM 2: Simple string equality comparison
private boolean resultsMatch(List<List<String>> expected, List<List<String>> actual) {
    if (expected.size() != actual.size()) {
        return false;
    }
    
    for (int i = 0; i < expected.size(); i++) {
        List<String> expectedRow = expected.get(i);
        List<String> actualRow = actual.get(i);
        
        if (expectedRow.size() != actualRow.size()) {
            return false;
        }
        
        for (int j = 0; j < expectedRow.size(); j++) {
            if (!expectedRow.get(j).equals(actualRow.get(j))) {
                return false;
            }
        }
    }
    
    return true;
}
```

#### 2. **Data Representation** (Lines 455-485)

```java
private List<List<String>> executeAndGetResults(String query) throws SQLException {
    List<List<String>> results = new ArrayList<>();
    
    // All values converted to String (including NULL → "NULL")
    while (rs.next()) {
        List<String> row = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            Object value = rs.getObject(i);
            row.add(value == null ? "NULL" : value.toString());
        }
        results.add(row);
    }
    
    return results;
}
```

#### 3. **Query Structure** (Lines 393-448)

```java
// Q1: SELECT dept, salary, age, ROW_NUMBER() OVER (...) AS wf_result FROM t1
// Q2: SELECT dept, salary, age, ROW_NUMBER() OVER (...) AS wf_result FROM t2
// Q_union: SELECT dept, salary, age, ROW_NUMBER() OVER (...) AS wf_result 
//          FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union

// Result structure:
// [dept, salary, age, ..., wf_result]
//  ^^^^^^^^^^^^^^^^^^^     ^^^^^^^^^^
//  Base columns            Window function result
```

---

## ❌ Why Current Approach Fails

### Problem 1: Sorts by ALL Columns (Breaks Window Semantics)

**Example:**

```sql
-- Window function: ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)

-- Q1 result (Finance partition):
Finance | 80000 | 35 | 1
Finance | 50000 | 30 | 2

-- Q2 result (Sales partition):
Sales   | 70000 | 28 | 1

-- Expected (Q1 ∪ Q2):
Finance | 80000 | 35 | 1
Finance | 50000 | 30 | 2
Sales   | 70000 | 28 | 1

-- Q_union result (correct):
Finance | 80000 | 35 | 1
Finance | 50000 | 30 | 2
Sales   | 70000 | 28 | 1
```

**Current sorting (by ALL columns lexicographically):**
```
Expected after sort:
Finance | 50000 | 30 | 2  ← ❌ WRONG! Sorted by salary ASC, not DESC
Finance | 80000 | 35 | 1
Sales   | 70000 | 28 | 1

Actual after sort:
Finance | 50000 | 30 | 2
Finance | 80000 | 35 | 1
Sales   | 70000 | 28 | 1

Comparison: MATCH ✓ (but semantically WRONG!)
```

**The problem:** Current sorting **changes the logical order** within partitions, which can hide real bugs or create false positives.

---

### Problem 2: Ignores Window Function Semantics

**Window functions depend on:**
1. **Partition boundaries** (PARTITION BY dept)
2. **Ordering within partitions** (ORDER BY salary DESC)
3. **Frame specifications** (ROWS/RANGE)

**Current approach ignores all of this!**

---

### Problem 3: String Comparison is Fragile

```java
// Current: "50000" vs "50000.0" → MISMATCH ❌
// Current: "NULL" vs "null" → MISMATCH ❌
// Current: "1.5" vs "1.50" → MISMATCH ❌
```

---

### Problem 4: No Per-Partition Validation

**MRUP metamorphic relation:**
```
For each partition P:
  Q_union[P] = Q1[P] if P ∈ t1 only
  Q_union[P] = Q2[P] if P ∈ t2 only
```

**Current approach:** Compares entire result sets globally, not per-partition.

---

## ✅ What Constraints Should We Follow?

### Constraint 1: MRUP Metamorphic Relation (Core Correctness)

```
H(t_union) = H(t1) ∪ H(t2)

Where:
- H() is a window function with PARTITION BY dept
- t_union = t1 UNION ALL t2
- t1 and t2 have DISJOINT partition values
```

**Implication for comparison:**
- Must compare **per partition**, not globally
- Partitions from t1 should appear unchanged in Q_union
- Partitions from t2 should appear unchanged in Q_union
- No partition should merge or split

---

### Constraint 2: Window Function Semantics (Ordering)

**Window functions define order within each partition:**

```sql
OVER (PARTITION BY dept ORDER BY salary DESC, age ASC)
      ^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      Partition         Order within partition
```

**Implication for comparison:**
- Sorting must preserve **partition boundaries**
- Sorting must respect **ORDER BY clause** from window spec
- Sorting must be **deterministic** (tie-breakers)

---

### Constraint 3: Deterministic Comparison (No Ambiguity)

**We've enforced (Phase 2):**
- C0: PARTITION BY dept (mandatory)
- C2: ORDER BY only uses salary/age
- C5: Only deterministic functions

**Implication for comparison:**
- ORDER BY salary, age should be sufficient for determinism
- If ties exist, window function result (wf_result) is the final tie-breaker
- NULL handling must follow SQL semantics (NULLS FIRST/LAST)

---

### Constraint 4: Type-Aware Comparison (Not Just Strings)

**Column types in our schema:**
- `dept`: TEXT (partition key)
- `salary`: INTEGER (order key 1)
- `age`: INTEGER (order key 2)
- `c0`, `c1`: TEXT/INTEGER/REAL (optional)
- `wf_result`: INTEGER/REAL (window function result)

**Implication for comparison:**
- Must handle NULL correctly
- Must handle numeric precision (50000 vs 50000.0)
- Must handle string collation

---

### Constraint 5: Disjoint Partition Guarantee (Phase 1)

**We've enforced:**
- t1 partitions: Set A = {Finance, Engineering, HR, NULL}
- t2 partitions: Set B = {Sales, Marketing, Operations}
- A ∩ B = ∅

**Implication for comparison:**
- Each partition in Q_union comes from EITHER t1 OR t2, never both
- Can validate per-partition independently

---

## 🧠 What Should We Implement?

### Architecture: 3-Layer Comparison

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Cardinality Check (Fast Fail)                      │
│ - Check: |Q_union| = |Q1| + |Q2|                            │
│ - If fail: Report bug immediately                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: MRUP Normalization (Semantic Sorting)              │
│ - Extract window spec info (PARTITION BY, ORDER BY)         │
│ - Sort by: partition → ORDER BY keys → wf_result            │
│ - Preserve window semantics                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Per-Partition Comparison (Exact Match)             │
│ - Group by partition                                        │
│ - Compare each partition independently                      │
│ - Type-aware value comparison                               │
└─────────────────────────────────────────────────────────────┘
```

---

### Component 1: Window Specification Extractor

**Purpose:** Extract PARTITION BY and ORDER BY info from the generated window function.

**What to extract:**
```java
class WindowSpec {
    String partitionColumn;           // "dept"
    List<String> orderByColumns;      // ["salary", "age"]
    List<String> orderByDirections;   // ["DESC", "ASC"]
    List<String> nullsHandling;       // ["NULLS LAST", "NULLS FIRST"]
}
```

**Why needed:**
- Current code generates window spec but doesn't store it
- Comparison needs to know the ORDER BY to sort correctly

**Implementation approach:**
```java
// In check() method, after generating window spec:
String windowSpec = generateWindowSpecOSRB(columns);
WindowSpec parsedSpec = parseWindowSpec(windowSpec);

// Store for later use in comparison
this.currentWindowSpec = parsedSpec;
```

---

### Component 2: MRUP Normalization Sorter

**Purpose:** Sort results in a way that preserves window function semantics.

**Algorithm:**

```
For each result set (Q1, Q2, Q_union):
  Sort rows by:
    1. Partition key (dept)
       - NULL handling: NULLS FIRST or NULLS LAST (from SQL standard)
    2. ORDER BY keys (salary, age) in specified order
       - Respect ASC/DESC from window spec
       - Respect NULLS FIRST/LAST from window spec
    3. Window function result (wf_result) as tie-breaker
       - Always ASC (row_number is 1, 2, 3, ...)
```

**Pseudocode:**

```java
private void normalizeForMRUP(List<List<String>> results, WindowSpec spec) {
    results.sort((row1, row2) -> {
        // 1. Compare partition key
        int partitionCmp = compareValue(
            row1.get(spec.partitionColumnIndex), 
            row2.get(spec.partitionColumnIndex),
            spec.partitionColumnType,
            "NULLS FIRST"  // SQL standard default
        );
        if (partitionCmp != 0) return partitionCmp;
        
        // 2. Compare ORDER BY keys in order
        for (int i = 0; i < spec.orderByColumns.size(); i++) {
            int colIndex = spec.orderByColumnIndices.get(i);
            String direction = spec.orderByDirections.get(i);
            String nullsHandling = spec.nullsHandling.get(i);
            
            int cmp = compareValue(
                row1.get(colIndex), 
                row2.get(colIndex),
                spec.orderByColumnTypes.get(i),
                nullsHandling
            );
            
            if (direction.equals("DESC")) {
                cmp = -cmp;
            }
            
            if (cmp != 0) return cmp;
        }
        
        // 3. Compare window function result (tie-breaker)
        int wfIndex = row1.size() - 1;  // wf_result is last column
        int wfCmp = compareValue(
            row1.get(wfIndex), 
            row2.get(wfIndex),
            "INTEGER",
            "NULLS LAST"
        );
        
        return wfCmp;
    });
}
```

**Key features:**
- ✅ Preserves partition boundaries
- ✅ Respects window ORDER BY semantics
- ✅ Deterministic (tie-breaker)
- ✅ NULL-aware
- ✅ Type-aware

---

### Component 3: Type-Aware Value Comparator

**Purpose:** Compare values correctly based on their types, not just as strings.

**Algorithm:**

```java
private int compareValue(String val1, String val2, String type, String nullsHandling) {
    // 1. Handle NULL
    boolean isNull1 = val1.equals("NULL");
    boolean isNull2 = val2.equals("NULL");
    
    if (isNull1 && isNull2) return 0;
    if (isNull1) return nullsHandling.equals("NULLS FIRST") ? -1 : 1;
    if (isNull2) return nullsHandling.equals("NULLS FIRST") ? 1 : -1;
    
    // 2. Type-specific comparison
    switch (type) {
        case "INTEGER":
            return Long.compare(Long.parseLong(val1), Long.parseLong(val2));
        
        case "REAL":
            // Handle floating point with epsilon
            double d1 = Double.parseDouble(val1);
            double d2 = Double.parseDouble(val2);
            if (Math.abs(d1 - d2) < 1e-9) return 0;
            return Double.compare(d1, d2);
        
        case "TEXT":
            return val1.compareTo(val2);  // Lexicographic
        
        default:
            return val1.compareTo(val2);
    }
}
```

**Key features:**
- ✅ NULL handling (NULLS FIRST/LAST)
- ✅ Numeric comparison (not string comparison)
- ✅ Floating point tolerance
- ✅ String collation

---

### Component 4: Per-Partition Comparator

**Purpose:** Compare results partition-by-partition, not globally.

**Algorithm:**

```
1. Group Q1 results by partition key → Map<String, List<Row>>
2. Group Q2 results by partition key → Map<String, List<Row>>
3. Group Q_union results by partition key → Map<String, List<Row>>

4. For each partition P in Q_union:
   a. Check if P exists in Q1 or Q2 (not both, due to disjoint constraint)
   b. If P ∈ Q1:
      - Compare Q_union[P] with Q1[P]
      - Must match exactly (row-by-row, column-by-column)
   c. If P ∈ Q2:
      - Compare Q_union[P] with Q2[P]
      - Must match exactly
   d. If mismatch found:
      - Report bug with partition details

5. Check no partition is missing or extra
```

**Pseudocode:**

```java
private boolean comparePerPartition(
    List<List<String>> q1Results, 
    List<List<String>> q2Results, 
    List<List<String>> qUnionResults,
    WindowSpec spec
) {
    // Group by partition
    Map<String, List<List<String>>> q1Partitions = groupByPartition(q1Results, spec);
    Map<String, List<List<String>>> q2Partitions = groupByPartition(q2Results, spec);
    Map<String, List<List<String>>> qUnionPartitions = groupByPartition(qUnionResults, spec);
    
    // Check each partition in Q_union
    for (String partition : qUnionPartitions.keySet()) {
        List<List<String>> unionRows = qUnionPartitions.get(partition);
        
        // Determine source (Q1 or Q2)
        if (q1Partitions.containsKey(partition)) {
            // This partition should come from Q1
            List<List<String>> expectedRows = q1Partitions.get(partition);
            
            if (!rowsMatch(expectedRows, unionRows, spec)) {
                reportBug(partition, expectedRows, unionRows, "Q1");
                return false;
            }
        } else if (q2Partitions.containsKey(partition)) {
            // This partition should come from Q2
            List<List<String>> expectedRows = q2Partitions.get(partition);
            
            if (!rowsMatch(expectedRows, unionRows, spec)) {
                reportBug(partition, expectedRows, unionRows, "Q2");
                return false;
            }
        } else {
            // Partition in Q_union but not in Q1 or Q2 → BUG!
            reportBug(partition, null, unionRows, "UNKNOWN");
            return false;
        }
    }
    
    // Check no partition is missing
    Set<String> allPartitions = new HashSet<>();
    allPartitions.addAll(q1Partitions.keySet());
    allPartitions.addAll(q2Partitions.keySet());
    
    if (!allPartitions.equals(qUnionPartitions.keySet())) {
        reportMissingPartitions(allPartitions, qUnionPartitions.keySet());
        return false;
    }
    
    return true;
}
```

**Key features:**
- ✅ Per-partition validation (MRUP correctness)
- ✅ Detects partition merging bugs
- ✅ Detects missing/extra partitions
- ✅ Clear error reporting

---

### Component 5: Enhanced Bug Reporter

**Purpose:** Provide clear, actionable bug reports with partition-level details.

**What to report:**

```
BUG FOUND: MRUP Metamorphic Relation Violated

Partition: Finance
Source: Q1 (t1)

Expected (Q1[Finance]):
  dept    | salary | age | wf_result
  --------|--------|-----|----------
  Finance | 80000  | 35  | 1
  Finance | 50000  | 30  | 2

Actual (Q_union[Finance]):
  dept    | salary | age | wf_result
  --------|--------|-----|----------
  Finance | 80000  | 35  | 1
  Finance | 50000  | 30  | 3  ← MISMATCH!

Difference: Row 2, Column wf_result: Expected 2, Got 3

Window Function: ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)

This indicates a bug in the window function implementation when applied to UNION ALL.
```

---

## 📋 Implementation Checklist

### Step 1: Extract Window Specification Info
- [ ] Create `WindowSpec` class to store partition/order info
- [ ] Parse generated window spec string
- [ ] Extract partition column name and index
- [ ] Extract ORDER BY columns, directions, NULLS handling
- [ ] Store column types for type-aware comparison

### Step 2: Implement MRUP Normalization
- [ ] Replace `sortResults()` with `normalizeForMRUP()`
- [ ] Sort by: partition → ORDER BY keys → wf_result
- [ ] Respect ASC/DESC from window spec
- [ ] Handle NULLS FIRST/LAST correctly

### Step 3: Implement Type-Aware Comparison
- [ ] Create `compareValue()` method
- [ ] Handle NULL values (NULLS FIRST/LAST)
- [ ] Handle INTEGER comparison (numeric, not string)
- [ ] Handle REAL comparison (with epsilon tolerance)
- [ ] Handle TEXT comparison (lexicographic)

### Step 4: Implement Per-Partition Comparison
- [ ] Create `groupByPartition()` method
- [ ] Create `comparePerPartition()` method
- [ ] Compare each partition independently
- [ ] Validate no partition merging
- [ ] Validate no missing/extra partitions

### Step 5: Enhance Bug Reporting
- [ ] Update `findFirstDifference()` to show partition info
- [ ] Show expected vs actual per partition
- [ ] Show window function spec in bug report
- [ ] Generate standalone SQL script with partition data

### Step 6: Testing & Validation
- [ ] Test with various window functions (ROW_NUMBER, RANK, SUM, etc.)
- [ ] Test with NULL values in partition key
- [ ] Test with NULL values in ORDER BY columns
- [ ] Test with duplicate values (ties)
- [ ] Test with DESC ordering
- [ ] Test with NULLS FIRST/LAST
- [ ] Measure false positive rate (target: <5%)

---

## 🎯 Expected Outcomes

### Before Phase 3 (Current):
- False positive rate: **~10-20%**
- Comparison: Naive lexicographic sorting
- Semantics: **Incorrect** (breaks window function order)
- Debugging: Difficult (no partition-level info)

### After Phase 3 (Target):
- False positive rate: **<5%**
- Comparison: MRUP-aware semantic sorting
- Semantics: **Correct** (preserves window function order)
- Debugging: Easy (partition-level details)

---

## 🔬 Example: Before vs After

### Scenario

```sql
-- Window function
ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)

-- Q1 (Finance partition)
Finance | 80000 | 35 | 1
Finance | 50000 | 30 | 2

-- Q2 (Sales partition)
Sales   | 70000 | 28 | 1

-- Q_union (correct)
Finance | 80000 | 35 | 1
Finance | 50000 | 30 | 2
Sales   | 70000 | 28 | 1
```

### Before Phase 3 (Current)

```
Sort by ALL columns (lexicographic):
Expected: [Finance, 50000, 30, 2], [Finance, 80000, 35, 1], [Sales, 70000, 28, 1]
Actual:   [Finance, 50000, 30, 2], [Finance, 80000, 35, 1], [Sales, 70000, 28, 1]
Result: MATCH ✓

Problem: Sorted by salary ASC (50000 before 80000), but window spec says DESC!
This hides potential bugs in window function ordering.
```

### After Phase 3 (Correct)

```
Sort by: partition (dept) → ORDER BY (salary DESC) → wf_result:
Expected: [Finance, 80000, 35, 1], [Finance, 50000, 30, 2], [Sales, 70000, 28, 1]
Actual:   [Finance, 80000, 35, 1], [Finance, 50000, 30, 2], [Sales, 70000, 28, 1]
Result: MATCH ✓

Semantics: Correct! Sorted by salary DESC as specified in window function.
```

---

## 🚀 Next Steps

1. **Read this analysis carefully** to understand the problem
2. **Design the WindowSpec class** to store parsed window info
3. **Implement MRUP normalization** (sorting algorithm)
4. **Implement type-aware comparison** (NULL, numeric, string)
5. **Implement per-partition comparison** (MRUP correctness)
6. **Test thoroughly** with various scenarios
7. **Measure false positive rate** (target: <5%)

---

## 📚 Key Takeaways

1. **Current comparison is semantically incorrect** (sorts by ALL columns)
2. **MRUP requires per-partition comparison** (not global)
3. **Window function semantics must be preserved** (ORDER BY, PARTITION BY)
4. **Type-aware comparison is essential** (not just string equality)
5. **Deterministic sorting is critical** (tie-breakers)
6. **Clear bug reporting helps debugging** (partition-level details)

**Phase 3 is the most critical phase** for making the MRUP oracle actually work correctly!

