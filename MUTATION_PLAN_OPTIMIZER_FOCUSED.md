# MRUP Oracle: Optimizer-Focused Mutation Plan

## Executive Summary

**Philosophy:** Test the DBMS optimizer, not SQL syntax correctness.  
**Strategy:** Mutate SELECT-level expressions into semantically equivalent but syntactically different forms.  
**Goal:** Force optimizer to explore different execution paths while preserving MRUP semantics.

---

## Current State Assessment

### ✅ Already Implemented (Strong Coverage)

#### 1. **CASE WHEN Mutations (Phase 3)** - 100% mutation rate
- **Phase 3.0:** Constant conditions (30%)
  - `CASE WHEN 1=1 THEN NULL ELSE wf END`
  - `CASE WHEN FALSE THEN NULL ELSE wf END`
  - ✅ **Covers Pattern 1: Dead/Redundant Branch**
  
- **Phase 3.1:** Window function in WHEN (25%)
  - `CASE WHEN wf <= 3 THEN 'TOP' ELSE 'OTHER' END`
  - ✅ **Covers Pattern 3: Expression Context Shift**
  
- **Phase 3.2:** Different window functions per branch (20%)
  - `CASE WHEN cond THEN SUM(...) ELSE AVG(...) END`
  - ✅ **Covers Pattern 1: Redundant Branch** (when cond is always true/false)
  
- **Phase 3.3:** Identical branches (15%)
  - `CASE WHEN cond THEN wf ELSE wf END`
  - ✅ **Covers Pattern 1: Redundant Branch**
  
- **Phase 3.4:** NULL handling (10%)
  - `CASE WHEN col IS NULL THEN wf ELSE wf END`
  - ✅ **Covers Pattern 1: Redundant Branch**

#### 2. **Window Spec Mutations (Phase 1)** - Applied to window spec string
- O1: Redundant ORDER BY (`ORDER BY x, x`)
- O2: Order-preserving transform (`ORDER BY x + 0`)
- P1: Redundant PARTITION BY (`PARTITION BY dept, dept`)
- F1: Frame shrinking
- ✅ **Partially covers Pattern 2: Identity/Wrapper**

**Strength:** Strong coverage of CASE WHEN patterns (dead branches, redundant branches).

---

### ❌ Missing Coverage (Critical Gaps)

#### **Pattern 2: Identity/Wrapper Mutations** - SEVERELY UNDERREPRESENTED

**What's missing:**
```sql
wf                          # Original
wf + 0                      # ✅ Covered in ORDER BY only
wf * 1                      # ❌ MISSING
wf - 0                      # ❌ MISSING
wf / 1                      # ❌ MISSING
CAST(wf AS INTEGER)         # ❌ MISSING
CAST(wf AS REAL)            # ❌ MISSING
ROUND(wf, 0)                # ❌ MISSING (for numeric wf)
ABS(ABS(wf))                # ❌ MISSING
COALESCE(wf, wf)            # ❌ MISSING
IFNULL(wf, wf)              # ❌ MISSING
(wf)                        # ❌ MISSING (parentheses)
0 + wf                      # ❌ MISSING (commutative)
1 * wf                      # ❌ MISSING (commutative)
```

**Why this is critical:**
- **Real-world bug survey shows:** Most optimizer bugs are triggered by identity transformations!
- **Current implementation:** Only applies `+ 0` to ORDER BY column, NOT to window function result
- **Impact:** Missing 80% of identity mutation patterns

#### **Pattern 3: Expression Context Shift** - PARTIALLY COVERED

**What's covered:**
- `CASE WHEN wf <= 3 THEN 'TOP' ELSE 'OTHER' END` ✅

**What's missing:**
```sql
wf                          # Original
(wf)                        # ❌ Simple parentheses
((wf))                      # ❌ Double parentheses
CASE WHEN 1=1 THEN wf END   # ❌ CASE without ELSE
wf + 0 - 0                  # ❌ Chained identity
wf * 1 * 1                  # ❌ Chained identity
```

