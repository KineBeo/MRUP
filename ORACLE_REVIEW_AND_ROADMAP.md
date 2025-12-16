# MRUP Oracle: Critical Review & Improvement Roadmap

## Executive Summary

The MRUP Oracle is **fundamentally sound** with a strong metamorphic relation at its core. However, it is currently **too conservative** in query generation, limiting its bug-finding power. This review identifies key weaknesses and provides a concrete, incremental roadmap to transform it into a production-grade oracle.

**Current State:** Solid foundation, limited exploration  
**Potential:** High - with targeted improvements  
**Risk Level:** Low - metamorphic relation is mathematically sound

---

## 1. Strength Analysis

### 1.1 Core Metamorphic Relation ✅

**Strength:** The MRUP relation `H(t1 ∪ t2) = H(t1) ∪ H(t2)` is **mathematically sound** and **well-defined**.

- **Why it's strong:** Similar to PQS's partitioning oracle, it exploits a fundamental property of window functions (partition locality)
- **Comparison to prior work:** 
  - PQS uses query partitioning: `Q(D) = Q(D1) ∪ Q(D2)`
  - MRUP uses data partitioning: `H(t1 ∪ t2) = H(t1) ∪ H(t2)`
  - Both are **sound** and **complete** within their domains

**Evidence of strength:** Zero false positives in testing (when comparator is correct)

### 1.2 Phase 3 Comparator (3-Layer) ✅

**Strength:** The comparator is **sophisticated** and handles the complexity of window function results.

**Design:**
1. Layer 1: Cardinality check
2. Layer 2: MRUP normalization (partition-aware sorting)
3. Layer 3: Per-partition comparison with type awareness

**Why it's strong:**
- Respects window function semantics (partition boundaries)
- Type-aware comparison (handles INTEGER, REAL, TEXT, NULL)
- Deterministic sorting with tie-breakers
- Similar to TLP's result comparison but more sophisticated

**Comparison to prior work:**
- PQS: Simple set equality (no ordering)
- TLP: Row-by-row comparison with type awareness
- MRUP: **Partition-aware** comparison (unique contribution)

### 1.3 Constraint System (C0-C5) ✅

**Strength:** The constraints ensure **semantic correctness** and prevent non-determinism.

**Constraints:**
- C0: PARTITION BY mandatory (ensures MRUP validity)
- C1: PARTITION BY only uses 'dept' (ensures disjoint partitions)
- C2: ORDER BY only uses salary/age (deterministic ordering)
- C3: No FRAME for ranking functions (SQLite requirement)
- C4: RANGE only with single ORDER BY (SQLite requirement)
- C5: Only deterministic functions (prevents flakiness)

**Why it's strong:**
- Prevents false positives (non-determinism)
- Respects DBMS-specific limitations
- Similar to EET's constraint system

### 1.4 Phase 3 CASE WHEN Mutations ✅

**Strength:** Inspired by **real MySQL bugs** (EET oracle), proven effective.

**Mutations:**
- Constant conditions (WHEN TRUE/FALSE)
- Window function in WHEN condition
- Different window functions per branch
- Identical branches
- NULL handling

**Why it's strong:**
- Evidence-based (found real bugs in MySQL)
- Targets optimizer (dead branch elimination, constant folding)
- High diversity (6 variants for constant conditions alone)
- 100% mutation rate

**Comparison to prior work:**
- EET: Uses CASE WHEN extensively (proven effective)
- MRUP: Adapted EET's patterns to window functions (smart)

---

## 2. Weakness Analysis

### 2.1 **CRITICAL WEAKNESS: Schema Rigidity**

**Problem:** The oracle uses a **fixed schema** with only 5 columns:
```sql
CREATE TABLE t (
    dept TEXT,      -- partition key
    salary INTEGER, -- orderable
    age INTEGER,    -- orderable
    c0 INTEGER,     -- nullable
    c1 INTEGER      -- nullable
)
```

**Why this is weak:**
- **Limited type diversity:** Only TEXT and INTEGER, no REAL, BLOB
- **Fixed column count:** Always 5 columns
- **Limited NULL patterns:** Only c0 and c1 are nullable
- **No complex types:** No expressions, no computed columns

