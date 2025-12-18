# MRUP Oracle Specification
## Single Source of Truth - Implementation-Based Documentation

**Version**: Current Codebase (as of latest commit)  
**Purpose**: Complete, accurate specification of what the MRUP Oracle actually implements  
**Scope**: This document describes ONLY what is implemented in code, not planned features

---

## 1. Oracle Goal and Scope

### 1.1 Primary Goal
Test window functions in SQLite3 for logic bugs using metamorphic testing.

### 1.2 Metamorphic Relation
The oracle implements the MRUP (MR-UNION-PARTITION) relation:

```
H(t1 ∪ t2) = H(t1) ∪ H(t2)
```

Where:
- `H` is a window function query with PARTITION BY
- `t1`, `t2` are tables with identical schema
- `t1` and `t2` have disjoint partition values
- `∪` is UNION ALL

### 1.3 Target DBMS
- **Primary**: SQLite3
- **Version**: 3.25.0+ (window functions support)
- **Integration**: SQLancer framework

---

## 2. End-to-End Workflow

### 2.1 High-Level Steps
1. Generate table pair (t1, t2) with disjoint partitions
2. Generate window function query H with constraints C0-C5
3. Apply mutations (identity + CASE WHEN, 100% rate)
4. Execute H on t1, t2, and t_union
5. Compare results using 3-layer comparator
6. Report bug if mismatch detected

### 2.2 Detailed Execution Flow

**File**: `SQLite3MRUPOracle.java::check()`

```
Step 1: Table Pair Generation
├─ Call SQLite3MRUPTablePairGenerator.generateMRUPTablePair()
├─ Returns [t1, t2] with same schema
├─ Verify both tables have data (>0 rows)
└─ Collect partition values for logging

Step 2: Window Function Type Selection
├─ 98% aggregate functions (SUM, AVG, COUNT, MIN, MAX)
└─ 2% ranking functions (ROW_NUMBER, RANK, DENSE_RANK)

Step 3: Window Spec Generation
├─ Call generateWindowSpecOSRB(columns)
├─ Mandatory: PARTITION BY dept
├─ Mandatory: ORDER BY salary/age (1-3 columns)
├─ Optional: Frame clause (if aggregate function)
└─ Store lastOrderByColumnCount for C4 validation

Step 4: Window Spec Mutations (Optional)
├─ Try up to 5 times to apply mutation
├─ Call SQLite3MRUPMutationOperator.applyRandomMutations()
└─ ~90% success rate

Step 5: Window Function Generation
├─ Call generateWindowFunction(type, column, spec)
└─ Returns complete window function string

Step 6: Identity Mutations (98% rate)
├─ Call SQLite3MRUPIdentityMutator.applyIdentityWrapper()
├─ Mutates argument: SUM(c1) → SUM(c1 + 0)
└─ Skipped for ranking functions (no argument)

Step 7: CASE WHEN Mutations (100% rate)
├─ Always apply one of 5 strategies:
│  ├─ 30%: Constant Condition
│  ├─ 25%: Window Function in WHEN
│  ├─ 20%: Different Window Functions
│  ├─ 15%: Identical Branches
│  └─ 10%: NULL Handling
└─ Call appropriate SQLite3MRUPCaseMutator method

Step 8: Parse Window Spec
├─ Call parseWindowSpec(windowSpec, columns)
└─ Extract PARTITION BY, ORDER BY metadata

Step 9: Build Queries
├─ Q1: SELECT ..., wf AS wf_result FROM t1
├─ Q2: SELECT ..., wf AS wf_result FROM t2
└─ Q_union: SELECT ..., wf AS wf_result FROM (t1 UNION ALL t2)

Step 10: Execute Queries
├─ Call executeAndGetResults(Q1) → R1
├─ Call executeAndGetResults(Q2) → R2
└─ Call executeAndGetResults(Q_union) → R_union

Step 11: Layer 1 - Cardinality Check
├─ Check: |R_union| == |R1| + |R2|
└─ If mismatch: Report bug immediately

Step 12: Layer 2 - MRUP Normalization
├─ Call normalizeForMRUP(R1, windowSpec)
├─ Call normalizeForMRUP(R2, windowSpec)
├─ Call normalizeForMRUP(R_union, windowSpec)
└─ Sort by: partition → ORDER BY cols → wf_result

Step 13: Layer 3 - Per-Partition Comparison
├─ Call comparePerPartition(R1, R2, R_union, windowSpec)
├─ Group results by partition
├─ Compare each partition independently
└─ If mismatch: Report bug with details

Step 14: Logging
├─ Write test case to file (mrup_logs/)
└─ Include: tables, queries, results, mutations
```