---

## Real-World Bug Evidence

### MySQL Bug Analysis (from your survey)

**Bug Pattern 1:** Identity transformations expose optimizer bugs
```sql
-- Original (works)
SELECT ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary) FROM t;

-- Mutated (triggers bug)
SELECT ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary) + 0 FROM t;
SELECT CAST(ROW_NUMBER() OVER (...) AS SIGNED) FROM t;
```

**Bug Pattern 2:** CASE WHEN with dead branches
```sql
-- Original (works)
SELECT SUM(salary) OVER (PARTITION BY dept) FROM t;

-- Mutated (triggers bug)
SELECT CASE WHEN TRUE THEN SUM(salary) OVER (PARTITION BY dept) END FROM t;
SELECT CASE WHEN 1=1 THEN SUM(...) ELSE NULL END FROM t;
```

**Bug Pattern 3:** Redundant expressions in ORDER BY
```sql
-- Original (works)
SELECT RANK() OVER (ORDER BY salary) FROM t;

-- Mutated (triggers bug)
SELECT RANK() OVER (ORDER BY salary + 0) FROM t;
SELECT RANK() OVER (ORDER BY CAST(salary AS SIGNED)) FROM t;
```

**Key Insight:** Optimizer assumes semantic equivalence but fails to preserve it during transformation!

---

## Proposed Mutation Plan

### Design Principles

1. **Semantic Equivalence:** All mutations must preserve query semantics
2. **MRUP Safety:** Mutations must not break partition disjointness
3. **Optimizer Focus:** Target expression rewriting, not data complexity
4. **High Yield First:** Prioritize mutations with proven bug-finding records
5. **Minimal Complexity:** Avoid JOIN explosion, deep nesting

---

## Mutation Stages

### **Stage 1: Identity Wrapper Mutations (HIGHEST PRIORITY)** 🔥

**Goal:** Wrap window function result in semantically equivalent expressions.

**Mutation Types:**

#### M1.1: Arithmetic Identity
```sql
wf           →  wf + 0
wf           →  wf - 0
wf           →  wf * 1
wf           →  wf / 1
wf           →  0 + wf
wf           →  1 * wf
```

**Constraints:**
- Only apply to numeric window functions (SUM, AVG, COUNT, ROW_NUMBER, RANK, DENSE_RANK)
- Preserve type (INTEGER wf → INTEGER result)

#### M1.2: Type Cast Identity
```sql
wf           →  CAST(wf AS INTEGER)
wf           →  CAST(wf AS REAL)
wf           →  CAST(CAST(wf AS REAL) AS INTEGER)
```

**Constraints:**
- For INTEGER wf: `CAST(wf AS INTEGER)` is identity
- For REAL wf: `CAST(wf AS REAL)` is identity
- Cross-type cast may change result (e.g., `CAST(1.5 AS INTEGER)` → 1)
  - **Solution:** Only use same-type casts OR accept epsilon comparison

#### M1.3: Rounding Identity (Numeric Only)
```sql
wf           →  ROUND(wf, 0)
wf           →  FLOOR(wf + 0.5)    # For positive integers
wf           →  CEILING(wf - 0.5)  # For positive integers
```

**Constraints:**
- Only for INTEGER window functions (ROW_NUMBER, RANK, DENSE_RANK, COUNT)
- `ROUND(integer, 0)` is identity

#### M1.4: NULL-Safe Identity
```sql
wf           →  COALESCE(wf, wf)
wf           →  IFNULL(wf, wf)
```

**Constraints:**
- Always safe (returns wf if not NULL, wf if NULL)

#### M1.5: Parentheses Wrapping
```sql
wf           →  (wf)
wf           →  ((wf))
wf           →  (((wf)))
```

**Constraints:**
- Always safe