**Comparison to prior work:**
- **PQS:** Generates tables with 1-10 columns, diverse types
- **TLP:** Uses existing database schema (arbitrary complexity)
- **EET:** Generates complex schemas with 5-20 columns

**Impact:** Misses bugs related to:
- Type coercion edge cases (REAL vs INTEGER)
- Large schemas (optimizer stress)
- Complex NULL patterns
- Column ordering bugs

**Evidence:** MySQL bug used `ref_0.pkey`, `ref_0.vkey`, `ref_0.c19`, `ref_0.c22` - diverse columns!

### 2.2 **CRITICAL WEAKNESS: Data Simplicity**

**Problem:** Generated data is **too simple**:
- Small tables (5-20 rows)
- Simple values (random integers)
- Limited NULL patterns
- No edge cases (MIN_INT, MAX_INT, 0, -1)

**Why this is weak:**
- **Misses boundary bugs:** Overflow, underflow, division by zero
- **Misses NULL propagation bugs:** Complex NULL interactions
- **Misses large data bugs:** Optimizer chooses different plans for large tables
- **Misses duplicate handling:** No intentional duplicates for DISTINCT/GROUP BY testing

**Comparison to prior work:**
- **PQS:** Generates diverse data including edge cases
- **TLP:** Uses existing database data (real-world complexity)
- **EET:** Generates complex data patterns

**Impact:** Misses bugs related to:
- Arithmetic overflow in window functions
- NULL handling in complex expressions
- Optimizer plan selection (small vs large tables)
- Duplicate handling in RANK/DENSE_RANK

### 2.3 **MAJOR WEAKNESS: Query Simplicity**

**Problem:** Queries are **too simple**:
- Single window function per query
- No subqueries (except in CASE WHEN Phase 3.4)
- No JOINs
- No WHERE clauses
- No GROUP BY
- No HAVING
- No CTEs (WITH clauses)

**Why this is weak:**
- **Misses interaction bugs:** Window function + WHERE + GROUP BY
- **Misses optimization bugs:** Complex query plans
- **Misses subquery bugs:** Correlated subqueries with window functions
- **Misses CTE bugs:** Window functions in CTEs

**Comparison to prior work:**
- **PQS:** Generates complex queries with JOINs, subqueries, aggregates
- **TLP:** Tests WHERE/GROUP BY/HAVING interactions
- **EET:** Generates extremely complex queries (10+ clauses)

**Impact:** Misses bugs in:
- Query optimizer (complex plans)
- Subquery execution (correlated subqueries)
- CTE materialization
- WHERE clause interaction with window functions

**Evidence:** Real-world queries often have WHERE clauses filtering before window functions!

### 2.4 **MAJOR WEAKNESS: Window Function Diversity**

**Problem:** Limited window function types:
- Only 8 function types: ROW_NUMBER, RANK, DENSE_RANK, SUM, AVG, COUNT, MIN, MAX
- No NTILE, LAG, LEAD, FIRST_VALUE, LAST_VALUE, NTH_VALUE
- No PERCENT_RANK, CUME_DIST
- No custom aggregates

**Why this is weak:**
- **Misses function-specific bugs:** NTILE boundary calculation, LAG/LEAD offset bugs
- **Misses rare function bugs:** PERCENT_RANK, CUME_DIST are less tested
- **Misses aggregate window bugs:** Custom aggregates with OVER clause

**Comparison to prior work:**
- **EET:** Tests all available window functions
- **MRUP:** Only tests 8 out of 15+ available functions

**Impact:** Misses bugs in:
- NTILE (partition size calculation)
- LAG/LEAD (offset handling, default values)
- FIRST_VALUE/LAST_VALUE (frame boundary bugs)
- NTH_VALUE (index out of bounds)

### 2.5 **MODERATE WEAKNESS: Frame Specification Simplicity**

