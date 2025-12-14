# Phase 3: Enhanced CASE WHEN Mutation Analysis

## Executive Summary

Phase 3 focuses on **CASE WHEN mutations** to add conditional logic while preserving MRUP semantics. This phase has **HIGH bug-finding potential** because it targets:
- Type inference across branches
- NULL handling in complex conditions
- Optimizer confusion with conditional window functions
- CASE evaluation order bugs

## Why Skip Phase 2?

**Agreed.** Phase 2 (arithmetic on window results like `wf_result * 2`) has **LOW impact** because:
- Simple arithmetic is well-tested in DBMSs
- Doesn't expose deep window function bugs
- Mostly tests basic type coercion
- Oracle complexity increases without bug-finding benefit

**Phase 3 is much more valuable** because CASE WHEN creates:
- Complex control flow
- Multiple execution paths
- Type inference challenges
- Optimizer confusion

---

## Phase 3 Mutation Strategies (Enhanced)

### Strategy 1: CASE on Window Function Result ⭐⭐⭐

**Impact:** HIGH - Tests CASE evaluation with computed window results

**Original Query:**
```sql
SELECT dept, salary, age,
       ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS wf_result
FROM t1
```

**Mutated Query:**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) <= 3 THEN 'TOP'
           WHEN ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) <= 10 THEN 'MID'
           ELSE 'LOW'
       END AS wf_result
FROM t1
```

**Why Safe:**
- Window function computed first, then CASE applied
- Deterministic: same row → same wf_result → same CASE branch
- MRUP preserved: `CASE(H(t1)) ∪ CASE(H(t2)) = CASE(H(t_union))`

**Bug Targets:**
- ✅ CASE evaluation order
- ✅ Type inference (numeric → string)
- ✅ NULL handling in WHEN conditions
- ✅ Multiple evaluations of same window function

**Variants:**
```sql
-- Variant A: NULL handling
CASE 
    WHEN AVG(salary) OVER (...) IS NULL THEN 0
    WHEN AVG(salary) OVER (...) > 60000 THEN 1
    ELSE 2
END

-- Variant B: Nested CASE
CASE 
    WHEN SUM(salary) OVER (...) > 100000 THEN
        CASE WHEN age > 40 THEN 'HIGH_SENIOR' ELSE 'HIGH_JUNIOR' END
    ELSE 'LOW'
END

-- Variant C: Complex conditions
CASE 
    WHEN RANK() OVER (...) BETWEEN 1 AND 5 THEN 'TOP_5'
    WHEN RANK() OVER (...) IN (6, 7, 8, 9, 10) THEN 'TOP_10'
    ELSE 'REST'
END
```

---

### Strategy 2: CASE with Different Window Functions per Branch ⭐⭐⭐⭐⭐

**Impact:** VERY HIGH - Forces optimizer to handle multiple window functions

**Original Query:**
```sql
SELECT dept, salary, age,
       SUM(salary) OVER (PARTITION BY dept ORDER BY age) AS wf_result
FROM t1
```

**Mutated Query:**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN age > 40 THEN SUM(salary) OVER (PARTITION BY dept ORDER BY age)
           ELSE COUNT(*) OVER (PARTITION BY dept ORDER BY age)
       END AS wf_result
FROM t1
```

**Why Safe:**
- Condition uses partition-local column (age)
- Both window functions have PARTITION BY dept
- Deterministic: same row → same branch
- MRUP preserved: partition-local decision

**Bug Targets:**
- ✅ Multiple window function evaluation
- ✅ Type mismatch between branches (SUM returns numeric, COUNT returns integer)
- ✅ Optimizer confusion with conditional window functions
- ✅ Frame calculation in different branches

**Variants:**
```sql
-- Variant A: Different functions, same frame
CASE 
    WHEN salary > 60000 THEN SUM(salary) OVER (PARTITION BY dept ORDER BY age ROWS 2 PRECEDING)
    ELSE AVG(salary) OVER (PARTITION BY dept ORDER BY age ROWS 2 PRECEDING)
END

-- Variant B: Same function, different frames
CASE 
    WHEN c0 IS NULL THEN AVG(salary) OVER (PARTITION BY dept ORDER BY age ROWS UNBOUNDED PRECEDING)
    ELSE AVG(salary) OVER (PARTITION BY dept ORDER BY age ROWS 1 PRECEDING)
END

-- Variant C: Different ORDER BY in branches
CASE 
    WHEN salary > 50000 THEN RANK() OVER (PARTITION BY dept ORDER BY salary DESC)
    ELSE RANK() OVER (PARTITION BY dept ORDER BY age ASC)
END
```

---

### Strategy 3: CASE with Complex Boolean Conditions ⭐⭐⭐⭐

**Impact:** HIGH - Tests complex condition evaluation