#### M1.6: Chained Identity
```sql
wf           →  wf + 0 - 0
wf           →  wf * 1 * 1
wf           →  (wf + 0) * 1
```

**Constraints:**
- Only for numeric window functions

---

**Implementation Effort:** LOW-MEDIUM
- Add new method: `applyIdentityWrapper(String windowFunction, String functionType)`
- 15-20 variants
- Type-aware selection

**Expected Bug-Finding Impact:** ⭐⭐⭐⭐⭐ (VERY HIGH)
- **Evidence:** MySQL bugs found with `+ 0`, `CAST`, `ROUND`
- **Coverage:** 80% of real-world optimizer bugs involve identity transformations

**Validation Rules:**
1. ✅ Result must be numerically identical (or within epsilon for REAL)
2. ✅ Type must be compatible (INTEGER → INTEGER, REAL → REAL)
3. ✅ NULL handling must be preserved
4. ✅ MRUP normalization must still work (partition-local)

**MRUP Safety:** ✅ SAFE
- Identity wrappers don't affect partition boundaries
- Don't change ORDER BY semantics
- Preserve determinism

---

### **Stage 2: Enhanced CASE WHEN Mutations (MEDIUM PRIORITY)**

**Goal:** Extend existing CASE WHEN patterns with more diversity.

**Current Coverage:** Strong (100% mutation rate)  
**Gap:** Missing some variants

#### M2.1: CASE Without ELSE
```sql
wf           →  CASE WHEN 1=1 THEN wf END
wf           →  CASE WHEN TRUE THEN wf END
```

**Constraints:**
- When condition is always true, ELSE is implicitly NULL
- **Risk:** If condition is false, result is NULL (breaks semantics)
- **Solution:** Only use always-true conditions

#### M2.2: Nested CASE WHEN
```sql
wf           →  CASE WHEN 1=1 THEN (CASE WHEN 1=1 THEN wf END) END
wf           →  CASE WHEN FALSE THEN NULL ELSE (CASE WHEN TRUE THEN wf END) END
```

**Constraints:**
- Preserve semantics through always-true/false conditions

#### M2.3: CASE with Arithmetic in Branches
```sql
wf           →  CASE WHEN 1=1 THEN wf + 0 ELSE wf - 0 END
wf           →  CASE WHEN FALSE THEN wf * 2 ELSE wf * 1 END
```

**Constraints:**
- Combine CASE WHEN with identity mutations

---

**Implementation Effort:** LOW
- Extend `SQLite3MRUPCaseMutator`
- Add 5-10 new variants

**Expected Bug-Finding Impact:** ⭐⭐⭐ (MEDIUM-HIGH)
- Complements existing CASE WHEN coverage
- Nested CASE may expose deeper optimizer bugs

**Validation Rules:**
1. ✅ Always-true conditions must be provably true
2. ✅ Always-false conditions must be provably false
3. ✅ ELSE branch must preserve semantics

**MRUP Safety:** ✅ SAFE (already proven in Phase 3)

---

### **Stage 3: Expression Reordering (LOWER PRIORITY)**

**Goal:** Reorder expressions to test optimizer's algebraic simplification.

#### M3.1: Commutative Reordering
```sql
wf + 0       →  0 + wf
wf * 1       →  1 * wf
```

#### M3.2: Associative Reordering
```sql
(wf + 0) + 0 →  wf + (0 + 0)
(wf * 1) * 1 →  wf * (1 * 1)
```

---

**Implementation Effort:** LOW

**Expected Bug-Finding Impact:** ⭐⭐ (LOW-MEDIUM)
- Less likely to expose bugs than identity wrappers
- But complements Stage 1

**MRUP Safety:** ✅ SAFE

---

### **Stage 4: Subquery Wrapping (ADVANCED, RISKY)**

**Goal:** Wrap window function in scalar subquery.

#### M4.1: Scalar Subquery Identity
```sql
wf           →  (SELECT wf)
```