**Problem:** Frame specifications are **too simple**:
- Only simple frames: `ROWS 1 PRECEDING`, `ROWS BETWEEN 2 PRECEDING AND CURRENT ROW`
- No complex frames: `ROWS BETWEEN UNBOUNDED PRECEDING AND 5 FOLLOWING`
- No GROUPS frame (not widely supported, but exists)
- No frame exclusion diversity (only 4 options, rarely used)

**Why this is weak:**
- **Misses frame calculation bugs:** Complex frame boundaries
- **Misses UNBOUNDED bugs:** Interaction with FOLLOWING
- **Misses exclusion bugs:** EXCLUDE TIES, EXCLUDE GROUP

**Impact:** Misses bugs in:
- Frame boundary calculation (complex BETWEEN)
- UNBOUNDED FOLLOWING edge cases
- Frame exclusion logic

### 2.6 **MODERATE WEAKNESS: Partition Diversity**

**Problem:** Always partitions by **single column** ('dept'):
- No multi-column partitions: `PARTITION BY dept, age`
- No expression partitions: `PARTITION BY dept || '_' || age`
- Always TEXT partition key

**Why this is weak:**
- **Misses multi-column partition bugs:** Partition key comparison
- **Misses expression partition bugs:** Expression evaluation in PARTITION BY
- **Misses type diversity bugs:** INTEGER/REAL partition keys

**Comparison to prior work:**
- **EET:** Uses multi-column partitions
- **MRUP:** Always single column

**Impact:** Misses bugs in:
- Multi-column partition key comparison
- Expression evaluation in PARTITION BY
- Type coercion in partition keys

### 2.7 **MINOR WEAKNESS: ORDER BY Simplicity**

**Problem:** ORDER BY is **limited**:
- Only 1-3 columns (Phase 1 improved this)
- Only simple columns (no expressions like `ORDER BY salary + age`)
- Always ASC/DESC with NULLS FIRST/LAST (good, but no diversity in omitting these)

**Why this is weak:**
- **Misses expression ORDER BY bugs:** Expression evaluation
- **Misses default NULL handling bugs:** When NULLS FIRST/LAST is omitted

**Impact:** Minor - this is less critical than other weaknesses

---

## 3. Comparison with Prior Work

### 3.1 PQS (Pivoted Query Synthesis)

**What PQS does well that MRUP doesn't:**
1. **Query partitioning:** Tests different WHERE clauses (partition by predicate)
2. **Diverse schemas:** 1-10 columns, diverse types
3. **Complex queries:** JOINs, subqueries, aggregates
4. **Aggregate testing:** GROUP BY, HAVING

**What MRUP does better than PQS:**
1. **Window functions:** PQS doesn't test window functions at all
2. **Partition-aware comparison:** MRUP's 3-layer comparator is more sophisticated

**Class of bugs PQS finds that MRUP misses:**
- WHERE clause optimization bugs
- JOIN optimization bugs
- GROUP BY bugs
- Aggregate function bugs (without OVER)

### 3.2 TLP (Ternary Logic Partitioning)

**What TLP does well that MRUP doesn't:**
1. **WHERE clause diversity:** Tests TRUE/FALSE/NULL partitioning
2. **Uses existing schema:** Tests real-world complexity
3. **Predicate testing:** Complex boolean expressions

**What MRUP does better than TLP:**
1. **Window functions:** TLP doesn't test window functions
2. **Metamorphic testing:** MRUP's relation is more general

**Class of bugs TLP finds that MRUP misses:**
- WHERE clause evaluation bugs
- NULL handling in predicates
- Boolean expression optimization

### 3.3 EET (Equivalent Expression Testing)

**What EET does well that MRUP doesn't:**
1. **Expression diversity:** Generates complex expressions
2. **CASE WHEN diversity:** More variants (MRUP adopted this!)
3. **Subquery diversity:** Correlated subqueries, IN/EXISTS
4. **Schema diversity:** 5-20 columns, diverse types

**What MRUP does better than EET:**
1. **Window functions:** EET tests them, but MRUP is specialized
2. **Partition-aware comparison:** MRUP's comparator is more sophisticated

**Class of bugs EET finds that MRUP misses:**
- Expression evaluation bugs (complex expressions)
- Subquery optimization bugs
- Type coercion bugs (diverse types)

