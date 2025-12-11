# NULL Handling Bug Fix

## Date: December 10, 2025

## Issue Discovered

User discovered a critical mismatch between MRUP log results and manually reproduced SQL script results.

### Test Case
- **Log File**: `mrup_logs/mrup_20251210_080452_712.log`
- **Window Function**: `AVG(c0) OVER (PARTITION BY dept ORDER BY salary NULLS LAST)`

### The Mismatch

**Expected (from MRUP log - H(t1) Row 3)**:
```
Engineering, 80000, 45, NULL, B, -63148.0
```

**Actual (from reproduction SQL script)**:
```
Engineering, 80000, 45, <NULL>, B, -42098.6666666667
```

The `wf_result` values are **different**: `-63148.0` vs `-42098.6666666667`

---

## Root Cause Analysis

### The Bug

In `log_to_sql.py`, the `format_value()` function was incorrectly handling NULL values:

```python
# BEFORE (INCORRECT):
def format_value(val):
    if val.upper() == 'NULL' or val == '':
        return 'NULL'
    try:
        int(val)
        return val
    except ValueError:
        return f"'{val}'"  # ← BUG: This quotes '<NULL>' as a string!
```

When the log file contained `<NULL>` (with angle brackets for display), the function would:
1. Check if `val.upper() == 'NULL'` → **False** (because it's `<NULL>`, not `NULL`)
2. Try to parse as integer → **Fails** (it's a string)
3. Quote it as a string → Returns `'<NULL>'`

This resulted in the SQL:
```sql
INSERT INTO t1 VALUES ('Engineerin', 80000, 45, '<NULL>', 'B');
                                                  ^^^^^^^^
                                                  String literal, not NULL!
```

### Why This Causes Wrong Results

In SQLite3:
- **`NULL`**: A special value meaning "no value"
  - Excluded from aggregate functions like `AVG()`
  - `AVG(10, 20, NULL, 30)` = `(10 + 20 + 30) / 3` = `20.0`

- **`'<NULL>'` string**: A text value
  - When used in integer context, SQLite converts it to `0`
  - `AVG(10, 20, 0, 30)` = `(10 + 20 + 0 + 30) / 4` = `15.0`

### Verification

```sql
-- Test 1: With real NULL (correct)
CREATE TABLE test (val INT);
INSERT INTO test VALUES (10), (20), (NULL), (30);
SELECT AVG(val) FROM test;
-- Result: 20.0 (excludes NULL)

-- Test 2: With '<NULL>' string (incorrect)
CREATE TABLE test2 (val INT);
INSERT INTO test2 VALUES (10), (20), ('<NULL>'), (30);
SELECT AVG(val) FROM test2;
-- Result: 15.0 (includes 0)

-- What SQLite sees:
SELECT CAST('<NULL>' AS INTEGER);
-- Result: 0
```

### Impact on Window Function

For the Engineering partition:
```
Row 1: c0 = -661220
Row 2: c0 = 534924
Row 3: c0 = NULL (or '<NULL>' string)
Row 4: c0 = -340291
```

**With real NULL (correct)**:
```
Row 1: AVG(-661220) = -661220.0
Row 2: AVG(-661220, 534924) = -63148.0
Row 3: AVG(-661220, 534924) = -63148.0  ← NULL excluded
Row 4: AVG(-661220, 534924, -340291) = -155529.0
```

**With '<NULL>' string (incorrect)**:
```
Row 1: AVG(-661220) = -661220.0
Row 2: AVG(-661220, 534924) = -63148.0
Row 3: AVG(-661220, 534924, 0) = -42098.67  ← 0 included!
Row 4: AVG(-661220, 534924, 0, -340291) = -116646.75
```

---

## The Fix

### Updated `format_value()` Function

```python
# AFTER (CORRECT):
def format_value(val):
    """Format a value for SQL INSERT."""
    # Check for NULL representation (case-insensitive, with or without angle brackets)
    if val.upper() == 'NULL' or val == '' or val == '<NULL>':
        return 'NULL'
    # Try to parse as number (integer or float)
    try:
        int(val)
        return val
    except ValueError:
        try:
            float(val)
            return val
        except ValueError:
            # It's a string, quote it (but escape single quotes)
            escaped_val = val.replace("'", "''")
            return f"'{escaped_val}'"
```

### Key Changes

1. **Explicit `<NULL>` check**: Added `val == '<NULL>'` to catch display-formatted NULLs
2. **Float support**: Added float parsing for numeric values with decimals
3. **String escaping**: Added proper SQL string escaping for single quotes

---

## Verification After Fix

### Generated SQL (Fixed)
```sql
INSERT INTO t1 VALUES ('Engineerin', 80000, 45, NULL, 'B');
                                                ^^^^
                                                Correct SQL NULL!
```

### Results After Fix
```
--- Q1: Window function on t1 ---
dept        salary  age  c0       c1     wf_result        
----------  ------  ---  -------  -----  -----------------
Engineerin  50000   55   -661220  Value  -661220.0        
Engineerin  55000   34   534924   B      -63148.0         
Engineerin  80000   45   <NULL>   B      -63148.0         ← MATCHES LOG!
Engineerin  85000   45   -340291  Test   -155529.0        
Finance     25000   57   -929134  Value  -929134.0        
Finance     80000   23   -697364  Test   -813249.0        
Finance     95000   64   -410884  Value  -679127.333333333
```

**Row 3 now shows `-63148.0`** ✅ which matches the MRUP log file!

---

## Lessons Learned

### 1. NULL Representation Matters
- **Display format**: `<NULL>` (for human readability)
- **SQL format**: `NULL` (for database operations)
- **Must convert correctly** between the two!

### 2. String vs NULL in SQL
- Strings like `'<NULL>'`, `'null'`, `'NULL'` are **NOT** the same as SQL `NULL`
- SQLite will coerce strings to numbers (often 0) in numeric contexts
- This silently changes aggregate function results!

### 3. Testing with NULL Values
- Always test with NULL values in different column types
- Verify that NULLs are excluded from aggregates
- Check that NULL ordering works correctly (`NULLS FIRST`/`NULLS LAST`)

### 4. Log Parsing Complexity
- Log files format values for display (e.g., `<NULL>`)
- Must parse these display formats back to SQL syntax
- Edge cases matter: `<NULL>`, `NULL`, `null`, empty strings, etc.

---

## Impact Assessment

### Before Fix
- ❌ NULL values incorrectly converted to strings
- ❌ Aggregate functions included 0 instead of excluding NULL
- ❌ Window function results didn't match MRUP Oracle
- ❌ False positives or false negatives possible

### After Fix
- ✅ NULL values correctly preserved as SQL NULL
- ✅ Aggregate functions properly exclude NULL
- ✅ Window function results match MRUP Oracle exactly
- ✅ Accurate manual verification possible

---

## Testing

### Test Case 1: Integer NULL
```sql
CREATE TABLE t (val INT);
INSERT INTO t VALUES (10), (NULL), (20);
SELECT AVG(val) FROM t;
-- Expected: 15.0
-- Result: 15.0 ✓
```

### Test Case 2: Text NULL
```sql
CREATE TABLE t (val TEXT);
INSERT INTO t VALUES ('A'), (NULL), ('B');
SELECT COUNT(*), COUNT(val) FROM t;
-- Expected: 3, 2
-- Result: 3, 2 ✓
```

### Test Case 3: Window Function with NULL
```sql
CREATE TABLE t (grp TEXT, val INT);
INSERT INTO t VALUES ('A', 10), ('A', NULL), ('A', 20);
SELECT grp, val, AVG(val) OVER (PARTITION BY grp ORDER BY val) 
FROM t;
-- Expected: NULL excluded from running average
-- Result: Correct ✓
```

---

## Additional Improvements

### 1. Float Support
The fix also added proper float/decimal number support:
```python
try:
    float(val)
    return val
except ValueError:
    # It's a string
```

### 2. String Escaping
Added proper SQL string escaping for single quotes:
```python
escaped_val = val.replace("'", "''")
return f"'{escaped_val}'"
```

Example:
- Input: `O'Brien`
- Output: `'O''Brien'` (correctly escaped for SQL)

---

## Conclusion

This was a **critical bug** that would have caused:
1. Incorrect manual verification results
2. Confusion about whether MRUP Oracle was working correctly
3. Potential false positives/negatives in bug detection

The fix ensures that:
- ✅ NULL values are handled correctly throughout the conversion process
- ✅ Manual verification results match MRUP Oracle results exactly
- ✅ The reproduction system is accurate and reliable

**Status**: ✅ BUG FIXED AND VERIFIED

---

## Files Modified

- `log_to_sql.py`: Updated `format_value()` function

## Files Created

- `NULL_HANDLING_BUG_FIX.md`: This documentation

---

**Thank you to the user for discovering this critical issue through careful manual verification!** 🙏

This demonstrates the importance of:
1. Manual verification of automated systems
2. Testing with edge cases (NULL values)
3. Comparing expected vs. actual results carefully