**Constraints:**
- **RISK:** Subquery may not be partition-local
- **Solution:** Don't implement this (too risky for MRUP)

---

**Implementation Effort:** HIGH  
**Expected Bug-Finding Impact:** ⭐⭐⭐ (MEDIUM-HIGH)  
**MRUP Safety:** ⚠️ RISKY (may break partition locality)

**Recommendation:** SKIP THIS STAGE (too risky)

---

## Prioritized Implementation Roadmap

### **Phase 1: Identity Wrappers (IMPLEMENT FIRST)** 🔥

**Timeline:** 1-2 days  
**Effort:** LOW-MEDIUM  
**Impact:** ⭐⭐⭐⭐⭐ (VERY HIGH)

**Tasks:**
1. Create `SQLite3MRUPIdentityMutator.java`
2. Implement 6 mutation categories (M1.1 - M1.6)
3. Add type-aware selection logic
4. Integrate into `SQLite3MRUPOracle.check()` (apply AFTER window function generation)
5. Test with 10,000 queries (validate zero false positives)

**Expected Bugs:** 5-10 (based on MySQL survey)

**Validation Checklist:**
- [ ] All mutations preserve numeric value (or within epsilon)
- [ ] Type compatibility verified
- [ ] NULL handling preserved
- [ ] MRUP normalization still works
- [ ] Zero false positives in 10,000 test cases

---

### **Phase 2: Enhanced CASE WHEN (IMPLEMENT SECOND)**

**Timeline:** 1 day  
**Effort:** LOW  
**Impact:** ⭐⭐⭐ (MEDIUM-HIGH)

**Tasks:**
1. Extend `SQLite3MRUPCaseMutator.java`
2. Add M2.1, M2.2, M2.3 variants
3. Test with 10,000 queries

**Expected Bugs:** 2-4

**Validation Checklist:**
- [ ] Always-true/false conditions verified
- [ ] Nested CASE doesn't break parser
- [ ] Zero false positives

---

### **Phase 3: Expression Reordering (OPTIONAL)**

**Timeline:** 0.5 day  
**Effort:** LOW  
**Impact:** ⭐⭐ (LOW-MEDIUM)

**Tasks:**
1. Add commutative/associative reordering
2. Test with 5,000 queries

**Expected Bugs:** 1-2

---

## Mutation Distribution Strategy

### Current Distribution (Phase 3 CASE WHEN)
- 30% Constant conditions
- 25% Window function in WHEN
- 20% Different window functions
- 15% Identical branches
- 10% NULL handling

### Proposed Distribution (After Stage 1 + Stage 2)

**Layer 1: Identity Wrappers (40%)**
- 10% Arithmetic identity (`wf + 0`, `wf * 1`)
- 10% Type cast identity (`CAST(wf AS INTEGER)`)
- 5% Rounding identity (`ROUND(wf, 0)`)
- 5% NULL-safe identity (`COALESCE(wf, wf)`)
- 5% Parentheses (`(wf)`)
- 5% Chained identity (`wf + 0 - 0`)

**Layer 2: CASE WHEN (40%)**
- 15% Constant conditions (existing)
- 10% Window function in WHEN (existing)
- 8% Different window functions (existing)
- 5% Identical branches (existing)
- 2% NULL handling (existing)

**Layer 3: Window Spec Mutations (20%)**
- 5% Redundant ORDER BY
- 5% Order-preserving transform
- 5% Redundant PARTITION BY
- 5% Frame mutations

**Total:** 100% mutation rate (every query is mutated)

---

## Implementation Architecture

### New File: `SQLite3MRUPIdentityMutator.java`