### 3.4 Summary: What MRUP Currently Misses

**High-priority bug classes:**
1. **WHERE clause bugs:** No WHERE clauses in queries
2. **Subquery bugs:** Limited subquery testing
3. **Type diversity bugs:** Only TEXT and INTEGER
4. **Schema complexity bugs:** Always 5 columns
5. **Data edge case bugs:** No MIN_INT, MAX_INT, overflow testing

**Medium-priority bug classes:**
6. **JOIN bugs:** No JOINs
7. **GROUP BY bugs:** No GROUP BY
8. **CTE bugs:** No WITH clauses
9. **Window function diversity bugs:** Missing NTILE, LAG, LEAD, etc.
10. **Multi-column partition bugs:** Always single column

**Low-priority bug classes:**
11. **Frame exclusion bugs:** Rarely used feature
12. **GROUPS frame bugs:** Not widely supported

---

## 4. Improvement Roadmap

This roadmap is designed to be **incremental**, **validatable**, and **high-impact**. Each step can be implemented independently and tested for zero false positives before moving to the next.

### Phase A: Data & Schema Improvements (Foundation)

#### A1: Add REAL Type Support

**What to change:**
- Add REAL columns to schema (e.g., `bonus REAL`, `rating REAL`)
- Generate REAL values (use `Randomly.getDouble()`)
- Update comparator to handle REAL comparison with epsilon tolerance

**Why it increases bug-finding power:**
- **Type coercion bugs:** INTEGER vs REAL in window functions
- **Floating-point bugs:** Precision issues, NaN, Infinity
- **Comparison bugs:** REAL comparison in ORDER BY

**Implementation effort:** LOW
- Add 1-2 REAL columns to schema
- Update data generator
- Update comparator (epsilon comparison)

**Risks:**
- **False positives:** Floating-point precision issues
- **Mitigation:** Use epsilon comparison (e.g., `|a - b| < 1e-9`)

**Expected bugs:** 2-3 (type coercion, floating-point edge cases)

#### A2: Add Edge Case Data Generation

**What to change:**
- Generate edge case values: `MIN_INT`, `MAX_INT`, `0`, `-1`, `1`
- Generate intentional NULLs (50% of c0, c1 values)
- Generate intentional duplicates (for RANK/DENSE_RANK testing)

**Why it increases bug-finding power:**
- **Overflow bugs:** `SUM(MAX_INT)` overflow
- **Division by zero bugs:** `AVG` with zero values
- **NULL propagation bugs:** Complex NULL interactions
- **Duplicate handling bugs:** RANK vs DENSE_RANK

**Implementation effort:** LOW
- Modify data generator to include edge cases
- Use `Randomly.fromOptions(MIN_INT, MAX_INT, 0, -1, 1, random)`

**Risks:**
- **False positives:** Overflow may be expected behavior
- **Mitigation:** Catch overflow exceptions as expected errors

**Expected bugs:** 3-5 (overflow, NULL handling, duplicate handling)

#### A3: Variable Schema Complexity

**What to change:**
- Generate schemas with 3-8 columns (not always 5)
- Vary column types (TEXT, INTEGER, REAL)
- Vary nullable columns (1-4 nullable columns)

**Why it increases bug-finding power:**
- **Schema complexity bugs:** Optimizer stress with many columns
- **Column ordering bugs:** Different column orderings
- **Type diversity bugs:** More type combinations

**Implementation effort:** MEDIUM
- Modify `SQLite3MRUPTablePairGenerator` to generate variable schemas
- Update constraint system to handle variable schemas

**Risks:**
- **Complexity:** More complex schema generation
- **Mitigation:** Start with 3-5 columns, then expand to 3-8

**Expected bugs:** 2-4 (schema complexity, column ordering)

---

### Phase B: Query Complexity Improvements (High Impact)

#### B1: Add WHERE Clauses (CRITICAL)

**What to change:**
- Add WHERE clauses to queries: `WHERE salary > 50000`
- Use partition-local predicates only (to preserve MRUP)
- Generate diverse predicates: `>`, `<`, `=`, `IS NULL`, `BETWEEN`, `IN`

