# MRUP Oracle: Query Mutation Roadmap

## Executive Summary

This document presents a **4-phase incremental mutation plan** to increase query complexity while maintaining MRUP semantic correctness. Each phase builds on the previous one, with clear validation gates and rollback points.

**Current State**: Oracle works correctly with zero false positives, but queries are too simple to expose deep DBMS bugs.

**Goal**: Systematically increase query complexity to expose bugs in:
- Window function edge cases
- Type coercion and casting
- NULL handling in complex expressions
- Subquery interaction with window functions
- Optimizer corner cases

---

## Mutation Philosophy

### Core Principles

1. **MRUP Preservation**: Every mutation must preserve the metamorphic relation `H(t1 ∪ t2) = H(t1) ∪ H(t2)`
2. **Determinism**: No mutations that introduce non-deterministic behavior
3. **Incrementality**: Each phase adds one mutation type at a time
4. **Testability**: Each mutation can be toggled on/off independently
5. **Debuggability**: Clear logging to identify which mutation caused a failure

### What Breaks MRUP?

**UNSAFE mutations** (avoid these):
- Non-deterministic functions (RANDOM(), CURRENT_TIMESTAMP)
- Mutations that reference partition values from other partitions
- Correlated subqueries that cross partition boundaries
- Window functions without PARTITION BY
- Aggregates that span multiple partitions

**SAFE mutations** (our focus):
- Deterministic transformations of window function results
- Partition-local operations
- Type conversions
- Arithmetic on window function outputs
- Filtering/conditions based on partition-local data

---

## Phase 1: Simple OVER() Clause Mutations

### Goals
- Add complexity to the OVER() clause itself
- Test DBMS handling of edge cases in window specifications
- Minimal code changes (all in window function generator)

### Mutation Types

#### M1.1: Multiple ORDER BY Columns with Mixed Directions
**Current**: `ORDER BY salary DESC`  
**Mutated**: `ORDER BY salary DESC, age ASC, c0 DESC NULLS FIRST`

**Why Safe**: 
- Still deterministic ordering within partition
- MRUP normalization already handles multiple ORDER BY columns
- No cross-partition interaction

**Example**:
```sql
SUM(salary) OVER (
    PARTITION BY dept 
    ORDER BY salary DESC, age ASC, c0 DESC NULLS FIRST
    ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
)
```

**Bug Potential**: 
- Incorrect NULL handling with mixed NULLS FIRST/LAST
- Optimizer bugs with complex sort keys
- Frame calculation errors with multiple sort columns

**Implementation**:
```java
// In SQLite3MRUPOracle.java - generateOrderByClause()
int numOrderBy = Randomly.getInteger(1, 3);  // Was: 1-2, now: 1-3
for (int i = 0; i < numOrderBy; i++) {
    SQLite3Column col = Randomly.fromList(orderableColumns);
    sb.append(col.getName()).append(" ");
    sb.append(Randomly.fromOptions("ASC", "DESC")).append(" ");
    sb.append(Randomly.fromOptions("NULLS FIRST", "NULLS LAST"));
    if (i < numOrderBy - 1) sb.append(", ");
}
```

#### M1.2: Complex Frame Specifications
**Current**: `ROWS 1 PRECEDING`  
**Mutated**: 
- `ROWS BETWEEN 3 PRECEDING AND 1 FOLLOWING`
- `ROWS BETWEEN UNBOUNDED PRECEDING AND 2 FOLLOWING`
- `RANGE BETWEEN 1000 PRECEDING AND 500 FOLLOWING`

**Why Safe**:
- Frame is evaluated within partition only
- MRUP semantics preserved (partition-local)
- Already constrained by C3, C4

**Example**:
```sql
AVG(salary) OVER (
    PARTITION BY dept 
    ORDER BY age ASC
    ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING
)
```

**Bug Potential**:
- Frame boundary calculation errors
- UNBOUNDED with FOLLOWING edge cases
- RANGE with non-integer ORDER BY columns