---

## 3. Supported Query Classes

### 3.1 SELECT Structure
**Implemented**:
```sql
SELECT col1, col2, ..., colN, window_function AS wf_result
FROM table_name
```

**NOT Implemented**:
- WHERE clauses
- JOIN operations
- GROUP BY / HAVING
- DISTINCT
- LIMIT / OFFSET
- Subqueries in FROM
- CTEs (WITH clauses)
- Multiple window functions per query

### 3.2 Window Functions
**Implemented**:
- Ranking: ROW_NUMBER(), RANK(), DENSE_RANK()
- Aggregate: SUM(col), AVG(col), COUNT(col), MIN(col), MAX(col)

**NOT Implemented**:
- NTILE()
- LAG(), LEAD()
- FIRST_VALUE(), LAST_VALUE(), NTH_VALUE()
- PERCENT_RANK(), CUME_DIST()
- Custom aggregates

### 3.3 Window Specification
**Implemented**:
```sql
OVER (
  PARTITION BY dept
  ORDER BY col1 [ASC|DESC] [NULLS FIRST|LAST] [, col2 ...]
  [ROWS|RANGE frame_clause]
)
```

**Components**:
- PARTITION BY: Always `dept` (single column)
- ORDER BY: 1-3 columns from {salary, age}
- Frame: ROWS or RANGE with various boundaries
- Frame exclusion: EXCLUDE NO OTHERS, TIES, CURRENT ROW, GROUP

**NOT Implemented**:
- Multi-column PARTITION BY
- Expression-based PARTITION BY
- ORDER BY with expressions
- GROUPS frame mode

---

## 4. Constraints (C0-C5)

### C0: PARTITION BY is MANDATORY
**Enforcement**: `generateWindowSpecOSRB()` always adds `PARTITION BY dept`  
**Reason**: MRUP relation requires partition locality  
**Code**: Lines 392-401 in SQLite3MRUPOracle.java

### C1: PARTITION BY only uses 'dept'
**Enforcement**: Hardcoded to use `dept` column only  
**Reason**: Ensures disjoint partitions between t1 and t2  
**Code**: Lines 395-400 in SQLite3MRUPOracle.java

### C2: ORDER BY only uses salary/age
**Enforcement**: `findOrderableColumns()` returns only {salary, age}  
**Reason**: Deterministic ordering on numeric columns  
**Code**: Lines 455-465 in SQLite3MRUPOracle.java

### C3: No FRAME for ranking functions
**Enforcement**: Frame generation skipped if `isRankingFunction == true`  
**Reason**: SQLite restriction  
**Code**: Lines 136-145 in SQLite3MRUPOracle.java

### C4: RANGE only with single ORDER BY
**Enforcement**: Frame type selection checks `lastOrderByColumnCount`  
**Reason**: SQLite restriction  
**Code**: Lines 477-484 in SQLite3MRUPOracle.java

### C5: Only deterministic functions
**Enforcement**: Function type selection limited to 8 deterministic functions  
**Reason**: Avoid false positives from non-determinism  
**Code**: Lines 118-129 in SQLite3MRUPOracle.java

---

## 5. Table Generation

### 5.1 Schema Structure
**File**: `SQLite3MRUPTablePairGenerator.java`

**Fixed columns**:
- `dept TEXT` - partition key
- `salary INTEGER|REAL` - orderable (66% INT, 33% REAL)
- `age INTEGER` - orderable (70% chance to include)

**Variable columns**:
- 1-4 additional columns: `c0, c1, c2, c3`
- Types: 40% INTEGER, 30% REAL, 30% TEXT
- All additional columns are nullable

**Total columns**: 3-7 per table