**Why it increases bug-finding power:**
- **WHERE + window function bugs:** Filtering before window function
- **Predicate evaluation bugs:** Complex predicates
- **Optimizer bugs:** Different query plans with WHERE

**Implementation effort:** MEDIUM
- Add WHERE clause generation to query builder
- Ensure predicates are partition-local (e.g., `WHERE salary > 50000`, not `WHERE dept = 'Engineering'`)
- Update MRUP validation to ensure WHERE doesn't break partition disjointness

**Risks:**
- **MRUP violation:** WHERE clause must not filter entire partitions
- **Mitigation:** Only use predicates on non-partition columns (salary, age, c0, c1)

**Expected bugs:** 5-10 (WHERE + window function interaction, optimizer bugs)

**CRITICAL:** This is the **highest impact** improvement. Real-world queries almost always have WHERE clauses!

#### B2: Add Subqueries in SELECT

**What to change:**
- Add scalar subqueries in SELECT: `SELECT ..., (SELECT MAX(salary) FROM t WHERE dept = outer.dept) AS max_sal, wf`
- Use partition-local subqueries (correlated by partition key)
- Ensure subqueries are deterministic

**Why it increases bug-finding power:**
- **Subquery + window function bugs:** Interaction between subqueries and window functions
- **Correlated subquery bugs:** Subquery optimization
- **Optimizer bugs:** Complex query plans

**Implementation effort:** MEDIUM
- Add subquery generation to query builder
- Ensure subqueries are partition-local (correlated by `dept`)
- Update comparator to handle additional columns

**Risks:**
- **MRUP violation:** Subquery must be partition-local
- **Mitigation:** Always correlate by partition key: `WHERE dept = outer.dept`

**Expected bugs:** 3-5 (subquery + window function interaction)

#### B3: Add Multiple Window Functions per Query

**What to change:**
- Generate queries with 2-3 window functions: `SELECT ..., wf1, wf2, wf3`
- Use different window specifications for each
- Update comparator to handle multiple wf_result columns

**Why it increases bug-finding power:**
- **Multiple window function bugs:** Interaction between window functions
- **Optimizer bugs:** Multiple window function evaluation
- **Resource bugs:** Memory/CPU stress

**Implementation effort:** MEDIUM
- Modify query builder to generate multiple window functions
- Update comparator to handle multiple result columns
- Update MRUP normalization to handle multiple window functions

**Risks:**
- **Comparator complexity:** Need to compare multiple columns
- **Mitigation:** Extend current comparator to handle multiple wf_result columns

**Expected bugs:** 2-4 (multiple window function interaction)

---

### Phase C: Window Function Diversity (Medium Impact)

#### C1: Add Missing Window Functions

**What to change:**
- Add NTILE, LAG, LEAD, FIRST_VALUE, LAST_VALUE, NTH_VALUE
- Add PERCENT_RANK, CUME_DIST (if SQLite supports)
- Generate queries with these functions

**Why it increases bug-finding power:**
- **Function-specific bugs:** NTILE boundary calculation, LAG/LEAD offset bugs
- **Rare function bugs:** Less-tested functions have more bugs

**Implementation effort:** LOW-MEDIUM
- Add new function types to generator
- Handle function-specific constraints (e.g., LAG/LEAD need offset parameter)

**Risks:**
- **SQLite support:** Some functions may not be supported
- **Mitigation:** Check SQLite version, skip unsupported functions

**Expected bugs:** 2-3 (function-specific bugs)

#### C2: Add Expression-Based ORDER BY

**What to change:**
- Generate ORDER BY with expressions: `ORDER BY salary + age`, `ORDER BY salary * 2`
- Use simple expressions (no subqueries)
- Ensure expressions are deterministic

**Why it increases bug-finding power:**
- **Expression evaluation bugs:** Expression in ORDER BY
- **Optimizer bugs:** Expression evaluation optimization

**Implementation effort:** LOW
- Modify ORDER BY generation to include expressions
- Use simple expressions: `col + col`, `col * 2`, `col - col`