**Implementation**:
```java
// In SQLite3MRUPOracle.java - generateFrameClause()
String frameType = Randomly.fromOptions("ROWS", "RANGE");
String frameBound = Randomly.fromOptions(
    "UNBOUNDED PRECEDING",
    "3 PRECEDING",
    "CURRENT ROW",
    "1 FOLLOWING",
    "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW",
    "BETWEEN 2 PRECEDING AND 1 FOLLOWING",
    "BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING"
);
```

#### M1.3: FILTER Clause (SQLite 3.30+)
**Current**: `COUNT(salary)`  
**Mutated**: `COUNT(salary) FILTER (WHERE salary > 50000)`

**Why Safe**:
- Filter is evaluated within partition
- Deterministic condition
- No cross-partition references

**Example**:
```sql
COUNT(salary) FILTER (WHERE salary > 50000) OVER (
    PARTITION BY dept 
    ORDER BY age DESC
)
```

**Bug Potential**:
- FILTER + FRAME interaction bugs
- NULL handling in FILTER condition
- FILTER with ranking functions (should error)

**Implementation**:
```java
// In SQLite3MRUPOracle.java - after function generation
if (Randomly.getBooleanWithProbability(0.3) && !isRankingFunction) {
    sb.append(" FILTER (WHERE ");
    SQLite3Column col = Randomly.fromList(columns);
    sb.append(col.getName());
    sb.append(Randomly.fromOptions(" > ", " < ", " = ", " IS NOT "));
    sb.append(generateValue(col.getType()));
    sb.append(")");
}
```

### Validation Checklist

- [ ] Run 1000 test cases with each mutation enabled
- [ ] Zero false positives
- [ ] Verify MRUP normalization handles new ORDER BY patterns
- [ ] Check logs for any new constraint violations
- [ ] Manually verify 5 random cases with detailed analysis tool

### Code Impact
**Level**: LOW  
**Files**: `SQLite3MRUPOracle.java` (window function generator only)  
**Lines Changed**: ~50-100

### Expected Bug-Finding Impact
**Level**: MEDIUM  
- Frame calculation bugs
- Complex ORDER BY edge cases
- FILTER clause interaction bugs

---

## Phase 2: SELECT-Level Expression Wrapping

### Goals
- Add complexity around window function results
- Test type coercion and arithmetic
- Still partition-local, deterministic

### Mutation Types

#### M2.1: Arithmetic Operations on Window Function Results
**Current**: `SELECT ..., wf_result FROM ...`  
**Mutated**: `SELECT ..., wf_result * 2 + 100 AS wf_result FROM ...`

**Why Safe**:
- Operates on window function output (already computed)
- Deterministic arithmetic
- No cross-partition interaction
- MRUP still holds: `(H(t1) * 2 + 100) ∪ (H(t2) * 2 + 100) = (H(t_union) * 2 + 100)`

**Examples**:
```sql
-- Arithmetic
SELECT dept, salary, age, 
       (SUM(salary) OVER (...) * 1.1) AS wf_result
FROM t1

-- Multiple operations
SELECT dept, salary, age,
       (ROW_NUMBER() OVER (...) + age * 2) AS wf_result
FROM t1

-- Division (with NULL handling)
SELECT dept, salary, age,
       (AVG(salary) OVER (...) / NULLIF(age, 0)) AS wf_result
FROM t1
```

**Bug Potential**:
- Integer overflow in arithmetic
- Division by zero
- Type coercion bugs (INT → REAL)
- NULL propagation in expressions

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
String windowFunctionExpr = windowFunction;

if (Randomly.getBooleanWithProbability(0.4)) {
    // Wrap with arithmetic
    String operator = Randomly.fromOptions(" * ", " + ", " - ", " / ");
    String operand = String.valueOf(Randomly.getInteger(1, 100));
    windowFunctionExpr = "(" + windowFunctionExpr + operator + operand + ")";
}