### 5.2 Disjoint Partition Strategy
**Partition Set A** (for t1): {Finance, Engineering, HR}  
**Partition Set B** (for t2): {Sales, Marketing, Operations}  
**Guarantee**: A ∩ B = ∅

**NULL partition**: Can appear in t1 only (not in both tables)

### 5.3 Data Generation
**Row count**: 3-8 rows per table  
**Partitions per table**: 2-3 partitions

**Value generation**:
- `salary`: 20000-100000 (rounded to 5000 for duplicates)
  - 10% edge cases: MIN_INT, MAX_INT, 0, -1, 1
- `age`: 20-65
  - 10% edge cases: 0, 1, -1, 100, 200
- Additional columns: Random values with 30% NULL rate
  - 15% edge cases for INTEGER
  - Intentional duplicates (20% chance)

---

## 6. Mutation Strategies

### 6.1 Window Spec Mutations (Optional, ~90% rate)
**File**: `SQLite3MRUPMutationOperator.java`

**Implemented mutations** (Top 10 strategies):
1. Redundant PARTITION BY
2. Order-preserving transforms
3. Frame boundary variations
4. NULLS FIRST/LAST variations
5. ASC/DESC variations
6. Multiple ORDER BY columns
7. Frame exclusion variations
8. ROWS vs RANGE variations
9. UNBOUNDED variations
10. Complex BETWEEN frames

**Application**: Try up to 5 times, ~90% success rate

### 6.2 Identity Mutations (98% rate)
**File**: `SQLite3MRUPIdentityMutator.java`

**Target**: Window function argument (NOT the windowed expression)

**Patterns**:
- `SUM(c1)` → `SUM(c1 + 0)`
- `AVG(salary)` → `AVG(salary * 1)`
- `COUNT(c0)` → `COUNT(c0 + 0)`

**Skipped for**:
- Ranking functions (no argument)
- COUNT(*) (no column argument)

**Variants**:
1. Addition: `arg + 0`
2. Subtraction: `arg - 0`
3. Multiplication: `arg * 1`
4. Division: `arg / 1`
5. Unary: `+arg`, `-(-arg)`
6. Parentheses: `(arg)`
7. COALESCE: `COALESCE(arg, arg)`
8. CAST: `CAST(arg AS type)`

### 6.3 CASE WHEN Mutations (100% rate)
**File**: `SQLite3MRUPCaseMutator.java`

**Always applied** - one of 5 strategies:

#### Strategy 1: Constant Condition (30%)
```sql
CASE WHEN TRUE THEN NULL ELSE wf END
CASE WHEN FALSE THEN NULL ELSE wf END
CASE WHEN 1=1 THEN 0 ELSE wf END
```
**Purpose**: Test dead branch elimination

#### Strategy 2: Window Function in WHEN (25%)
```sql
CASE WHEN wf <= 3 THEN 'TOP' ELSE 'OTHER' END
CASE WHEN wf IN (1,2,3) THEN 'TOP_3' ELSE 'OTHER' END
CASE WHEN wf IS NULL THEN 'NULL_RESULT' ELSE 'HAS_VALUE' END
```
**Purpose**: Test window function evaluation in conditions

#### Strategy 3: Different Window Functions (20%)
```sql
CASE WHEN condition
     THEN RANK() OVER (...)
     ELSE DENSE_RANK() OVER (...)
END
```
**Purpose**: Test multiple window functions, type mismatch

#### Strategy 4: Identical Branches (15%)
```sql
CASE WHEN condition
     THEN wf
     ELSE wf
END
```
**Purpose**: Test optimizer recognition of identical branches

#### Strategy 5: NULL Handling (10%)
```sql
CASE WHEN col IS NULL
     THEN 0
     ELSE wf
END
```
**Purpose**: Test NULL handling in conditions

---

## 7. Result Comparator (3-Layer)

### 7.1 Layer 1: Cardinality Check
**Purpose**: Fast fail for obvious bugs  
**Check**: `|R_union| == |R1| + |R2|`  
**Code**: Lines 309-327 in SQLite3MRUPOracle.java

### 7.2 Layer 2: MRUP Normalization
**Purpose**: Sort results preserving window semantics  
**Algorithm**:
1. Sort by partition key (dept)
2. Sort by ORDER BY columns (with direction + NULLS handling)
3. Sort by wf_result (tie-breaker)