**Mutated Query:**
```sql
SELECT dept, salary, age, c0,
       CASE 
           WHEN salary > 60000 AND age > 40 THEN SUM(salary) OVER (...)
           WHEN salary > 40000 OR c0 IS NOT NULL THEN AVG(salary) OVER (...)
           WHEN age BETWEEN 25 AND 35 THEN COUNT(*) OVER (...)
           ELSE 0
       END AS wf_result
FROM t1
```

**Why Safe:**
- All columns are partition-local
- Deterministic boolean logic
- All window functions have PARTITION BY dept

**Bug Targets:**
- ✅ Complex boolean expression evaluation
- ✅ Short-circuit evaluation (AND/OR)
- ✅ NULL handling in AND/OR
- ✅ BETWEEN operator with NULL

**Variants:**
```sql
-- Variant A: NOT conditions
CASE 
    WHEN NOT (salary > 50000) THEN SUM(salary) OVER (...)
    WHEN NOT (age IS NULL) THEN AVG(age) OVER (...)
    ELSE 0
END

-- Variant B: IN operator
CASE 
    WHEN dept IN ('Engineering', 'Finance') AND salary > 50000 THEN RANK() OVER (...)
    ELSE 0
END

-- Variant C: NULL-safe comparisons
CASE 
    WHEN c0 IS NOT NULL AND c0 > 0 THEN SUM(c0) OVER (...)
    WHEN c0 IS NULL THEN 0
    ELSE AVG(c0) OVER (...)
END
```

---

### Strategy 4: CASE with Aggregate + Window Function Mix ⭐⭐⭐⭐

**Impact:** HIGH - Tests interaction between aggregates and window functions

**Mutated Query:**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN salary > (SELECT AVG(salary) FROM t1 WHERE dept = t1.dept) 
               THEN ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)
           ELSE 0
       END AS wf_result
FROM t1
```

**Why Safe:**
- Subquery is partition-local (dept = t1.dept)
- Deterministic: same row → same result
- MRUP preserved: partition-local aggregate

**Bug Targets:**
- ✅ Subquery + window function interaction
- ✅ Correlated subquery optimization
- ✅ Aggregate in WHEN condition
- ✅ Type coercion (aggregate result vs window result)

**Variants:**
```sql
-- Variant A: MAX/MIN in condition
CASE 
    WHEN salary = (SELECT MAX(salary) FROM t1 WHERE dept = t1.dept)
        THEN 'TOP_EARNER'
    ELSE CAST(RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS TEXT)
END

-- Variant B: COUNT in condition
CASE 
    WHEN (SELECT COUNT(*) FROM t1 WHERE dept = t1.dept) > 5
        THEN DENSE_RANK() OVER (PARTITION BY dept ORDER BY age)
    ELSE 0
END
```

---

### Strategy 5: CASE with NULL Coalescing ⭐⭐⭐⭐

**Impact:** HIGH - Tests NULL handling edge cases

**Mutated Query:**
```sql
SELECT dept, salary, age, c0, c1,
       CASE 
           WHEN c0 IS NULL AND c1 IS NULL THEN 0
           WHEN c0 IS NULL THEN AVG(c1) OVER (PARTITION BY dept ORDER BY salary)
           WHEN c1 IS NULL THEN AVG(c0) OVER (PARTITION BY dept ORDER BY salary)
           ELSE AVG(c0 + c1) OVER (PARTITION BY dept ORDER BY salary)
       END AS wf_result
FROM t1
```

**Why Safe:**
- All conditions are partition-local
- Deterministic NULL checks
- MRUP preserved

**Bug Targets:**
- ✅ NULL handling in multiple branches
- ✅ COALESCE-like logic
- ✅ Arithmetic with NULL
- ✅ Window function on NULL columns

**Variants:**
```sql
-- Variant A: COALESCE equivalent
CASE 
    WHEN c0 IS NOT NULL THEN SUM(c0) OVER (...)
    WHEN c1 IS NOT NULL THEN SUM(c1) OVER (...)
    ELSE 0
END

-- Variant B: NULL propagation
CASE 
    WHEN c0 IS NULL THEN NULL
    WHEN c0 > 0 THEN SUM(c0) OVER (...)
    ELSE AVG(c0) OVER (...)
END
```

---

### Strategy 6: CASE with Type Conversions ⭐⭐⭐⭐

**Impact:** HIGH - Tests type system edge cases

**Mutated Query:**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN salary > 50000 THEN CAST(ROW_NUMBER() OVER (...) AS TEXT)
           WHEN age > 30 THEN CAST(AVG(salary) OVER (...) AS TEXT)
           ELSE 'N/A'
       END AS wf_result
FROM t1
```

**Why Safe:**
- All branches return same type (TEXT)
- Deterministic conversions
- MRUP preserved

**Bug Targets:**
- ✅ Type inference across branches
- ✅ CAST in CASE context
- ✅ Numeric → String conversion
- ✅ NULL handling in CAST