query.append(windowFunctionExpr).append(" AS wf_result");
```

#### M2.2: Type Casting
**Current**: `SELECT ..., wf_result FROM ...`  
**Mutated**: `SELECT ..., CAST(wf_result AS REAL) AS wf_result FROM ...`

**Why Safe**:
- Deterministic type conversion
- Operates on computed result
- MRUP preserved: `CAST(H(t1), T) ∪ CAST(H(t2), T) = CAST(H(t_union), T)`

**Examples**:
```sql
-- Cast to REAL
SELECT dept, salary, age,
       CAST(COUNT(*) OVER (...) AS REAL) AS wf_result
FROM t1

-- Cast to TEXT
SELECT dept, salary, age,
       CAST(SUM(salary) OVER (...) AS TEXT) AS wf_result
FROM t1

-- Cast with arithmetic
SELECT dept, salary, age,
       CAST(AVG(salary) OVER (...) * 1.5 AS INTEGER) AS wf_result
FROM t1
```

**Bug Potential**:
- Type coercion bugs
- Precision loss (REAL → INT)
- NULL handling in CAST
- String representation of numbers

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.3)) {
    String targetType = Randomly.fromOptions("INTEGER", "REAL", "TEXT");
    windowFunctionExpr = "CAST(" + windowFunctionExpr + " AS " + targetType + ")";
}
```

#### M2.3: NULL-Handling Functions
**Current**: `SELECT ..., wf_result FROM ...`  
**Mutated**: `SELECT ..., COALESCE(wf_result, 0) AS wf_result FROM ...`

**Why Safe**:
- Deterministic NULL handling
- Operates on result only
- MRUP preserved

**Examples**:
```sql
-- COALESCE
SELECT dept, salary, age,
       COALESCE(AVG(c0) OVER (...), 0.0) AS wf_result
FROM t1

-- NULLIF
SELECT dept, salary, age,
       NULLIF(COUNT(*) OVER (...), 0) AS wf_result
FROM t1

-- IFNULL
SELECT dept, salary, age,
       IFNULL(SUM(salary) OVER (...), -1) AS wf_result
FROM t1
```

**Bug Potential**:
- NULL handling edge cases
- Type inference with COALESCE
- Interaction with window function NULL semantics

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.3)) {
    String nullFunc = Randomly.fromOptions("COALESCE", "IFNULL", "NULLIF");
    String defaultValue = generateDefaultValue();
    windowFunctionExpr = nullFunc + "(" + windowFunctionExpr + ", " + defaultValue + ")";
}
```

### Validation Checklist

- [ ] Run 2000 test cases with Phase 1 + Phase 2 mutations
- [ ] Zero false positives
- [ ] Verify arithmetic doesn't break comparison (type-aware comparison)
- [ ] Check for division by zero handling
- [ ] Test CAST with all supported types
- [ ] Manually verify 10 cases with complex expressions

### Code Impact
**Level**: LOW-MEDIUM  
**Files**: `SQLite3MRUPOracle.java` (query builder)  
**Lines Changed**: ~100-150

### Expected Bug-Finding Impact
**Level**: MEDIUM-HIGH  
- Type coercion bugs
- Arithmetic overflow/underflow
- NULL handling in expressions
- Optimizer bugs with complex expressions

---

## Phase 3: Controlled CASE WHEN Mutations

### Goals
- Add conditional logic to queries
- Test branching in window function context
- Maintain determinism and partition locality

### Mutation Types

#### M3.1: CASE on Window Function Result
**Current**: `SELECT ..., wf_result FROM ...`  
**Mutated**: 
```sql
SELECT ..., 
       CASE 
           WHEN wf_result > 100 THEN 1
           WHEN wf_result > 50 THEN 2
           ELSE 3
       END AS wf_result
FROM ...
```

**Why Safe**:
- Operates on computed window function result
- Deterministic branching
- No cross-partition references
- MRUP preserved: `CASE(H(t1)) ∪ CASE(H(t2)) = CASE(H(t_union))`

**Examples**:
```sql
-- Simple CASE
SELECT dept, salary, age,
       CASE 
           WHEN ROW_NUMBER() OVER (...) <= 3 THEN 'TOP'
           ELSE 'OTHER'
       END AS wf_result
FROM t1

