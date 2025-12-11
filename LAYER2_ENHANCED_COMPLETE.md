# Layer 2 Enhanced Analysis - Complete

## Summary

The MRUP detailed analysis tool has been **enhanced** to show complete **before/after normalization** details in Layer 2, giving you full visibility into how the oracle normalizes results for comparison.

---

## What Was Added

### Layer 2 Now Shows:

#### 1. **BEFORE NORMALIZATION** Section
Shows the original query result order as returned by SQLite:
- Expected [H(t1) ∪ H(t2)] - Original order
- Actual [H(t_union)] - Original order
- Row-by-row listing with all column values

#### 2. **NORMALIZATION ALGORITHM** Section
Step-by-step explanation of the sorting process:
- Step 1: Group rows by partition key (dept)
- Step 2: Within each partition, sort by window ORDER BY keys
- Step 3: Use wf_result as final tie-breaker

Shows the exact sorting keys in order with directions (ASC/DESC) and NULL handling (NULLS FIRST/LAST).

#### 3. **AFTER NORMALIZATION** Section
Shows the MRUP sorted order after applying the normalization algorithm:
- Expected [H(t1) ∪ H(t2)] - After MRUP normalization
- Actual [H(t_union)] - After MRUP normalization
- Row-by-row listing with all column values

#### 4. **NORMALIZATION IMPACT** Section
Analyzes the impact of normalization:
- Whether Expected result order changed (YES/NO)
- Whether Actual result order changed (YES/NO)
- Explanation of why normalization matters
- Insight into whether results were already in MRUP order

---

## Example Output

```
┌───────────────────────────────────────────────────────────────────┐
│ LAYER 2: MRUP NORMALIZATION (Semantic Sorting)                   │
└───────────────────────────────────────────────────────────────────┘

This layer normalizes results for comparison by sorting them according
to MRUP semantics, which preserves window function behavior.

🔧 MRUP Normalization Algorithm:
   1. Sort by partition key (dept)
   2. Within each partition, sort by window ORDER BY keys
   3. Use wf_result as tie-breaker for deterministic ordering

📊 Sorting Keys (in order):
   1. Partition key: dept
   2. Window ORDER BY: salary DESC NULLS LAST
   3. Window ORDER BY: age DESC NULLS FIRST
   4. Tie-breaker: wf_result

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BEFORE NORMALIZATION (Original Query Result Order)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Expected [H(t1) ∪ H(t2)] - Original order:
   Row  1: ['Engineering', '60000', '24', '84.061', '1']
   Row  2: ['Engineering', '45000', '49', '36.964', '2']
   Row  3: ['Finance', '50000', '51', '33.813', '1']
   Row  4: ['Finance', '50000', '21', '32.456', '2']
   Row  5: ['Finance', '20000', '39', '4.113', '2']
   Row  6: ['Marketing', '90000', '37', '33.31', '1']
   Row  7: ['Marketing', '30000', '28', '24.279', '2']
   Row  8: ['Sales', '80000', '26', '63.186', '1']
   Row  9: ['Sales', '75000', '39', 'NULL', '2']
   Row 10: ['Sales', '65000', '57', '40.461', '2']

Actual [H(t_union)] - Original order:
   Row  1: ['Engineering', '60000', '24', '84.061', '1']
   Row  2: ['Engineering', '45000', '49', '36.964', '2']
   Row  3: ['Finance', '50000', '51', '33.813', '1']
   Row  4: ['Finance', '50000', '21', '32.456', '2']
   Row  5: ['Finance', '20000', '39', '4.113', '2']
   Row  6: ['Marketing', '90000', '37', '33.31', '1']
   Row  7: ['Marketing', '30000', '28', '24.279', '2']
   Row  8: ['Sales', '80000', '26', '63.186', '1']
   Row  9: ['Sales', '75000', '39', 'NULL', '2']
   Row 10: ['Sales', '65000', '57', '40.461', '2']

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
APPLYING MRUP NORMALIZATION...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sorting algorithm:
   Step 1: Group rows by partition key (dept)
   Step 2: Within each partition, sort by:
           1. salary DESC NULLS LAST
           2. age DESC NULLS FIRST
   Step 3: Use wf_result as final tie-breaker

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
AFTER NORMALIZATION (MRUP Sorted Order)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Expected [H(t1) ∪ H(t2)] - After MRUP normalization:
   Row  1: ['Engineering', '60000', '24', '84.061', '1']
   Row  2: ['Engineering', '45000', '49', '36.964', '2']
   Row  3: ['Finance', '50000', '51', '33.813', '1']
   Row  4: ['Finance', '50000', '21', '32.456', '2']
   Row  5: ['Finance', '20000', '39', '4.113', '2']
   Row  6: ['Marketing', '90000', '37', '33.31', '1']
   Row  7: ['Marketing', '30000', '28', '24.279', '2']
   Row  8: ['Sales', '80000', '26', '63.186', '1']
   Row  9: ['Sales', '75000', '39', 'NULL', '2']
   Row 10: ['Sales', '65000', '57', '40.461', '2']

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NORMALIZATION IMPACT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   Expected result order changed: NO (already in MRUP order)
   Actual result order changed:   NO (already in MRUP order)

   💡 Results were already in MRUP order!
      SQLite happened to return results in the same order as the
      MRUP normalization would produce.

   Result: ✓ All result sets normalized and ready for comparison
```

---

## What You Can See

### Before Normalization
- **Original query results** as returned by SQLite
- Shows both Expected [H(t1) ∪ H(t2)] and Actual [H(t_union)]
- Row-by-row listing with all column values
- This is the "raw" order before any sorting