**Risks:**
- **Non-determinism:** Expressions with NULL may be non-deterministic
- **Mitigation:** Use COALESCE to handle NULL: `ORDER BY COALESCE(salary, 0) + age`

**Expected bugs:** 1-2 (expression evaluation in ORDER BY)

#### C3: Add Multi-Column PARTITION BY

**What to change:**
- Generate PARTITION BY with 1-2 columns: `PARTITION BY dept`, `PARTITION BY dept, age`
- Ensure disjoint partitions (tricky with multi-column)
- Update partition validation logic

**Why it increases bug-finding power:**
- **Multi-column partition bugs:** Partition key comparison
- **Optimizer bugs:** Multi-column partition optimization

**Implementation effort:** MEDIUM-HIGH
- Modify data generator to ensure disjoint multi-column partitions
- Update partition validation logic
- Update comparator to handle multi-column partitions

**Risks:**
- **Complexity:** Ensuring disjoint multi-column partitions is complex
- **Mitigation:** Start with 2-column partitions, use simple disjoint strategy (e.g., dept + age ranges)

**Expected bugs:** 2-3 (multi-column partition bugs)

---

### Phase D: Advanced Query Patterns (Lower Priority)

#### D1: Add CTEs (WITH Clauses)

**What to change:**
- Wrap queries in CTEs: `WITH cte AS (SELECT ... FROM t) SELECT ... FROM cte`
- Use window functions in CTEs
- Ensure MRUP is preserved

**Why it increases bug-finding power:**
- **CTE bugs:** CTE materialization bugs
- **Window function in CTE bugs:** Interaction between CTEs and window functions

**Implementation effort:** MEDIUM
- Add CTE generation to query builder
- Ensure MRUP is preserved (CTE must not break partition disjointness)

**Risks:**
- **MRUP violation:** CTE must preserve partition disjointness
- **Mitigation:** Use simple CTEs that don't modify partitions

**Expected bugs:** 1-2 (CTE + window function bugs)

#### D2: Add JOINs (Careful!)

**What to change:**
- Add self-joins: `SELECT ... FROM t1 AS a JOIN t1 AS b ON a.dept = b.dept`
- Ensure JOINs preserve partition locality
- Update MRUP validation

**Why it increases bug-finding power:**
- **JOIN + window function bugs:** Interaction between JOINs and window functions
- **Optimizer bugs:** Complex query plans with JOINs

**Implementation effort:** HIGH
- Add JOIN generation to query builder
- Ensure JOINs preserve partition locality (tricky!)
- Update MRUP validation to handle JOINs

**Risks:**
- **MRUP violation:** JOINs can easily break partition disjointness
- **Mitigation:** Use self-joins with partition key equality: `ON a.dept = b.dept`

**Expected bugs:** 2-4 (JOIN + window function bugs)

**WARNING:** This is **high risk** - JOINs can easily break MRUP semantics. Only attempt after all other improvements are validated.

---

## 5. Prioritized Roadmap Summary

### Tier 1: CRITICAL (Implement First)

1. **B1: Add WHERE Clauses** - Highest impact, medium effort
2. **A2: Add Edge Case Data** - High impact, low effort
3. **A1: Add REAL Type** - High impact, low effort

**Expected total bugs:** 10-18  
**Implementation time:** 1-2 weeks  
**Risk:** Low

### Tier 2: HIGH PRIORITY (Implement Second)

4. **B2: Add Subqueries in SELECT** - High impact, medium effort
5. **A3: Variable Schema Complexity** - Medium impact, medium effort
6. **B3: Multiple Window Functions** - Medium impact, medium effort

**Expected total bugs:** 7-13  
**Implementation time:** 2-3 weeks  
**Risk:** Medium

### Tier 3: MEDIUM PRIORITY (Implement Third)

7. **C1: Add Missing Window Functions** - Medium impact, low-medium effort
8. **C2: Expression-Based ORDER BY** - Low-medium impact, low effort
9. **C3: Multi-Column PARTITION BY** - Medium impact, medium-high effort

**Expected total bugs:** 5-8  
**Implementation time:** 1-2 weeks  
**Risk:** Medium