-- CASE with NULL
SELECT dept, salary, age,
       CASE 
           WHEN AVG(salary) OVER (...) IS NULL THEN 0
           WHEN AVG(salary) OVER (...) > 60000 THEN 1
           ELSE 2
       END AS wf_result
FROM t1

-- Nested CASE
SELECT dept, salary, age,
       CASE 
           WHEN SUM(salary) OVER (...) > 100000 THEN
               CASE WHEN age > 40 THEN 'HIGH_SENIOR' ELSE 'HIGH_JUNIOR' END
           ELSE 'LOW'
       END AS wf_result
FROM t1
```

**Bug Potential**:
- CASE evaluation order bugs
- NULL handling in WHEN conditions
- Type inference across branches
- Optimizer bugs with complex CASE

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.3)) {
    StringBuilder caseExpr = new StringBuilder("CASE ");
    int numBranches = Randomly.getInteger(2, 4);
    
    for (int i = 0; i < numBranches; i++) {
        caseExpr.append("WHEN ").append(windowFunctionExpr);
        caseExpr.append(Randomly.fromOptions(" > ", " < ", " = ", " IS NOT NULL"));
        caseExpr.append(generateValue()).append(" THEN ");
        caseExpr.append(generateValue()).append(" ");
    }
    
    caseExpr.append("ELSE ").append(generateValue()).append(" END");
    windowFunctionExpr = caseExpr.toString();
}
```

#### M3.2: CASE on Partition-Local Columns
**Current**: `SELECT ..., wf_result FROM ...`  
**Mutated**:
```sql
SELECT ...,
       CASE 
           WHEN salary > 60000 THEN SUM(salary) OVER (...)
           ELSE AVG(salary) OVER (...)
       END AS wf_result
FROM ...
```

**Why Safe**:
- Condition uses partition-local column (salary)
- Both branches compute window functions with PARTITION BY dept
- Deterministic: same row always takes same branch
- MRUP preserved: partition-local decision

**Examples**:
```sql
-- Different window functions per branch
SELECT dept, salary, age,
       CASE 
           WHEN age > 40 THEN SUM(salary) OVER (PARTITION BY dept ORDER BY age)
           ELSE COUNT(*) OVER (PARTITION BY dept ORDER BY age)
       END AS wf_result
FROM t1

-- CASE with NULL column
SELECT dept, salary, age, c0,
       CASE 
           WHEN c0 IS NULL THEN 0
           ELSE RANK() OVER (PARTITION BY dept ORDER BY c0 DESC)
       END AS wf_result
FROM t1
```

**Bug Potential**:
- Window function evaluation in CASE branches
- Type mismatch between branches
- Optimizer confusion with multiple window functions
- Frame calculation in conditional context

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.2)) {
    SQLite3Column condCol = Randomly.fromList(columns);
    String wf1 = generateWindowFunction();  // Different window function
    String wf2 = generateWindowFunction();
    
    windowFunctionExpr = "CASE WHEN " + condCol.getName() + 
                        Randomly.fromOptions(" > ", " < ", " IS NOT NULL") +
                        generateValue(condCol.getType()) +
                        " THEN " + wf1 + " ELSE " + wf2 + " END";
}
```

#### M3.3: CASE with Multiple Column References
**Current**: Simple CASE  
**Mutated**:
```sql
SELECT ...,
       CASE 
           WHEN salary > 60000 AND age > 40 THEN SUM(salary) OVER (...)
           WHEN salary > 40000 OR age < 30 THEN AVG(salary) OVER (...)
           ELSE COUNT(*) OVER (...)
       END AS wf_result
FROM ...
```

**Why Safe**:
- All columns are partition-local
- Deterministic boolean logic
- Window functions all have PARTITION BY dept

**Bug Potential**:
- Complex boolean expression evaluation
- Short-circuit evaluation bugs
- NULL handling in AND/OR

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.15)) {
    StringBuilder condition = new StringBuilder();
    int numConditions = Randomly.getInteger(2, 3);
    
    for (int i = 0; i < numConditions; i++) {
        if (i > 0) condition.append(Randomly.fromOptions(" AND ", " OR "));
        SQLite3Column col = Randomly.fromList(columns);
        condition.append(col.getName());
        condition.append(Randomly.fromOptions(" > ", " < ", " = "));
        condition.append(generateValue(col.getType()));
    }
    
    windowFunctionExpr = "CASE WHEN " + condition + 
                        " THEN " + generateWindowFunction() +
                        " ELSE " + generateWindowFunction() + " END";
}
```