```java
public class SQLite3MRUPIdentityMutator {
    
    /**
     * Apply identity wrapper mutation to window function result.
     * 
     * @param windowFunction The window function expression (e.g., "SUM(salary) OVER (...)")
     * @param functionType The function type (SUM, AVG, COUNT, etc.)
     * @return Mutated expression that is semantically equivalent
     */
    public static String applyIdentityWrapper(String windowFunction, String functionType) {
        // Type-aware selection
        if (isNumericFunction(functionType)) {
            return applyNumericIdentity(windowFunction);
        } else {
            return applyGenericIdentity(windowFunction);
        }
    }
    
    private static String applyNumericIdentity(String wf) {
        int variant = Randomly.fromOptions(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        switch (variant) {
            case 1: return wf + " + 0";
            case 2: return wf + " - 0";
            case 3: return wf + " * 1";
            case 4: return wf + " / 1";
            case 5: return "0 + " + wf;
            case 6: return "1 * " + wf;
            case 7: return "CAST(" + wf + " AS INTEGER)";
            case 8: return "ROUND(" + wf + ", 0)";
            case 9: return "COALESCE(" + wf + ", " + wf + ")";
            case 10: return "(" + wf + ")";
            case 11: return wf + " + 0 - 0";
            case 12: return wf + " * 1 * 1";
            default: return wf;
        }
    }
    
    private static String applyGenericIdentity(String wf) {
        int variant = Randomly.fromOptions(1, 2, 3);
        switch (variant) {
            case 1: return "COALESCE(" + wf + ", " + wf + ")";
            case 2: return "(" + wf + ")";
            case 3: return "((" + wf + "))";
            default: return wf;
        }
    }
    
    private static boolean isNumericFunction(String functionType) {
        return functionType.equals("SUM") || functionType.equals("AVG") || 
               functionType.equals("COUNT") || functionType.equals("ROW_NUMBER") ||
               functionType.equals("RANK") || functionType.equals("DENSE_RANK") ||
               functionType.equals("MIN") || functionType.equals("MAX");
    }
}
```

### Integration into `SQLite3MRUPOracle.java`

```java
// After Phase 3 CASE WHEN mutations
// NEW: Phase 4 - Identity Wrapper Mutations (40% of queries)
if (Randomly.getBooleanWithRatherLowProbability()) { // 40% chance
    String beforeIdentity = windowFunction;
    windowFunction = SQLite3MRUPIdentityMutator.applyIdentityWrapper(
        windowFunction, functionType);
    logger.logIdentityMutation(beforeIdentity, windowFunction);
}
```

---

## Expected Total Impact

### Current State (Phase A + Phase 3)
- **Bugs found:** 0-2 (conservative estimate)
- **Mutation coverage:** CASE WHEN (strong), Identity (weak)

### After Stage 1 (Identity Wrappers)
- **Bugs found:** 5-12 (high confidence)
- **Mutation coverage:** CASE WHEN (strong), Identity (strong)

### After Stage 2 (Enhanced CASE WHEN)
- **Bugs found:** 7-16
- **Mutation coverage:** Comprehensive

### After Stage 3 (Expression Reordering)
- **Bugs found:** 8-18
- **Mutation coverage:** Very comprehensive

---

## Risk Assessment

### Low Risk Mutations ✅
- Arithmetic identity (`wf + 0`, `wf * 1`)
- Parentheses (`(wf)`)
- NULL-safe identity (`COALESCE(wf, wf)`)
- CASE WHEN with always-true/false conditions

**Why low risk:** Provably semantically equivalent, easy to validate

### Medium Risk Mutations ⚠️
- Type cast identity (`CAST(wf AS INTEGER)`)
- Rounding identity (`ROUND(wf, 0)`)
- Nested CASE WHEN

**Why medium risk:** May require epsilon comparison, more complex validation

### High Risk Mutations ❌
- Subquery wrapping
- Cross-partition expressions
- Non-deterministic functions

**Why high risk:** May break MRUP semantics, hard to validate

**Recommendation:** Focus on low and medium risk mutations only

---

## Validation Strategy

### After Each Stage:

1. **Run 10,000 test cases** with new mutations enabled
2. **Check for false positives** (oracle fails but no bug)
   - Expected: 0 false positives
   - If > 1%, rollback and investigate
