# Phase 3: Final CASE WHEN Mutation Plan

## Motivation from MySQL Bug (EET Oracle)

### The Bug-Finding Query

```sql
SELECT  
  CASE WHEN true THEN null 
       ELSE (DENSE_RANK() OVER (PARTITION BY ref_0.pkey ORDER BY ref_0.vkey DESC, ref_0.pkey ASC)) 
  END AS c_0, 
  
  CASE WHEN ((LAST_VALUE(ref_0.pkey) OVER (PARTITION BY ref_0.c19 ORDER BY ref_0.vkey DESC, ref_0.pkey DESC)) 
             IN (SELECT ref_0.c22 FROM t2 AS ref_8)) 
       THEN ref_0.c20 
       ELSE ref_0.c20 
  END AS c_1 
FROM t2 AS ref_0
ORDER BY c_1 DESC;
```

### Key Insights from This Bug

1. **Constant Conditions** (`WHEN true`, `WHEN false`)
   - Forces optimizer to eliminate dead branches
   - Tests constant folding with window functions
   - **Bug**: Optimizer may incorrectly eliminate window function evaluation

2. **Dead Branch with Window Function**
   - `WHEN true THEN null ELSE (DENSE_RANK() ...)`
   - The ELSE branch is never executed, but optimizer must handle it correctly
   - **Bug**: Window function in dead branch may cause crashes or wrong results

3. **Window Function in WHEN Condition**
   - `WHEN (LAST_VALUE(...) IN (SELECT ...)) THEN ...`
   - Window function result used in subquery condition
   - **Bug**: Evaluation order issues, subquery optimization bugs

4. **Identical THEN/ELSE Branches**
   - `THEN ref_0.c20 ELSE ref_0.c20`
   - Optimizer should recognize this, but may fail with complex conditions
   - **Bug**: Redundant evaluation, optimization bugs

5. **Subquery in WHEN Condition**
   - `IN (SELECT ...)` inside WHEN
   - Combines window function + subquery + CASE
   - **Bug**: Complex interaction between features

---

## Enhanced Phase 3 Strategies (Based on MySQL Bug)

### NEW Strategy 0: Constant Condition with Dead Branch ⭐⭐⭐⭐⭐

**HIGHEST PRIORITY** - This is what found the MySQL bug!

**Mutation A: Always True with NULL**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN 1 = 1 THEN NULL
           ELSE ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC)
       END AS wf_result
FROM t1
```

**Mutation B: Always False with Window Function**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN 1 = 0 THEN 'DEAD_BRANCH'
           ELSE RANK() OVER (PARTITION BY dept ORDER BY salary DESC)
       END AS wf_result
FROM t1
```

**Mutation C: TRUE/FALSE literals**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN TRUE THEN NULL
           ELSE SUM(salary) OVER (PARTITION BY dept ORDER BY age)
       END AS wf_result
FROM t1
```

**Why This Finds Bugs:**
- Optimizer must handle dead branch elimination
- Window function in dead branch may not be properly skipped
- NULL in always-true branch tests type inference
- Tests constant folding with window functions

**MRUP Safe:** ✅ Constant condition is deterministic across all partitions

---

### Strategy 1: Window Function in WHEN Condition ⭐⭐⭐⭐⭐

**Directly from MySQL bug!**

```sql
SELECT dept, salary, age, c0,
       CASE 
           WHEN ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) <= 3 
               THEN 'TOP_3'
           ELSE 'OTHER'
       END AS wf_result
FROM t1
```

**Variant A: Window function with subquery**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN RANK() OVER (PARTITION BY dept ORDER BY salary DESC) 
                IN (SELECT 1 UNION SELECT 2 UNION SELECT 3)
               THEN 'TOP_3'
           ELSE 'OTHER'
       END AS wf_result
FROM t1
```

**Variant B: Multiple window functions in condition**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) = 
                DENSE_RANK() OVER (PARTITION BY dept ORDER BY salary DESC)
               THEN 'UNIQUE_SALARY'
           ELSE 'DUPLICATE_SALARY'
       END AS wf_result