### Validation Checklist

- [ ] Run 3000 test cases with Phase 1-3 mutations
- [ ] Zero false positives
- [ ] Verify CASE branches are deterministic
- [ ] Test NULL handling in WHEN conditions
- [ ] Verify type consistency across branches
- [ ] Check optimizer doesn't break with multiple window functions
- [ ] Manually verify 15 cases with CASE expressions

### Code Impact
**Level**: MEDIUM  
**Files**: `SQLite3MRUPOracle.java` (query builder, window function generator)  
**Lines Changed**: ~150-250

### Expected Bug-Finding Impact
**Level**: HIGH  
- CASE evaluation bugs
- Type inference across branches
- Optimizer bugs with conditional window functions
- NULL handling in complex conditions

---

## Phase 4: Subquery-Level Mutations

### Goals
- Add subquery complexity
- Test interaction between window functions and subqueries
- Maintain partition locality and determinism

### Mutation Types

#### M4.1: IN Subquery with Partition-Local Values
**Current**: Simple SELECT  
**Mutated**:
```sql
SELECT dept, salary, age,
       SUM(salary) OVER (PARTITION BY dept ORDER BY age) AS wf_result
FROM t1
WHERE salary IN (
    SELECT salary FROM t1 WHERE dept = t1.dept AND age > 30
)
```

**Why Safe**:
- Subquery is correlated but partition-local (dept = t1.dept)
- Deterministic: same set of salaries for same partition
- MRUP preserved: filtering is partition-local
- **Critical**: Subquery must reference same table (t1 for Q1, t2 for Q2, t_union for Q_union)

**Examples**:
```sql
-- IN subquery
SELECT dept, salary, age,
       COUNT(*) OVER (PARTITION BY dept ORDER BY salary) AS wf_result
FROM t1
WHERE age IN (
    SELECT age FROM t1 WHERE dept = t1.dept AND salary > 50000
)

-- NOT IN subquery
SELECT dept, salary, age,
       AVG(salary) OVER (PARTITION BY dept ORDER BY age) AS wf_result
FROM t1
WHERE c0 NOT IN (
    SELECT c0 FROM t1 WHERE dept = t1.dept AND c0 IS NOT NULL
)
```

**Bug Potential**:
- Correlated subquery optimization bugs
- NULL handling in IN/NOT IN
- Subquery + window function interaction
- Partition boundary handling

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.2)) {
    SQLite3Column filterCol = Randomly.fromList(columns);
    SQLite3Column subqueryCol = Randomly.fromList(columns);
    
    String subquery = "SELECT " + subqueryCol.getName() + 
                     " FROM " + tableName + 
                     " WHERE dept = " + tableName + ".dept" +
                     " AND " + generateCondition();
    
    query.append(" WHERE ").append(filterCol.getName());
    query.append(Randomly.fromOptions(" IN ", " NOT IN "));
    query.append("(").append(subquery).append(")");
}
```

#### M4.2: EXISTS Subquery with Partition-Local Correlation
**Current**: Simple SELECT  
**Mutated**:
```sql
SELECT dept, salary, age,
       ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS wf_result
FROM t1 AS outer_t
WHERE EXISTS (
    SELECT 1 FROM t1 AS inner_t 
    WHERE inner_t.dept = outer_t.dept 
      AND inner_t.salary > outer_t.salary
)
```

**Why Safe**:
- EXISTS checks for existence within same partition
- Deterministic: same result for same row
- Partition-local correlation
- MRUP preserved

**Examples**:
```sql
-- EXISTS with comparison
SELECT dept, salary, age,
       DENSE_RANK() OVER (PARTITION BY dept ORDER BY age) AS wf_result