**Code**: Lines 826-869 in SQLite3MRUPOracle.java

**Key insight**: Window functions don't guarantee global order, only within-partition order. Normalization ensures consistent comparison.

### 7.3 Layer 3: Per-Partition Comparison
**Purpose**: Validate MRUP relation per partition  
**Algorithm**:
1. Group R1, R2, R_union by partition
2. For each partition P in R_union:
   - If P ∈ t1: compare with R1[P]
   - If P ∈ t2: compare with R2[P]
3. Check no partitions missing

**Code**: Lines 922-975 in SQLite3MRUPOracle.java

**Type-aware comparison**:
- NULL: NULL == NULL is true
- INTEGER: Exact equality
- REAL: Epsilon comparison (|a - b| < 1e-9)
- TEXT: Lexicographic equality

---

## 8. Known Limitations

### 8.1 Query Simplicity
**NOT Implemented**:
- WHERE clauses
- JOINs (self-joins, cross joins, etc.)
- GROUP BY / HAVING
- Subqueries (correlated or uncorrelated)
- CTEs (WITH clauses)
- DISTINCT
- LIMIT / OFFSET
- Multiple window functions per query

**Impact**: Misses bugs in query optimizer for complex queries

### 8.2 Schema Rigidity
**Fixed**:
- Always 3-7 columns
- Always includes dept, salary, age
- Limited type diversity (no BLOB, no complex types)

**Impact**: Misses bugs related to schema complexity

### 8.3 Window Function Coverage
**Missing**:
- NTILE(), LAG(), LEAD()
- FIRST_VALUE(), LAST_VALUE(), NTH_VALUE()
- PERCENT_RANK(), CUME_DIST()

**Impact**: Function-specific bugs not detected

### 8.4 Partition Diversity
**Fixed**:
- Always single-column PARTITION BY
- Always TEXT partition key
- Always uses 'dept' column

**NOT Implemented**:
- Multi-column PARTITION BY
- Expression-based PARTITION BY
- INTEGER/REAL partition keys

**Impact**: Misses multi-column partition bugs

### 8.5 Data Simplicity
**Limitations**:
- Small tables (3-8 rows)
- Simple values (no complex strings, no BLOBs)
- Limited edge cases (10-15% rate)

**Impact**: Misses large-data bugs, complex value bugs

---

## 9. Non-Goals

### 9.1 Explicitly NOT Tested
- Transactions (BEGIN, COMMIT, ROLLBACK)
- Triggers
- Views with window functions
- Indexes on window function columns
- PRAGMA settings
- Concurrent execution
- Non-deterministic functions (RANDOM(), CURRENT_TIMESTAMP)
- User-defined functions (UDFs)

### 9.2 Explicitly NOT Supported
- Non-SQLite DBMS (PostgreSQL, MySQL, etc.)
- SQLite versions < 3.25.0 (no window functions)
- Window functions without PARTITION BY
- Aggregate functions without OVER clause

---

## 10. Heuristics and Randomness

### 10.1 Probability Distributions

**Window function type**:
- 98% aggregate functions
- 2% ranking functions
- Reason: Aggregate functions allow identity mutations

**Identity mutations**:
- 98% application rate
- Skipped if no argument to mutate
- Reason: High diversity, proven bug-finding

**CASE WHEN mutations**:
- 100% application rate
- Weighted: 30%, 25%, 20%, 15%, 10%
- Reason: Maximum diversity, all strategies proven effective

**Window spec mutations**:
- ~90% application rate (try 5 times)
- 10 strategies with equal probability
- Reason: Increase diversity without breaking constraints

**Frame generation**:
- 50% chance for aggregate functions
- 0% for ranking functions
- Reason: Balance between simple and complex queries

**ORDER BY columns**:
- 1-3 columns (weighted toward 2)
- Always includes direction (ASC/DESC)
- Always includes NULLS handling
- Reason: Deterministic ordering

### 10.2 Pruning Strategies

**Expected errors**:
- "misuse of aggregate"
- "misuse of window function"
- "second argument to nth_value must be a positive integer"
- "no such table"
- All SQLite3Errors.addExpectedExpressionErrors()
- All SQLite3Errors.addQueryErrors()