FROM t1
```

**Why This Finds Bugs:**
- Window function evaluation in WHEN condition
- Evaluation order: window function first, then CASE
- Optimizer may incorrectly reorder or cache

**MRUP Safe:** ✅ Window function has PARTITION BY dept, deterministic

---

### Strategy 2: Identical THEN/ELSE Branches ⭐⭐⭐⭐

**From MySQL bug: `THEN ref_0.c20 ELSE ref_0.c20`**

```sql
SELECT dept, salary, age,
       CASE 
           WHEN salary > 50000 THEN salary
           ELSE salary
       END AS wf_result
FROM t1
```

**Variant A: Identical window functions**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN age > 40 THEN SUM(salary) OVER (PARTITION BY dept ORDER BY age)
           ELSE SUM(salary) OVER (PARTITION BY dept ORDER BY age)
       END AS wf_result
FROM t1
```

**Variant B: Complex identical expressions**
```sql
SELECT dept, salary, age, c0,
       CASE 
           WHEN c0 IS NULL THEN COALESCE(c0, 0)
           ELSE COALESCE(c0, 0)
       END AS wf_result
FROM t1
```

**Why This Finds Bugs:**
- Optimizer should recognize identical branches
- May fail to optimize correctly
- Tests common subexpression elimination

**MRUP Safe:** ✅ All expressions are partition-local

---

### Strategy 3: Different Window Functions per Branch ⭐⭐⭐⭐⭐

**Original Strategy 2 - Still very important**

```sql
SELECT dept, salary, age,
       CASE 
           WHEN age > 40 THEN SUM(salary) OVER (PARTITION BY dept ORDER BY age)
           ELSE COUNT(*) OVER (PARTITION BY dept ORDER BY age)
       END AS wf_result
FROM t1
```

**Variant A: Different frames**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN salary > 60000 
               THEN AVG(salary) OVER (PARTITION BY dept ORDER BY age ROWS UNBOUNDED PRECEDING)
           ELSE AVG(salary) OVER (PARTITION BY dept ORDER BY age ROWS 2 PRECEDING)
       END AS wf_result
FROM t1
```

**Variant B: Different ORDER BY**
```sql
SELECT dept, salary, age,
       CASE 
           WHEN c0 IS NOT NULL 
               THEN RANK() OVER (PARTITION BY dept ORDER BY salary DESC)
           ELSE RANK() OVER (PARTITION BY dept ORDER BY age ASC)
       END AS wf_result
FROM t1
```

---

### Strategy 4: NULL Handling Patterns ⭐⭐⭐⭐

```sql
SELECT dept, salary, age, c0, c1,
       CASE 
           WHEN c0 IS NULL THEN NULL
           WHEN c1 IS NULL THEN AVG(c0) OVER (PARTITION BY dept ORDER BY salary)
           ELSE AVG(c0 + c1) OVER (PARTITION BY dept ORDER BY salary)
       END AS wf_result
FROM t1
```

**Variant A: NULL propagation**
```sql
SELECT dept, salary, age, c0,
       CASE 
           WHEN c0 IS NULL THEN NULL
           WHEN c0 > 0 THEN SUM(c0) OVER (PARTITION BY dept ORDER BY salary)
           ELSE 0
       END AS wf_result
FROM t1
```

---

### Strategy 5: Complex Boolean Conditions ⭐⭐⭐⭐

```sql
SELECT dept, salary, age, c0,
       CASE 
           WHEN salary > 60000 AND age > 40 AND c0 IS NOT NULL
               THEN SUM(salary) OVER (PARTITION BY dept ORDER BY age)
           WHEN salary > 40000 OR age < 30
               THEN AVG(salary) OVER (PARTITION BY dept ORDER BY age)
           ELSE COUNT(*) OVER (PARTITION BY dept ORDER BY age)
       END AS wf_result
FROM t1
```

---

### Strategy 6: Nested CASE ⭐⭐⭐

```sql
SELECT dept, salary, age,
       CASE 
           WHEN salary > 70000 THEN
               CASE 
                   WHEN age > 40 THEN 'HIGH_SENIOR'
                   ELSE 'HIGH_JUNIOR'
               END
           ELSE
               CASE 
                   WHEN age > 40 THEN 'LOW_SENIOR'
                   ELSE 'LOW_JUNIOR'
               END
       END AS wf_result