FROM t1 AS outer_t
WHERE EXISTS (
    SELECT 1 FROM t1 AS inner_t
    WHERE inner_t.dept = outer_t.dept
      AND inner_t.age < outer_t.age
)

-- NOT EXISTS
SELECT dept, salary, age,
       SUM(salary) OVER (PARTITION BY dept ORDER BY salary) AS wf_result
FROM t1 AS outer_t
WHERE NOT EXISTS (
    SELECT 1 FROM t1 AS inner_t
    WHERE inner_t.dept = outer_t.dept
      AND inner_t.c0 IS NULL
)
```

**Bug Potential**:
- EXISTS optimization bugs
- Correlated subquery + window function interaction
- NULL handling in correlation condition
- Optimizer confusion with complex predicates

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
if (Randomly.getBooleanWithProbability(0.15)) {
    SQLite3Column corrCol = Randomly.fromList(columns);
    String existsType = Randomly.fromOptions("EXISTS", "NOT EXISTS");
    
    String subquery = "SELECT 1 FROM " + tableName + " AS inner_t" +
                     " WHERE inner_t.dept = outer_t.dept" +
                     " AND inner_t." + corrCol.getName() +
                     Randomly.fromOptions(" > ", " < ", " = ") +
                     "outer_t." + corrCol.getName();
    
    query.append(" WHERE ").append(existsType);
    query.append(" (").append(subquery).append(")");
}
```

#### M4.3: Subquery in SELECT List (Scalar Subquery)
**Current**: Simple window function  
**Mutated**:
```sql
SELECT dept, salary, age,
       SUM(salary) OVER (PARTITION BY dept ORDER BY age) AS wf_result,
       (SELECT AVG(salary) FROM t1 AS inner_t 
        WHERE inner_t.dept = outer_t.dept) AS avg_dept_salary
FROM t1 AS outer_t
```

**Why Safe**:
- Scalar subquery returns single value per row
- Partition-local correlation
- Deterministic
- **Note**: This adds a column, need to handle in comparison

**Bug Potential**:
- Scalar subquery + window function interaction
- Multiple subqueries in SELECT list
- Optimizer bugs with complex SELECT

**Implementation**:
```java
// In SQLite3MRUPOracle.java - buildQuery()
// NOTE: This requires modifying result comparison to handle extra columns
if (Randomly.getBooleanWithProbability(0.1)) {
    SQLite3Column aggCol = Randomly.fromList(columns);
    String aggFunc = Randomly.fromOptions("AVG", "SUM", "COUNT", "MIN", "MAX");
    
    String scalarSubquery = "(SELECT " + aggFunc + "(" + aggCol.getName() + ")" +
                           " FROM " + tableName + " AS inner_t" +
                           " WHERE inner_t.dept = outer_t.dept)";
    
    query.append(", ").append(scalarSubquery).append(" AS subquery_result");
}
```

### Advanced Mutation (Optional)

#### M4.4: Window Function in Subquery
**Current**: Simple subquery  
**Mutated**:
```sql
SELECT dept, salary, age,
       COUNT(*) OVER (PARTITION BY dept ORDER BY salary) AS wf_result
FROM t1 AS outer_t
WHERE salary > (
    SELECT AVG(salary) OVER (PARTITION BY dept ORDER BY age)
    FROM t1 AS inner_t
    WHERE inner_t.dept = outer_t.dept
    LIMIT 1
)
```

**Why Safe**:
- Window function in subquery is partition-local
- LIMIT 1 ensures scalar result
- Deterministic with ORDER BY

**Bug Potential**: HIGH
- Nested window function evaluation
- Optimizer complexity
- Frame calculation in subquery context

**Implementation**: Complex, requires careful handling

### Validation Checklist

- [ ] Run 5000 test cases with all Phase 1-4 mutations
- [ ] Zero false positives
- [ ] Verify subqueries are partition-local
- [ ] Test NULL handling in IN/EXISTS
- [ ] Check correlated subquery optimization
- [ ] Verify scalar subquery returns single value
- [ ] Test interaction between subquery and window function
- [ ] Manually verify 20 cases with subqueries