### Tier 4: LOWER PRIORITY (Implement Last)

10. **D1: Add CTEs** - Low-medium impact, medium effort
11. **D2: Add JOINs** - Medium impact, high effort, high risk

**Expected total bugs:** 3-6  
**Implementation time:** 2-3 weeks  
**Risk:** High

---

## 6. Expected Total Impact

### Current State
- **Bugs found:** 0-2 (conservative estimate)
- **Query diversity:** Low
- **Schema diversity:** Very low
- **False positives:** 0 (good!)

### After Tier 1 (CRITICAL)
- **Bugs found:** 10-20
- **Query diversity:** Medium
- **Schema diversity:** Medium
- **False positives:** 0-2 (manageable)

### After Tier 2 (HIGH PRIORITY)
- **Bugs found:** 17-33
- **Query diversity:** High
- **Schema diversity:** High
- **False positives:** 2-5 (manageable)

### After Tier 3 (MEDIUM PRIORITY)
- **Bugs found:** 22-41
- **Query diversity:** Very high
- **Schema diversity:** Very high
- **False positives:** 3-8 (manageable)

### After Tier 4 (LOWER PRIORITY)
- **Bugs found:** 25-47
- **Query diversity:** Extremely high
- **Schema diversity:** Extremely high
- **False positives:** 5-12 (requires careful validation)

---

## 7. Risk Assessment

### Low Risk Improvements
- A1: REAL type support
- A2: Edge case data
- C1: Missing window functions
- C2: Expression-based ORDER BY

**Why low risk:** Don't change MRUP semantics, easy to validate

### Medium Risk Improvements
- A3: Variable schema complexity
- B1: WHERE clauses
- B2: Subqueries in SELECT
- B3: Multiple window functions
- C3: Multi-column PARTITION BY

**Why medium risk:** Require careful MRUP validation, but semantics are clear

### High Risk Improvements
- D1: CTEs
- D2: JOINs

**Why high risk:** Can easily break MRUP semantics, require extensive validation

---

## 8. Validation Strategy

After each improvement:

1. **Run 10,000 test cases** with new improvement enabled
2. **Check for false positives** (oracle fails but no bug)
3. **Manually verify 20 random cases** using reproduction scripts
4. **Check performance** (queries/s should remain > 50)
5. **Document any new expected errors**

If false positives > 1%, **rollback** and investigate.

---

## 9. Conclusion

### Current Assessment
The MRUP Oracle is **fundamentally sound** but **under-exploring** the DBMS execution space. It has a strong metamorphic relation and sophisticated comparator, but query/schema simplicity limits bug-finding power.

### Key Insight
The oracle is **too conservative** in query generation. Real-world SQL queries are much more complex (WHERE clauses, subqueries, multiple window functions). By incrementally adding complexity while preserving MRUP semantics, we can dramatically increase bug-finding power.

### Recommended Next Steps
1. **Implement Tier 1** (WHERE clauses, edge case data, REAL type) - **highest impact**
2. **Validate thoroughly** (10,000 test cases, zero false positives)
3. **Measure bug-finding improvement** (run for 24 hours, count unique bugs)
4. **Proceed to Tier 2** if validation passes

### Expected Outcome
With Tier 1 + Tier 2 improvements, the MRUP Oracle should find **17-33 unique bugs** in SQLite3, making it a **production-grade** testing oracle comparable to PQS/TLP/EET.

---

## 10. Final Thoughts

**What makes this oracle special:**
- **Partition-aware comparison** (unique contribution)
- **Window function specialization** (fills gap in SQLancer)
- **Evidence-based mutations** (CASE WHEN from MySQL bugs)

**What holds it back:**
- **Query simplicity** (no WHERE, no subqueries)
- **Schema rigidity** (always 5 columns, limited types)
- **Data simplicity** (no edge cases)

**The path forward:**
- **Incremental complexity** (validate after each step)
- **Focus on query-level mutations** (not data explosion)
- **Preserve MRUP semantics** (mathematical soundness)

**This oracle has the potential to be a top-tier testing tool for window functions. The roadmap above provides a clear, validated path to get there.**