FROM t1
```

---

## Final Implementation Plan

### Phase 3.0: Constant Conditions (NEW - Highest Priority)

**Goal:** Find optimizer bugs with dead branch elimination

**Mutations:**
1. `CASE WHEN TRUE THEN NULL ELSE wf END`
2. `CASE WHEN FALSE THEN 'dead' ELSE wf END`
3. `CASE WHEN 1=1 THEN NULL ELSE wf END`
4. `CASE WHEN 1=0 THEN wf ELSE NULL END`

**Probability:** 30% of queries

**Expected Bugs:**
- Dead branch elimination with window functions
- Constant folding bugs
- Type inference with NULL in always-true branch

---

### Phase 3.1: Window Function in WHEN Condition

**Goal:** Test window function evaluation in conditions

**Mutations:**
1. `CASE WHEN wf <= 3 THEN 'TOP' ELSE 'OTHER' END`
2. `CASE WHEN wf IN (1,2,3) THEN 'TOP' ELSE 'OTHER' END`
3. `CASE WHEN wf1 = wf2 THEN 'EQUAL' ELSE 'DIFFERENT' END`

**Probability:** 25% of queries

**Expected Bugs:**
- Evaluation order bugs
- Window function caching issues
- Subquery + window function interaction

---

### Phase 3.2: Different Window Functions per Branch

**Goal:** Force optimizer to handle multiple window functions

**Mutations:**
1. Different functions: `SUM` vs `COUNT`
2. Different frames: `UNBOUNDED PRECEDING` vs `2 PRECEDING`
3. Different ORDER BY: `salary DESC` vs `age ASC`

**Probability:** 20% of queries

**Expected Bugs:**
- Multiple window function evaluation
- Type mismatch between branches
- Frame calculation bugs

---

### Phase 3.3: Identical THEN/ELSE Branches

**Goal:** Test optimizer's common subexpression elimination

**Mutations:**
1. `CASE WHEN cond THEN salary ELSE salary END`
2. `CASE WHEN cond THEN wf ELSE wf END`

**Probability:** 15% of queries

**Expected Bugs:**
- Redundant evaluation
- Optimization bugs

---

### Phase 3.4: NULL Handling

**Goal:** Test NULL propagation and handling

**Mutations:**
1. `CASE WHEN c0 IS NULL THEN NULL ELSE wf END`
2. `CASE WHEN c0 IS NULL THEN 0 ELSE AVG(c0) OVER (...) END`

**Probability:** 10% of queries

**Expected Bugs:**
- NULL handling in conditions
- NULL propagation through CASE

---

## Implementation Architecture

### 1. New Class: `SQLite3MRUPCaseMutator.java`

```java
public class SQLite3MRUPCaseMutator {
    
    // Phase 3.0: Constant conditions
    public static String applyConstantCondition(String windowFunction) {
        int variant = Randomly.fromOptions(1, 2, 3, 4);
        switch (variant) {
            case 1: return "CASE WHEN TRUE THEN NULL ELSE " + windowFunction + " END";
            case 2: return "CASE WHEN FALSE THEN 'dead' ELSE " + windowFunction + " END";
            case 3: return "CASE WHEN 1=1 THEN NULL ELSE " + windowFunction + " END";
            case 4: return "CASE WHEN 1=0 THEN " + windowFunction + " ELSE NULL END";
        }
    }
    
    // Phase 3.1: Window function in WHEN
    public static String applyWindowFunctionCondition(String windowFunction) {
        int threshold = Randomly.fromOptions(1, 2, 3, 5, 10);
        return "CASE WHEN " + windowFunction + " <= " + threshold + 
               " THEN 'TOP' ELSE 'OTHER' END";
    }
    