### Code Impact
**Level**: HIGH  
**Files**: `SQLite3MRUPOracle.java` (query builder, major refactor)  
**Lines Changed**: ~300-500

### Expected Bug-Finding Impact
**Level**: VERY HIGH  
- Correlated subquery bugs
- Optimizer bugs with complex queries
- Subquery + window function interaction
- NULL handling in complex predicates

---

## Implementation Strategy

### Mutation Toggle System

Add a configuration system to enable/disable mutations:

```java
// In SQLite3MRUPOracle.java
public class MutationConfig {
    // Phase 1
    public static boolean ENABLE_COMPLEX_ORDER_BY = false;
    public static boolean ENABLE_COMPLEX_FRAMES = false;
    public static boolean ENABLE_FILTER_CLAUSE = false;
    
    // Phase 2
    public static boolean ENABLE_ARITHMETIC = false;
    public static boolean ENABLE_CASTING = false;
    public static boolean ENABLE_NULL_FUNCTIONS = false;
    
    // Phase 3
    public static boolean ENABLE_CASE_ON_RESULT = false;
    public static boolean ENABLE_CASE_ON_COLUMNS = false;
    public static boolean ENABLE_COMPLEX_CASE = false;
    
    // Phase 4
    public static boolean ENABLE_IN_SUBQUERY = false;
    public static boolean ENABLE_EXISTS_SUBQUERY = false;
    public static boolean ENABLE_SCALAR_SUBQUERY = false;
    
    // Global mutation probability
    public static double MUTATION_PROBABILITY = 0.3;
}
```

### Logging Enhancement

Add mutation tracking to logs:

```java
// In SQLite3MRUPTestCaseLogger.java
public void logMutations(List<String> appliedMutations) {
    logBuffer.append("🔄 Applied Mutations:\n");
    for (String mutation : appliedMutations) {
        logBuffer.append("   - ").append(mutation).append("\n");
    }
    logBuffer.append("\n");
}
```

### Validation Framework

Create a validation test suite:

```bash
# test_mutations.sh
#!/bin/bash

# Test each phase independently
for phase in 1 2 3 4; do
    echo "Testing Phase $phase..."
    java -Dmrup.logging.enabled=true \
         -Dmrup.phase=$phase \
         -jar target/sqlancer-2.0.0.jar \
         --num-queries 1000 \
         sqlite3 --oracle MRUP
    
    # Check for failures
    if [ $? -ne 0 ]; then
        echo "Phase $phase FAILED"
        exit 1
    fi
done

echo "All phases PASSED"
```

---

## Risk Assessment

### Phase 1: LOW RISK
- **Probability of breaking MRUP**: Very Low
- **Complexity**: Low
- **Rollback effort**: Trivial (toggle flags)
- **Testing time**: 1-2 hours

### Phase 2: LOW-MEDIUM RISK
- **Probability of breaking MRUP**: Low
- **Complexity**: Low-Medium
- **Rollback effort**: Easy (toggle flags)
- **Testing time**: 2-4 hours
- **Risk**: Type comparison might need enhancement

### Phase 3: MEDIUM RISK
- **Probability of breaking MRUP**: Medium
- **Complexity**: Medium
- **Rollback effort**: Moderate (more code changes)
- **Testing time**: 4-8 hours
- **Risk**: CASE with multiple window functions needs careful testing

### Phase 4: HIGH RISK
- **Probability of breaking MRUP**: Medium-High
- **Complexity**: High
- **Rollback effort**: Significant (major refactor)
- **Testing time**: 8-16 hours
- **Risk**: Subquery correlation must be partition-local

---

## Expected Bug Discovery

### Phase 1: Window Specification Bugs
- Frame calculation errors
- Complex ORDER BY handling
- FILTER clause edge cases
- **Estimated bugs**: 2-5

### Phase 2: Type System Bugs
- Arithmetic overflow/underflow
- Type coercion errors
- NULL propagation bugs
- **Estimated bugs**: 5-10