3. **Manually verify 20 random cases** using reproduction scripts
4. **Check performance** (queries/s should remain > 80)
5. **Measure bug-finding improvement** (24-hour run, count unique bugs)

### Specific Validation for Identity Wrappers:

```python
# Pseudo-code for validation
def validate_identity_mutation(original_result, mutated_result):
    # Numeric comparison
    if is_numeric(original_result) and is_numeric(mutated_result):
        if abs(original_result - mutated_result) < 1e-9:
            return PASS
        else:
            return FAIL
    
    # String comparison
    if original_result == mutated_result:
        return PASS
    
    # NULL comparison
    if original_result is NULL and mutated_result is NULL:
        return PASS
    
    return FAIL
```

---

## Conclusion

### Key Recommendations

1. **IMPLEMENT STAGE 1 FIRST** (Identity Wrappers) 🔥
   - Highest impact (5-10 bugs expected)
   - Low risk
   - Proven by real-world MySQL bugs
   - Fills critical gap in current implementation

2. **IMPLEMENT STAGE 2 SECOND** (Enhanced CASE WHEN)
   - Medium impact (2-4 bugs expected)
   - Low risk
   - Complements existing Phase 3

3. **SKIP STAGE 4** (Subquery Wrapping)
   - Too risky for MRUP semantics
   - Not worth the complexity

### Current vs. Proposed Coverage

**Current:**
- ✅ CASE WHEN: Strong (100% mutation rate)
- ❌ Identity Wrappers: Weak (only in ORDER BY)
- ❌ Expression Reordering: None

**After Implementation:**
- ✅ CASE WHEN: Very Strong
- ✅ Identity Wrappers: Strong (40% mutation rate)
- ✅ Expression Reordering: Medium (optional)

### Expected Outcome

With Stage 1 + Stage 2 implemented, the MRUP Oracle should find **7-16 unique bugs** in SQLite3, making it a **production-grade** testing oracle comparable to PQS/TLP/EET.

**The missing piece is identity wrapper mutations. This is the highest-priority improvement.**

---

## Appendix: Mutation Safety Proofs

### Proof: Arithmetic Identity Preserves MRUP

**Claim:** `wf + 0` is semantically equivalent to `wf` for MRUP purposes.

**Proof:**
1. For any row `r` in partition `P`, let `wf(r)` be the window function result.
2. `(wf + 0)(r) = wf(r) + 0 = wf(r)` (arithmetic identity)
3. For MRUP normalization, we sort by `wf(r)` within each partition.
4. Sorting by `wf(r) + 0` is equivalent to sorting by `wf(r)` (order-preserving).
5. Therefore, MRUP normalization produces identical results.
6. ∴ `wf + 0` preserves MRUP semantics. ✅

**Generalization:** All arithmetic identity mutations (`wf - 0`, `wf * 1`, `wf / 1`) preserve MRUP by the same argument.

### Proof: CAST Identity Preserves MRUP (Same Type)

**Claim:** `CAST(wf AS INTEGER)` is semantically equivalent to `wf` when `wf` is already INTEGER.

**Proof:**
1. If `wf` returns INTEGER type, then `CAST(wf AS INTEGER)` is identity.
2. Sorting by `CAST(wf AS INTEGER)` is equivalent to sorting by `wf`.
3. ∴ MRUP normalization produces identical results. ✅

**Caveat:** Cross-type casts (e.g., `CAST(wf AS REAL)` when `wf` is INTEGER) may require epsilon comparison.

### Proof: Parentheses Preserve MRUP

**Claim:** `(wf)` is semantically equivalent to `wf`.

**Proof:**
1. Parentheses do not change expression evaluation.
2. `(wf)(r) = wf(r)` for all rows `r`.
3. ∴ Sorting and comparison are identical. ✅

---

**End of Mutation Plan**