    // Phase 3.2: Different window functions per branch
    public static String applyDifferentWindowFunctions(
            String windowSpec, 
            List<SQLite3Column> columns,
            String condition) {
        
        String wf1 = generateWindowFunction1(windowSpec, columns);
        String wf2 = generateWindowFunction2(windowSpec, columns);
        
        return "CASE WHEN " + condition + 
               " THEN " + wf1 + 
               " ELSE " + wf2 + " END";
    }
    
    // Phase 3.3: Identical branches
    public static String applyIdenticalBranches(String expression, String condition) {
        return "CASE WHEN " + condition + 
               " THEN " + expression + 
               " ELSE " + expression + " END";
    }
    
    // Phase 3.4: NULL handling
    public static String applyNullHandling(String windowFunction, String column) {
        return "CASE WHEN " + column + " IS NULL THEN NULL ELSE " + 
               windowFunction + " END";
    }
}
```

### 2. Integration in `SQLite3MRUPOracle.java`

```java
// In check() method, after window function generation

// Phase 3: Apply CASE WHEN mutations
if (Randomly.getBooleanWithProbability(0.7)) {  // 70% of queries get CASE mutation
    
    double rand = Randomly.getDouble();
    
    if (rand < 0.30) {
        // Phase 3.0: Constant conditions (30%)
        windowFunction = SQLite3MRUPCaseMutator.applyConstantCondition(windowFunction);
        logger.logCaseMutation("Constant Condition", windowFunction);
        
    } else if (rand < 0.55) {
        // Phase 3.1: Window function in WHEN (25%)
        windowFunction = SQLite3MRUPCaseMutator.applyWindowFunctionCondition(windowFunction);
        logger.logCaseMutation("Window Function in WHEN", windowFunction);
        
    } else if (rand < 0.75) {
        // Phase 3.2: Different window functions (20%)
        String condition = generatePartitionLocalCondition(columns);
        windowFunction = SQLite3MRUPCaseMutator.applyDifferentWindowFunctions(
            windowSpec, columns, condition);
        logger.logCaseMutation("Different Window Functions", windowFunction);
        
    } else if (rand < 0.90) {
        // Phase 3.3: Identical branches (15%)
        String condition = generatePartitionLocalCondition(columns);
        windowFunction = SQLite3MRUPCaseMutator.applyIdenticalBranches(
            windowFunction, condition);
        logger.logCaseMutation("Identical Branches", windowFunction);
        
    } else {
        // Phase 3.4: NULL handling (10%)
        SQLite3Column nullableCol = findNullableColumn(columns);
        if (nullableCol != null) {
            windowFunction = SQLite3MRUPCaseMutator.applyNullHandling(
                windowFunction, nullableCol.getName());
            logger.logCaseMutation("NULL Handling", windowFunction);
        }
    }
}
```

### 3. Update Logger

```java
// In SQLite3MRUPTestCaseLogger.java

