# CRITICAL FIX: PARTITION BY is MANDATORY for MRUP

## 🔴 The Problem

Without `PARTITION BY`, the MRUP oracle **fundamentally does not work**. Here's why:

### Mathematical Explanation

The MRUP metamorphic relation states:

```
H(e_union) = H(e1) ∪ H(e2)
```

Where `H()` is a window function. This relation **only holds** when the window function operates on **disjoint partitions**.

### Concrete Example

Given:
- `e1 = {Finance: 3 rows}`
- `e2 = {Sales: 2 rows}`

#### WITHOUT PARTITION BY (BROKEN):

```sql
-- Query on e_union
SELECT ROW_NUMBER() OVER (ORDER BY salary) FROM e_union
Result: [1, 2, 3, 4, 5]  -- One continuous sequence

-- Query on e1
SELECT ROW_NUMBER() OVER (ORDER BY salary) FROM e1
Result: [1, 2, 3]

-- Query on e2
SELECT ROW_NUMBER() OVER (ORDER BY salary) FROM e2
Result: [1, 2]

-- Expected (e1 ∪ e2)
[1, 2, 3, 1, 2]

-- Comparison
[1, 2, 3, 4, 5] ≠ [1, 2, 3, 1, 2]  ❌ MISMATCH!
```

The oracle would report this as a "bug", but it's actually a **false positive** caused by incorrect query generation.

#### WITH PARTITION BY dept (CORRECT):

```sql
-- Query on e_union
SELECT ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary) FROM e_union
Result: Finance:[1, 2, 3], Sales:[1, 2]

-- Query on e1
SELECT ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary) FROM e1
Result: Finance:[1, 2, 3]

-- Query on e2
SELECT ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary) FROM e2
Result: Sales:[1, 2]

-- Expected (e1 ∪ e2)
Finance:[1, 2, 3], Sales:[1, 2]

-- Comparison
Finance:[1, 2, 3], Sales:[1, 2] = Finance:[1, 2, 3], Sales:[1, 2]  ✅ MATCH!
```

Now the metamorphic relation holds correctly.

---

## ✅ The Fix

### Code Change

**BEFORE (WRONG):**
```java
// Optional: PARTITION BY (70% chance)
if (Randomly.getBoolean() && Randomly.getBoolean()) {
    SQLite3Column deptColumn = findColumnByName(columns, "dept");
    if (deptColumn != null) {
        sb.append("PARTITION BY ");
        sb.append(deptColumn.getName());
        sb.append(" ");
    }
}
```

**AFTER (CORRECT):**
```java
// C0: PARTITION BY - MANDATORY for MRUP to work correctly!
// Without PARTITION BY, window function treats entire e_union as one partition,
// which is different from e1 + e2 (breaks MRUP metamorphic relation)
SQLite3Column deptColumn = findColumnByName(columns, "dept");
if (deptColumn == null) {
    throw new IgnoreMeException(); // Skip if dept column not found
}
sb.append("PARTITION BY ");
sb.append(deptColumn.getName());
sb.append(" ");
```

### Impact

- **Before:** 25% of queries had `PARTITION BY` → 75% false positives
- **After:** 100% of queries have `PARTITION BY` → 0% false positives from this issue

---

## 📊 Verification

### Test Results

Ran 15 queries and verified all have `PARTITION BY dept`:

```
1.  OVER (PARTITION BY dept ORDER BY salary DESC)
2.  OVER (PARTITION BY dept ORDER BY salary)
3.  OVER (PARTITION BY dept ORDER BY salary DESC)
4.  OVER (PARTITION BY dept ORDER BY salary NULLS FIRST ROWS 2 PRECEDING)
5.  OVER (PARTITION BY dept ORDER BY salary)
6.  OVER (PARTITION BY dept ORDER BY salary NULLS FIRST)
7.  OVER (PARTITION BY dept ORDER BY salary NULLS LAST RANGE ...)
8.  OVER (PARTITION BY dept ORDER BY salary NULLS LAST, age ASC ROWS ...)
9.  OVER (PARTITION BY dept ORDER BY salary ASC)
10. OVER (PARTITION BY dept ORDER BY salary DESC, age ASC NULLS LAST)
11. OVER (PARTITION BY dept ORDER BY salary DESC NULLS FIRST ROWS ...)
12. OVER (PARTITION BY dept ORDER BY salary ASC NULLS FIRST RANGE ...)
13. OVER (PARTITION BY dept ORDER BY salary ASC RANGE ...)
14. OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST, age ASC ...)
15. OVER (PARTITION BY dept ORDER BY salary NULLS FIRST)
```

**Result:** 15/15 (100%) ✅

---

## 🎯 Why This is Critical

This is not just an optimization or improvement—it's a **correctness requirement** for the MRUP oracle.

### Without this fix:
- **False positive rate:** ~75% (from missing PARTITION BY alone)
- **Oracle validity:** Broken (metamorphic relation doesn't hold)
- **Bug detection:** Impossible (too many false positives)

### With this fix:
- **False positive rate:** Reduced by ~75%
- **Oracle validity:** Correct (metamorphic relation holds)
- **Bug detection:** Possible (real bugs can be found)

---

## 📝 Related Constraints

This fix is part of the **Phase 2: Window Function Generation** constraints:

- **C0:** PARTITION BY is MANDATORY ← **This fix**
- **C1:** PARTITION BY must use only 'dept' column
- **C2:** ORDER BY must use only 'salary' or 'age'
- **C3:** No FRAME for ranking functions
- **C4:** RANGE only with single ORDER BY column
- **C5:** Only deterministic functions

All six constraints (C0-C5) are now enforced in the implementation.

---

## ✅ Status

**Fixed in:** Phase 2 (Window Function Generation)  
**Verified:** 100% of generated queries have PARTITION BY dept  
**Impact:** ~75% reduction in false positives  
**Criticality:** CRITICAL (oracle doesn't work without this)

---

**Date:** December 8, 2025  
**File Modified:** `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`  
**Lines Changed:** ~10 lines in `generateWindowSpecOSRB()` method

