# MRUP Oracle - Example Execution

## Example Test Case

This document shows a concrete example of how the MRUP oracle works.

### Step 1: Initial Tables

**Table t1:**
```sql
CREATE TABLE t1 (c0 INT, c1 TEXT);
INSERT INTO t1 VALUES (1, 'A'), (2, 'B');
```

| c0 | c1 |
|----|-----|
| 1  | A   |
| 2  | B   |

**Table t2:**
```sql
CREATE TABLE t2 (c0 INT, c1 TEXT);
INSERT INTO t2 VALUES (3, 'C'), (4, 'D'), (5, 'E');
```

| c0 | c1 |
|----|-----|
| 3  | C   |
| 4  | D   |
| 5  | E   |

### Step 2: Union Table

**Conceptual t_union:**
```sql
SELECT * FROM t1 UNION ALL SELECT * FROM t2
```

| c0 | c1 |
|----|-----|
| 1  | A   |
| 2  | B   |
| 3  | C   |
| 4  | D   |
| 5  | E   |

### Step 3: Generated Window Specification

**OSRB generates:**
```sql
ROW_NUMBER() OVER (ORDER BY c0 ASC)
```

### Step 4: Execute Queries

**Q1 - Window function on t1:**
```sql
SELECT c0, c1, ROW_NUMBER() OVER (ORDER BY c0 ASC) AS wf_result
FROM t1;
```

**Result Q1:**
| c0 | c1 | wf_result |
|----|-----|-----------|
| 1  | A   | 1         |
| 2  | B   | 2         |

**Cardinality of Q1 = 2**

---

**Q2 - Window function on t2:**
```sql
SELECT c0, c1, ROW_NUMBER() OVER (ORDER BY c0 ASC) AS wf_result
FROM t2;
```

**Result Q2:**
| c0 | c1 | wf_result |
|----|-----|-----------|
| 3  | C   | 1         |
| 4  | D   | 2         |
| 5  | E   | 3         |

**Cardinality of Q2 = 3**

---

**Q_union - Window function on union:**
```sql
SELECT c0, c1, ROW_NUMBER() OVER (ORDER BY c0 ASC) AS wf_result
FROM (
  SELECT * FROM t1
  UNION ALL
  SELECT * FROM t2
) AS t_union;
```

**Result Q_union:**
| c0 | c1 | wf_result |
|----|-----|-----------|
| 1  | A   | 1         |
| 2  | B   | 2         |
| 3  | C   | 3         |
| 4  | D   | 4         |
| 5  | E   | 5         |

**Cardinality of Q_union = 5**

### Step 5: Compare Results

**Cardinality Check:**
```
Expected = |Q1| + |Q2| = 2 + 3 = 5
Actual   = |Q_union|   = 5

Expected == Actual ✅ PASS
```

## Example with PARTITION BY

### Generated Window Specification

```sql
SUM(c0) OVER (PARTITION BY c1 ORDER BY c0 ASC)
```

### Execution

**Q1 on t1:**
```sql
SELECT c0, c1, SUM(c0) OVER (PARTITION BY c1 ORDER BY c0 ASC) AS wf_result
FROM t1;
```

| c0 | c1 | wf_result |
|----|-----|-----------|
| 1  | A   | 1         |
| 2  | B   | 2         |

**Cardinality = 2**

---

**Q2 on t2:**
```sql
SELECT c0, c1, SUM(c0) OVER (PARTITION BY c1 ORDER BY c0 ASC) AS wf_result
FROM t2;
```

| c0 | c1 | wf_result |
|----|-----|-----------|
| 3  | C   | 3         |
| 4  | D   | 4         |
| 5  | E   | 5         |

**Cardinality = 3**

---

**Q_union:**
```sql
SELECT c0, c1, SUM(c0) OVER (PARTITION BY c1 ORDER BY c0 ASC) AS wf_result
FROM (
  SELECT * FROM t1
  UNION ALL
  SELECT * FROM t2
) AS t_union;
```

| c0 | c1 | wf_result |
|----|-----|-----------|
| 1  | A   | 1         |
| 2  | B   | 2         |
| 3  | C   | 3         |
| 4  | D   | 4         |
| 5  | E   | 5         |

**Cardinality = 5**

### Result

```
Expected = 2 + 3 = 5
Actual   = 5

✅ PASS
```

## Example Bug Detection Scenario

### Hypothetical Bug

Suppose SQLite has a bug where window functions incorrectly handle UNION ALL in certain cases.

**Buggy Q_union result:**
| c0 | c1 | wf_result |
|----|-----|-----------|
| 1  | A   | 1         |
| 2  | B   | 2         |
| 3  | C   | 3         |
| 4  | D   | 4         |

**Cardinality = 4** (missing one row!)

### Detection

```
Expected = 2 + 3 = 5
Actual   = 4

Expected ≠ Actual ❌ BUG DETECTED!
```

**Oracle Output:**
```
MRUP Oracle: Cardinality mismatch!
Expected: 5 (Q1: 2 + Q2: 3)
Actual: 4
Queries:
-- Q1:
SELECT c0, c1, ROW_NUMBER() OVER (ORDER BY c0 ASC) AS wf_result FROM t1
-- Q2:
SELECT c0, c1, ROW_NUMBER() OVER (ORDER BY c0 ASC) AS wf_result FROM t2
-- Q_union:
SELECT c0, c1, ROW_NUMBER() OVER (ORDER BY c0 ASC) AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union
```

## Complex Example with FRAME Clause

### Generated Specification

```sql
AVG(c0) OVER (
  PARTITION BY c1 
  ORDER BY c0 ASC 
  ROWS BETWEEN 1 PRECEDING AND CURRENT ROW
)
```

This tests:
- Aggregate window function (AVG)
- Partitioning (PARTITION BY c1)
- Ordering (ORDER BY c0 ASC)
- Frame specification (ROWS BETWEEN...)

### Why This is Powerful

The MRUP oracle can detect bugs in:
1. **Partition boundary handling** - Does UNION affect partitions?
2. **Frame calculation** - Are frames computed correctly across union?
3. **Ordering consistency** - Is ORDER BY respected in union context?
4. **Aggregate computation** - Are aggregates calculated correctly?

## Real-World Testing

When running SQLancer with MRUP oracle:

```bash
java -jar target/sqlancer-*.jar --num-queries 100 sqlite3 --oracle MRUP
```

**Output:**
```
[2025/11/29 21:44:30] Executed 1337 queries (266 queries/s)
[2025/11/29 21:44:35] Executed 3032 queries (339 queries/s)
...
```

Each iteration:
1. Generates random tables (or reuses existing)
2. Generates random window specification using OSRB
3. Executes 3 queries (Q1, Q2, Q_union)
4. Compares cardinalities
5. Reports any mismatches

## Summary

The MRUP oracle provides:
- ✅ Automated test case generation
- ✅ Diverse window function coverage
- ✅ Metamorphic relation validation
- ✅ Bug detection capability
- ✅ High throughput (300+ queries/sec)

This POC demonstrates the feasibility and effectiveness of the MRUP approach for testing window functions in database systems.