public void logCaseMutation(String mutationType, String casedWindowFunction) {
    logBuffer.append("\n🔄 CASE Mutation Applied:\n");
    logBuffer.append("   Type: ").append(mutationType).append("\n");
    logBuffer.append("   Result: ").append(casedWindowFunction).append("\n");
}
```

---

## Comparator Considerations

### Type Handling

CASE mutations may change result types:
- Window function: `INTEGER` or `REAL`
- CASE with NULL: `NULL` type
- CASE with strings: `TEXT` type

**Solution:** Already handled by existing `compareValue()` method - it's type-aware.

### NULL Handling

Constant conditions like `WHEN TRUE THEN NULL` will produce all NULL results.

**Solution:** Existing comparator handles NULL correctly.

### String Results

Some CASE mutations return strings ('TOP', 'OTHER', etc.)

**Solution:** Existing comparator handles TEXT type.

**No comparator changes needed!** ✅

---

## Testing Strategy

### Phase 3.0 Testing (Constant Conditions)
- Run 500 test cases
- Focus on: `WHEN TRUE THEN NULL ELSE wf`
- Verify: No crashes, correct NULL handling
- Expected: Optimizer bugs with dead branches

### Phase 3.1 Testing (Window Function in WHEN)
- Run 500 test cases
- Focus on: `WHEN wf <= 3 THEN ... ELSE ...`
- Verify: Correct evaluation order
- Expected: Evaluation order bugs

### Phase 3.2 Testing (Different Window Functions)
- Run 1000 test cases
- Focus on: Different functions per branch
- Verify: Type handling across branches
- Expected: Type inference bugs, optimizer bugs

### Phase 3.3 Testing (Identical Branches)
- Run 300 test cases
- Focus on: `THEN x ELSE x`
- Verify: Optimizer handles redundancy
- Expected: Optimization bugs

### Phase 3.4 Testing (NULL Handling)
- Run 200 test cases
- Focus on: NULL in conditions and branches
- Verify: NULL propagation
- Expected: NULL handling bugs

**Total:** 2500 test cases for Phase 3

---

## Success Metrics

Phase 3 is successful if:
- ✅ Zero false positives across 2500+ test cases
- ✅ At least 3 unique bugs found (targeting MySQL-like bugs)
- ✅ Constant condition mutations work correctly
- ✅ Window function in WHEN condition works
- ✅ Execution speed remains > 70 queries/s
- ✅ All CASE mutations logged correctly

---

## Risk Assessment

**Risk Level:** MEDIUM-HIGH

**Risks:**
1. ❌ **Type mismatch** → Mitigated by existing type-aware comparator
2. ❌ **NULL handling** → Mitigated by existing NULL comparison
3. ❌ **Constant TRUE/FALSE** → May produce all NULL/same results (expected)
4. ❌ **Performance** → CASE adds overhead (acceptable)

**Mitigation:**
- Each strategy can be toggled independently
- Start with Phase 3.0 (constant conditions) - highest impact
- If issues arise, disable that strategy only

---

## Implementation Order

### Week 1: Phase 3.0 (Constant Conditions)
1. Create `SQLite3MRUPCaseMutator.java`
2. Implement `applyConstantCondition()`
3. Integrate into oracle
4. Test 500 cases
5. Verify zero false positives

### Week 2: Phase 3.1 (Window Function in WHEN)
1. Implement `applyWindowFunctionCondition()`
2. Test 500 cases
3. Verify evaluation order

### Week 3: Phase 3.2 (Different Window Functions)
1. Implement `applyDifferentWindowFunctions()`
2. Test 1000 cases
3. Verify type handling

### Week 4: Phase 3.3 + 3.4 (Identical Branches + NULL)
1. Implement remaining strategies
2. Test 500 cases
3. Full integration test with 2500 cases

---

## Expected Bug-Finding Impact

**VERY HIGH** - Based on MySQL bug evidence:

1. **Constant Conditions** (Phase 3.0)
   - **Impact:** ⭐⭐⭐⭐⭐
   - **Evidence:** Found real MySQL bug
   - **Target:** Dead branch elimination, constant folding

2. **Window Function in WHEN** (Phase 3.1)
   - **Impact:** ⭐⭐⭐⭐⭐
   - **Evidence:** From MySQL bug pattern
   - **Target:** Evaluation order, caching

3. **Different Window Functions** (Phase 3.2)
   - **Impact:** ⭐⭐⭐⭐
   - **Target:** Optimizer, type inference

4. **Identical Branches** (Phase 3.3)
   - **Impact:** ⭐⭐⭐⭐
   - **Evidence:** From MySQL bug
   - **Target:** Common subexpression elimination

5. **NULL Handling** (Phase 3.4)
   - **Impact:** ⭐⭐⭐
   - **Target:** NULL propagation

---

## Conclusion

**Phase 3 is now laser-focused on proven bug patterns from MySQL!**

**Key Insight from MySQL Bug:**
- Constant conditions (`WHEN TRUE`, `WHEN FALSE`) with window functions in dead branches are **extremely effective** at finding optimizer bugs
- Window functions in WHEN conditions expose evaluation order bugs
- Identical THEN/ELSE branches test common subexpression elimination

**Recommendation:**
1. Start with Phase 3.0 (constant conditions) - **highest priority**
2. Then Phase 3.1 (window function in WHEN)
3. Then Phase 3.2 (different window functions)
4. Finally Phase 3.3 + 3.4

This plan is **clean, focused, and evidence-based**! 🎯