### Normalization Algorithm
- **Step-by-step explanation** of how sorting works
- **Sorting keys** in priority order:
  1. Partition key (always first)
  2. Window ORDER BY columns (with ASC/DESC and NULLS handling)
  3. Window function result (tie-breaker)
- Shows exact ORDER BY clauses from the window function

### After Normalization
- **MRUP sorted results** after applying the algorithm
- Shows both Expected and Actual in normalized order
- This is the order used for Layer 3 comparison
- Ensures semantically correct comparison

### Impact Analysis
- **Order changed?** YES/NO for both Expected and Actual
- **Why it matters**: Explanation of normalization importance
- **Insight**: Whether SQLite already returned results in MRUP order

---

## Why This Matters

### 1. Understand the Oracle
- See **exactly** how the oracle normalizes results
- Verify the sorting algorithm is **correct**
- Understand why MRUP normalization preserves window semantics

### 2. Debug False Positives
- If results don't match, see if it's a **sorting issue**
- Compare before/after to understand what **changed**
- Identify if the problem is in normalization or actual results

### 3. Verify Correctness
- Make sure the oracle is **implemented correctly**
- Ensure sorting respects PARTITION BY and ORDER BY
- Confirm tie-breaker logic works as expected

### 4. Learn MRUP
- See **concrete examples** of MRUP normalization
- Understand how window function semantics are preserved
- Learn why naive sorting doesn't work

---

## Key Insights

### Case 1: Order Already Correct
In the example above:
- Both Expected and Actual were **already in MRUP order**
- SQLite returned results in the correct order naturally
- **No reordering was needed**
- This shows the oracle is working correctly!

### Case 2: Order Changed (Example)
In other cases you might see:
```
Expected result order changed: YES ✓
Actual result order changed:   YES ✓

💡 Why normalization matters:
   SQLite may return results in different physical orders depending
   on how the query is executed. MRUP normalization ensures we
   compare results in a semantically consistent order that respects
   the window function's PARTITION BY and ORDER BY clauses.
```

This means:
- SQLite returned results in a **different order**
- Normalization **reordered** the rows
- Without normalization, comparison would **fail** (false positive)
- With normalization, we can **correctly compare** the results

---

## How to Use

Same workflow as before:

```bash
# 1. Generate MRUP logs
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 5 sqlite3 --oracle MRUP

# 2. Analyze any log file
python3 analyze_mrup_log_detailed.py mrup_logs/<log_file>

# 3. View the detailed analysis
cat <log_file>_DETAILED_ANALYSIS.txt

# 4. Look at Layer 2 section to see before/after normalization!
```

---

## Technical Details

### MRUP Normalization Algorithm

The normalization algorithm implemented in the analysis script:

1. **Group by partition key** (dept)
   - All rows with the same dept value are grouped together
   - Partitions are sorted alphabetically

2. **Sort within each partition** by window ORDER BY keys
   - For each ORDER BY column:
     - Extract the column value
     - Apply ASC/DESC direction
     - Apply NULLS FIRST/LAST handling
   - Multiple ORDER BY columns are applied in order (primary, secondary, etc.)

3. **Tie-breaker**: Window function result
   - If all ORDER BY columns are equal, use wf_result
   - Ensures deterministic ordering

### Example Sorting Keys

For window function:
```sql
COUNT(salary) OVER (
    PARTITION BY dept 
    ORDER BY salary DESC NULLS LAST, age DESC NULLS FIRST 
    ROWS 1 PRECEDING
)
```

Sorting keys (in priority order):
1. `dept` (ASC, NULLS LAST) - partition key
2. `salary` (DESC, NULLS LAST) - first ORDER BY
3. `age` (DESC, NULLS FIRST) - second ORDER BY
4. `wf_result` (ASC, NULLS LAST) - tie-breaker

---

## Benefits

### Complete Transparency
✅ See original order from SQLite  
✅ See MRUP sorted order  
✅ Side-by-side comparison of Expected vs Actual  
✅ Whether the order changed or stayed the same  
✅ Why normalization is necessary  
✅ Complete transparency into the sorting algorithm  

### Debugging Power
✅ Identify sorting issues  
✅ Verify normalization correctness  
✅ Understand false positives  
✅ Debug comparison failures  

### Learning Tool
✅ Understand MRUP normalization  
✅ See concrete examples  
✅ Learn window function semantics  
✅ Understand why naive sorting fails  

---

## Files

- **`analyze_mrup_log_detailed.py`** - Enhanced analysis script with Layer 2 before/after
- **`DETAILED_ANALYSIS_GUIDE.md`** - Complete guide for the analysis tool
- **`LAYER2_ENHANCED_COMPLETE.md`** - This document
- **`mrup_logs/*.log`** - Original MRUP log files
- **`*_DETAILED_ANALYSIS.txt`** - Generated detailed analysis files

---

## Status

✅ **LAYER 2 ENHANCED ANALYSIS COMPLETE**

You now have **COMPLETE VISIBILITY** into:
- Step 1: Table creation with SQL statements
- Step 2: Data insertion with SQL statements
- Step 3: Window function generation
- Step 4: Generated queries
- Step 5: 3-layer comparison
  - **Layer 1**: Cardinality check with detailed counts
  - **Layer 2**: MRUP normalization with **before/after** comparison ⭐ NEW
  - **Layer 3**: Per-partition comparison with row-by-row matching

---

## Next Steps

The MRUP Oracle detailed analysis is now **complete** with full transparency into every step, including the critical MRUP normalization process in Layer 2.

You can:
1. ✅ Generate logs and analyze them
2. ✅ See exact SQL statements for reproduction
3. ✅ Understand how normalization works
4. ✅ Verify oracle correctness
5. ✅ Debug any issues
6. ✅ Share test cases with others

**Everything is working perfectly!** 🎉