**Variants:**
```sql
-- Variant A: Mixed numeric types
CASE 
    WHEN salary > 50000 THEN CAST(RANK() OVER (...) AS REAL)
    ELSE AVG(salary) OVER (...)
END

-- Variant B: String concatenation
CASE 
    WHEN age > 40 THEN dept || '_' || CAST(ROW_NUMBER() OVER (...) AS TEXT)
    ELSE dept
END
```

---

## Implementation Priority

### Tier 1: Must Implement (Highest Impact)
1. **Strategy 2**: Different window functions per branch
2. **Strategy 4**: Aggregate + window function mix
3. **Strategy 5**: NULL coalescing

### Tier 2: Should Implement (High Impact)
4. **Strategy 1**: CASE on window function result
5. **Strategy 3**: Complex boolean conditions

### Tier 3: Nice to Have (Medium Impact)
6. **Strategy 6**: Type conversions

---

## MRUP Preservation Proof

For all Phase 3 mutations, MRUP is preserved because:

1. **Partition Locality**: All CASE conditions use partition-local columns
2. **Determinism**: Same row → same condition result → same branch
3. **Window Functions**: All window functions have `PARTITION BY dept`
4. **No Cross-Partition References**: CASE never references data from other partitions

**Mathematical Proof:**
```
Given: H(t1 ∪ t2) = H(t1) ∪ H(t2)  (MRUP for window functions)

For CASE mutation:
  CASE(H(t1 ∪ t2)) = CASE(H(t1) ∪ H(t2))  (CASE is applied row-by-row)
                    = CASE(H(t1)) ∪ CASE(H(t2))  (UNION distributes over CASE)

Therefore: MRUP is preserved ✓
```

---

## Comparator Considerations

**IMPORTANT:** Phase 3 mutations may change result **types**:
- Window function returns `INTEGER` or `REAL`
- CASE may return `TEXT`

**Solution:** Update Phase 3 comparator to:
1. **Type-aware comparison**: Compare values based on actual type
2. **String normalization**: Trim whitespace, case-insensitive for TEXT
3. **NULL handling**: Ensure NULL = NULL in comparisons

**No changes needed to MRUP normalization** - it already handles mixed types.

---

## Testing Strategy

### Phase 3.1: Basic CASE (Strategy 1)
- Run 500 test cases
- Verify zero false positives
- Check type handling

### Phase 3.2: Multiple Window Functions (Strategy 2)
- Run 1000 test cases
- Focus on optimizer bugs
- Verify different window functions work

### Phase 3.3: Complex Conditions (Strategies 3-6)
- Run 1500 test cases
- Test NULL handling
- Test type conversions
- Test subquery interactions

---

## Expected Bug Categories

1. **Type Inference Bugs** (40% of bugs)
   - CASE branches return different types
   - Implicit type conversion errors
   - NULL type handling

2. **Optimizer Bugs** (30% of bugs)
   - Multiple window function evaluation
   - Common subexpression elimination
   - Dead branch elimination

3. **NULL Handling Bugs** (20% of bugs)
   - NULL in WHEN conditions
   - NULL propagation through CASE
   - IS NULL vs = NULL

4. **Evaluation Order Bugs** (10% of bugs)
   - CASE evaluation order
   - Short-circuit evaluation
   - Window function evaluation timing

---

## Code Changes Required

### 1. Add CASE Mutation to Oracle
**File:** `SQLite3MRUPOracle.java`
**Location:** After window function generation, before query building
**Lines:** ~100-200 new lines

### 2. Update Comparator (if needed)
**File:** `SQLite3MRUPOracle.java`
**Location:** `compareValue()` method
**Lines:** ~20-30 lines

### 3. Add CASE Mutation Operator
**File:** `SQLite3MRUPMutationOperator.java` (or new file)
**Location:** New methods for CASE generation
**Lines:** ~150-250 new lines

---

## Risk Assessment

**Risk Level:** MEDIUM

**Risks:**
1. ❌ **Type mismatch** → Mitigated by type-aware comparator
2. ❌ **Non-determinism** → Mitigated by partition-local conditions only
3. ❌ **MRUP violation** → Mitigated by mathematical proof above

**Rollback Plan:**
- Each strategy can be toggled on/off independently
- If false positives occur, disable that strategy
- Core oracle remains unchanged

---

## Success Metrics

**Phase 3 is successful if:**
- ✅ Zero false positives across 3000+ test cases
- ✅ At least 5 unique SQLite bugs found
- ✅ Query complexity increases significantly
- ✅ Execution speed remains > 80 queries/s
- ✅ All CASE mutations logged correctly

---

## Conclusion

**Phase 3 has VERY HIGH potential** for bug-finding because:
1. CASE WHEN is complex and error-prone
2. Multiple window functions stress the optimizer
3. Type inference across branches is challenging
4. NULL handling in conditions is tricky
5. Subquery + window function interaction is rare

**Recommendation:** Implement Phase 3 strategies in order of priority (Tier 1 → Tier 2 → Tier 3).

**Next Step:** Start with **Strategy 2** (different window functions per branch) as it has the highest impact.