**Action**: Throw `IgnoreMeException` to skip test case

**Empty tables**:
- Skip if t1 or t2 has 0 rows
- Reason: MRUP relation undefined for empty tables

**Invalid schema**:
- Skip if 'dept' column not found
- Skip if no orderable columns
- Reason: Cannot generate valid window spec

---

## 11. Implementation Files

### 11.1 Core Oracle
- `SQLite3MRUPOracle.java` (1126 lines)
  - Main oracle logic
  - Query generation
  - Result comparison
  - Constraint enforcement

### 11.2 Table Generation
- `SQLite3MRUPTablePairGenerator.java` (537 lines)
  - Schema generation
  - Disjoint partition data
  - Validation

### 11.3 Mutations
- `SQLite3MRUPIdentityMutator.java` (~320 lines)
  - Identity transformations
  - Argument parsing
- `SQLite3MRUPCaseMutator.java` (~220 lines)
  - 5 CASE WHEN strategies
  - Partition-local conditions
- `SQLite3MRUPMutationOperator.java`
  - Window spec mutations
  - 10 mutation strategies

### 11.4 Utilities
- `SQLite3MRUPTestCaseLogger.java`
  - File-based logging
  - Reproduction scripts
- `SQLite3MRUPBugReproducer.java`
  - Bug report generation
  - Minimal test cases

---

## 12. Validation and Testing

### 12.1 Constraint Verification
**Method**: `verifyConstraints(functionType, windowSpec)`  
**Checks**:
- C0: PARTITION BY present
- C1: Only 'dept' in PARTITION BY
- C2: Only salary/age in ORDER BY
- C3: No frame for ranking functions
- C4: RANGE only with single ORDER BY
- C5: Only deterministic functions

**Result**: Map<String, Boolean> logged to file

### 12.2 Disjoint Partition Validation
**Method**: `validateDisjointPartitions(t1, t2, schema)`  
**Check**: PARTITION_VALUES(t1) ∩ PARTITION_VALUES(t2) = ∅  
**Action**: Throw SQLException if overlap detected

### 12.3 Logging
**Location**: `mrup_logs/mrup_YYYYMMDD_HHMMSS_mmm.log`  
**Contents**:
- Table schemas and data
- Partition values
- Generated queries
- Mutation pipeline
- Constraint verification
- Comparison results
- Bug details (if found)

---

## 13. Summary

### 13.1 What MRUP Oracle Does
✅ Tests window functions with PARTITION BY  
✅ Uses metamorphic testing (no ground truth needed)  
✅ Enforces 6 strict constraints (C0-C5)  
✅ Applies 3 types of mutations (100% rate)  
✅ Uses 3-layer result comparator  
✅ Generates disjoint partition data  
✅ Logs all test cases to files  
✅ Creates bug reproduction scripts  

### 13.2 What MRUP Oracle Does NOT Do
❌ Test WHERE clauses  
❌ Test JOINs  
❌ Test GROUP BY / HAVING  
❌ Test subqueries or CTEs  
❌ Test window functions without PARTITION BY  
❌ Test multi-column PARTITION BY  
❌ Test LAG/LEAD/NTILE/FIRST_VALUE/LAST_VALUE  
❌ Test large tables (>8 rows)  
❌ Test complex schemas (>7 columns)  
❌ Test non-SQLite DBMS  

### 13.3 Key Strengths
1. **Sound**: Zero false positives (when comparator is correct)
2. **Specialized**: Exploits partition locality unique to window functions
3. **Evidence-based**: Mutations based on real MySQL/PostgreSQL bugs
4. **Reproducible**: Every test case logged with reproduction script

### 13.4 Key Weaknesses
1. **Limited query complexity**: No WHERE, JOIN, GROUP BY, subqueries
2. **Limited schema diversity**: Fixed structure, limited types
3. **Limited function coverage**: Only 8 of 15+ window functions
4. **Limited data diversity**: Small tables, simple values

---

**End of Specification**

This document reflects the ACTUAL implementation as of the current codebase.  
Any feature not listed here is NOT implemented.

