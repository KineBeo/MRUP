# Phase 1 Mutations - Complete ✅

## Summary

Phase 1 mutations implemented to increase query complexity while preserving MRUP semantics.

## Changes Made

### M1.1: Multiple ORDER BY Columns with Mixed Directions

**Before:**
```sql
ORDER BY salary DESC
```

**After:**
```sql
ORDER BY salary DESC NULLS FIRST, age DESC NULLS LAST
```

**Implementation:**
- Increased max ORDER BY columns from 2 → 3
- Always add ASC/DESC (was optional)
- Always add NULLS FIRST/LAST (was optional)

**Example from logs:**
```sql
ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC NULLS FIRST, age DESC NULLS LAST)
```

### M1.2: Complex Frame Specifications

**Before:**
```sql
ROWS 1 PRECEDING
ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
```

**After:**
```sql
ROWS 3 PRECEDING
ROWS BETWEEN 3 PRECEDING AND 2 FOLLOWING
ROWS BETWEEN UNBOUNDED PRECEDING AND 1 FOLLOWING
```

**Implementation:**
- Added "3 PRECEDING" option
- Added "2 FOLLOWING" option for BETWEEN frames
- More complex boundary combinations

**Example from logs:**
```sql
AVG(c1) OVER (PARTITION BY dept ORDER BY salary DESC NULLS LAST 
              ROWS BETWEEN 2 PRECEDING AND 1 FOLLOWING EXCLUDE TIES)
```

## Bug Potential

Phase 1 mutations target:
- ✅ Incorrect NULL handling with mixed NULLS FIRST/LAST
- ✅ Optimizer bugs with complex sort keys (3 columns)
- ✅ Frame calculation errors with multiple ORDER BY columns
- ✅ Frame boundary calculation with BETWEEN X PRECEDING AND Y FOLLOWING
- ✅ UNBOUNDED with FOLLOWING edge cases

## Verification

```bash
# Test run shows Phase 1 working:
✓ Multiple ORDER BY columns: salary DESC NULLS FIRST, age DESC NULLS LAST
✓ Complex frames: BETWEEN 2 PRECEDING AND 1 FOLLOWING
✓ All constraints still validated
✓ No crashes or compilation errors
```

## Code Changes

**File:** `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`

1. **generateWindowSpecOSRB()** - Lines ~289-323
   - Changed: `int numOrderCols = Randomly.fromOptions(1, 2, 3)`
   - Changed: Always add ASC/DESC and NULLS FIRST/LAST

2. **generateFrameClause()** - Lines ~362-411
   - Added: "3 PRECEDING" option
   - Added: "2 FOLLOWING" option

## Status

✅ **Phase 1 Complete**

- M1.1: Multiple ORDER BY columns ✅
- M1.2: Complex frame specifications ✅
- M1.3: FILTER clause ⏸️ (Skipped - requires function-level changes)

## Next Steps

**Phase 2:** Window Function Result Mutations
- M2.1: Arithmetic on window results
- M2.2: Type casting
- M2.3: NULL handling

See `MUTATION_ROADMAP.md` for details.