### Phase 3: Conditional Logic Bugs
- CASE evaluation order
- Type inference across branches
- Optimizer bugs with multiple window functions
- **Estimated bugs**: 10-20

### Phase 4: Optimizer Bugs
- Correlated subquery optimization
- Subquery + window function interaction
- Complex predicate evaluation
- **Estimated bugs**: 20-50+

---

## Recommended Rollout Schedule

### Week 1: Phase 1
- Day 1-2: Implement mutations
- Day 3-4: Test with 10,000 queries
- Day 5: Analyze results, fix any false positives

### Week 2: Phase 2
- Day 1-2: Implement mutations
- Day 3-4: Test with 20,000 queries
- Day 5: Analyze results, enhance type comparison if needed

### Week 3: Phase 3
- Day 1-3: Implement mutations
- Day 4-5: Test with 30,000 queries
- Day 6-7: Analyze results, fix any issues

### Week 4: Phase 4
- Day 1-4: Implement mutations (major refactor)
- Day 5-7: Test with 50,000 queries
- Day 8-10: Analyze results, fix any issues

### Week 5: Integration
- Run all phases together
- Tune mutation probabilities
- Document findings
- Prepare bug reports

---

## Additional Recommendations

### 1. Mutation Sampling
Don't apply all mutations at once. Use probability:
```java
if (Randomly.getBooleanWithProbability(MutationConfig.MUTATION_PROBABILITY)) {
    applyMutation();
}
```

### 2. Mutation Tracking
Track which mutations are applied to each test case:
```java
List<String> appliedMutations = new ArrayList<>();
if (applyArithmetic()) appliedMutations.add("ARITHMETIC");
if (applyCasting()) appliedMutations.add("CASTING");
logger.logMutations(appliedMutations);
```

### 3. Differential Testing
When a bug is found, generate minimal reproduction:
```java
// Disable all mutations except the one that triggered the bug
MutationConfig.ENABLE_ALL = false;
MutationConfig.ENABLE_ARITHMETIC = true;  // Only the failing mutation
```

### 4. Performance Monitoring
Track query execution time:
```java
long startTime = System.currentTimeMillis();
executeQuery(q);
long duration = System.currentTimeMillis() - startTime;
if (duration > 5000) {
    logger.logSlowQuery(q, duration);
}
```

### 5. Bug Classification
Categorize bugs found:
- **Correctness bugs**: Wrong result
- **Crash bugs**: DBMS crashes
- **Performance bugs**: Extremely slow queries
- **Error handling bugs**: Incorrect error messages

---

## Success Metrics

### Quantitative
- **Zero false positives** maintained across all phases
- **Bug discovery rate** > 1 bug per 1000 queries
- **Query complexity** increased by 3-5x
- **Test throughput** maintained > 100 queries/second

### Qualitative
- Bugs found are **deep optimizer bugs** (not trivial)
- Mutations are **semantically meaningful**
- Code remains **maintainable** and **debuggable**
- Oracle is **stable** and **reliable**

---

## Conclusion

This 4-phase mutation roadmap provides a **safe, incremental path** to increase query complexity while maintaining MRUP correctness. Each phase:

✅ Has clear goals and validation criteria  
✅ Can be rolled back independently  
✅ Increases bug-finding potential  
✅ Maintains zero false positives  
✅ Is thoroughly testable  

**Recommended approach**: Start with Phase 1, validate thoroughly, then proceed incrementally. Don't rush to Phase 4 - the bugs found in Phases 1-3 are valuable and easier to debug.

**Key insight**: Even "simple" mutations (Phase 1-2) can expose deep bugs. Complex mutations (Phase 3-4) exponentially increase bug potential but also increase debugging difficulty. Balance is key.

---

## Next Steps

1. ✅ Review this roadmap
2. ⏳ Implement Phase 1 mutations
3. ⏳ Create mutation toggle system
4. ⏳ Enhance logging with mutation tracking
5. ⏳ Run validation tests
6. ⏳ Proceed to Phase 2 after validation

**Status**: Ready for implementation 🚀

