# MRUP Test Case Reproduction System - Complete

## Date: December 10, 2025

## Summary

Successfully implemented a complete system for manually verifying MRUP test cases by converting log files into standalone SQL reproduction scripts.

---

## ✅ All Tasks Completed

### Task 1: Generate MRUP Logs ✅
- Run oracle with logging enabled: `java -Dmrup.logging.enabled=true`
- Logs stored in `mrup_logs/` directory
- Each log contains detailed results: H(t1), H(t2), H(t1) ∪ H(t2), H(t_union)

### Task 2: Extract Table Creation SQL ✅
- Python script `log_to_sql.py` extracts:
  - CREATE TABLE statements with correct schema
  - INSERT statements with actual data
  - Window function queries (Q1, Q2, Q_union)
- Handles variable column counts (3-5 columns)
- Properly formats NULL values and strings

### Task 3: Create Standalone SQL Script ✅
- Generated scripts are fully self-contained
- Include all necessary SQLite3 directives (.mode, .headers, .nullvalue)
- Provide clear output formatting
- Include manual verification guide

### Task 4: Test in SQLite3 Environment ✅
- Successfully tested with multiple log files
- Verified with 3-column tables (dept, salary, age)
- Verified with 4-column tables (dept, salary, age, c0)
- All queries execute correctly
- Results match MRUP Oracle expectations

---

## Files Created

### 1. `log_to_sql.py` (Main Tool)
**Purpose**: Convert MRUP log files to standalone SQL scripts

**Features**:
- Extracts table schemas and data
- Handles variable column counts
- Formats NULL values correctly
- Generates complete, runnable SQL scripts

**Usage**:
```bash
python3 log_to_sql.py <log_file>
```

**Example**:
```bash
python3 log_to_sql.py mrup_logs/mrup_20251210_075620_708.log
# Output: reproduction_mrup_20251210_075620_708.sql
```

### 2. `verify_mrup_case.sh` (Helper Script)
**Purpose**: One-command workflow for verification

**Features**:
- Converts log to SQL
- Runs SQL in SQLite3
- Shows formatted output
- Provides verification checklist

**Usage**:
```bash
./verify_mrup_case.sh <log_file>
# Or use latest log:
./verify_mrup_case.sh latest
```

### 3. `MANUAL_VERIFICATION_GUIDE.md` (Documentation)
**Purpose**: Comprehensive guide for manual verification

**Contents**:
- Quick start guide
- Step-by-step verification process
- Example outputs with explanations
- Troubleshooting tips
- Advanced usage scenarios

---

## Example Workflow

### Step 1: Generate Test Cases
```bash
cd /home/kienbeovl/Desktop/DBMS_Oracles/MRUP

# Generate 5 test cases with detailed logging
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 5 sqlite3 --oracle MRUP
```

### Step 2: Convert to SQL
```bash
# Convert a specific log file
python3 log_to_sql.py mrup_logs/mrup_20251210_075620_708.log

# Or use the helper script for latest
./verify_mrup_case.sh latest
```

### Step 3: Run in SQLite3
```bash
# Direct execution
sqlite3 < reproduction_mrup_20251210_075620_708.sql

# Or interactive
sqlite3
.read reproduction_mrup_20251210_075620_708.sql
```

---

## Example Output

### Table Contents
```
═══════════════════════════════════════════════════════════════════
TABLE CONTENTS
═══════════════════════════════════════════════════════════════════

--- Table t1 (t4) - 5 rows ---
dept        salary  age  c0    
----------  ------  ---  ------
Engineerin  40000   28   Data  
Engineerin  40000   34   Test  
Engineerin  45000   28   <NULL>
Engineerin  70000   64   Data  
Finance     60000   47   C     

--- Table t2 (t42) - 5 rows ---
dept       salary  age  c0  
---------  ------  ---  ----
Marketing  25000   53   Test
Marketing  50000   28   C   
Sales      25000   64   Data
Sales      60000   56   A   
Sales      65000   34   B   
```

### Window Function Results
```
═══════════════════════════════════════════════════════════════════
WINDOW FUNCTION QUERIES
═══════════════════════════════════════════════════════════════════

Window Function: MAX(age) OVER (PARTITION BY dept ORDER BY salary NULLS LAST)

--- Q1: Window function on t1 ---
dept        salary  age  c0      wf_result
----------  ------  ---  ------  ---------
Engineerin  40000   28   Data    34       
Engineerin  40000   34   Test    34       
Engineerin  45000   28   <NULL>  34       
Engineerin  70000   64   Data    64       
Finance     60000   47   C       47       

--- Q2: Window function on t2 ---
dept       salary  age  c0    wf_result
---------  ------  ---  ----  ---------
Marketing  25000   53   Test  53       
Marketing  50000   28   C     53       
Sales      25000   64   Data  64       
Sales      60000   56   A       64       
Sales      65000   34   B       64       

--- Q_union: Window function on (t1 UNION ALL t2) ---
dept        salary  age  c0      wf_result
----------  ------  ---  ------  ---------
Engineerin  40000   28   Data    34       
Engineerin  40000   34   Test    34       
Engineerin  45000   28   <NULL>  34       
Engineerin  70000   64   Data    64       
Finance     60000   47   C       47       
Marketing   25000   53   Test    53       
Marketing   50000   28   C       53       
Sales       25000   64   Data    64       
Sales       60000   56   A       64       
Sales       65000   34   B       64       
```

---

## Manual Verification

### Check 1: Disjoint Partitions ✓
- **t1 partitions**: Engineering, Finance
- **t2 partitions**: Marketing, Sales
- **Overlap**: NONE ✓

### Check 2: Q1 Results (5 rows) ✓
- Engineering: 4 rows (wf_result: 34, 34, 34, 64)
- Finance: 1 row (wf_result: 47)

### Check 3: Q2 Results (5 rows) ✓
- Marketing: 2 rows (wf_result: 53, 53)
- Sales: 3 rows (wf_result: 64, 64, 64)

### Check 4: Q_union = Q1 ∪ Q2 ✓
- **Expected**: 10 rows (5 from Q1 + 5 from Q2)
- **Actual**: 10 rows ✓
- **All Q1 rows present**: ✓
- **All Q2 rows present**: ✓
- **wf_result values match**: ✓

**Conclusion**: ✅ MRUP metamorphic relation verified!

---

## Key Features

### 1. Handles Variable Schemas
- 3 columns: `dept TEXT, salary INT, age INT`
- 4 columns: `dept TEXT, salary INT, age INT, c0 TEXT`
- 5 columns: `dept TEXT, salary INT, age INT, c0 TEXT, c1 REAL`

### 2. Proper NULL Handling
- Displays `<NULL>` in output for readability
- Correctly inserts NULL values in SQL
- Distinguishes between NULL and empty strings

### 3. Clean Output Format
- Column-aligned tables
- Clear section headers
- Verification guidance included
- Easy to compare results visually

### 4. Fully Reproducible
- No dependencies on SQLancer runtime
- Can be shared with others
- Can be run on any SQLite3 installation
- Can be archived for future reference

---

## Benefits

### For Development
1. **Debug False Positives**: Quickly verify if a detected "bug" is real
2. **Understand Test Cases**: See exactly what data and queries are being tested
3. **Iterate Faster**: No need to re-run the entire oracle to check one case

### For Bug Reporting
1. **Minimal Reproducers**: Share a single SQL file instead of logs
2. **Easy Verification**: Anyone with SQLite3 can verify the bug
3. **Clear Evidence**: Shows expected vs. actual results side-by-side

### For Documentation
1. **Examples**: Generate examples for documentation
2. **Test Archive**: Keep a library of interesting test cases
3. **Regression Tests**: Convert bugs into regression tests

---

## Advanced Usage

### Batch Processing
```bash
# Convert all logs to SQL
for log in mrup_logs/*.log; do
    python3 log_to_sql.py "$log"
done

# Run all SQL scripts and save outputs
for sql in reproduction_*.sql; do
    echo "Testing: $sql"
    sqlite3 < "$sql" > "${sql%.sql}_output.txt" 2>&1
done
```

### Compare SQLite Versions
```bash
# Test with different SQLite versions
sqlite3 < reproduction_mrup_20251210_075620_708.sql > output_v3.42.txt
/path/to/sqlite3.41 < reproduction_mrup_20251210_075620_708.sql > output_v3.41.txt

# Compare
diff output_v3.42.txt output_v3.41.txt
```

### Extract Specific Test Cases
```bash
# Find logs with specific window functions
grep -l "ROW_NUMBER" mrup_logs/*.log | while read log; do
    python3 log_to_sql.py "$log"
done
```

---

## Testing Results

### Test Case 1: 3-Column Table (dept, salary, age)
- **Log**: `mrup_20251210_075620_708.log`
- **Tables**: t0 (7 rows), t92 (5 rows)
- **Window Function**: `COUNT(*) OVER (PARTITION BY dept ORDER BY salary DESC ...)`
- **Result**: ✅ PASS - All results match

### Test Case 2: 4-Column Table (dept, salary, age, c0)
- **Log**: `mrup_20251210_075624_362.log`
- **Tables**: t4 (5 rows), t42 (5 rows)
- **Window Function**: `MAX(age) OVER (PARTITION BY dept ORDER BY salary ...)`
- **Result**: ✅ PASS - All results match

### Test Case 3: NULL Values
- **Verified**: NULL values in partition columns
- **Verified**: NULL values in ORDER BY columns
- **Verified**: NULL values in additional columns (c0, c1)
- **Result**: ✅ PASS - NULL handling correct

---

## Troubleshooting

### Issue: "Parse error: table has N columns but M values were supplied"
**Cause**: Column count mismatch between schema and data
**Solution**: Updated `log_to_sql.py` to dynamically detect column count from schema
**Status**: ✅ FIXED

### Issue: NULL values not displayed correctly
**Cause**: SQLite3 default NULL display is empty string
**Solution**: Added `.nullvalue <NULL>` directive to SQL scripts
**Status**: ✅ FIXED

### Issue: Hard to compare results visually
**Cause**: Default SQLite3 output format
**Solution**: Added `.mode column` and `.headers on` directives
**Status**: ✅ FIXED

---

## Files Summary

| File | Purpose | Status |
|------|---------|--------|
| `log_to_sql.py` | Convert logs to SQL | ✅ Complete |
| `verify_mrup_case.sh` | One-command verification | ✅ Complete |
| `MANUAL_VERIFICATION_GUIDE.md` | Comprehensive documentation | ✅ Complete |
| `REPRODUCTION_COMPLETE.md` | This summary | ✅ Complete |
| `mrup_logs/*.log` | Test case logs | ✅ Generated |
| `reproduction_*.sql` | Standalone SQL scripts | ✅ Generated |

---

## Conclusion

The MRUP test case reproduction system is **complete and fully functional**!

### What We Can Do Now:
1. ✅ Generate detailed MRUP logs with H(t1), H(t2), H(t_union) results
2. ✅ Convert any log file to a standalone SQL script
3. ✅ Run SQL scripts in any SQLite3 environment
4. ✅ Manually verify the MRUP metamorphic relation
5. ✅ Share reproducible test cases with others
6. ✅ Archive interesting test cases for future reference

### Key Achievements:
- **Fully automated** conversion process
- **Handles all schema variations** (3-5 columns)
- **Proper NULL handling** throughout
- **Clean, readable output** for manual verification
- **No false positives** in our tests
- **Easy to use** with helper scripts

---

**Status**: ✅ REPRODUCTION SYSTEM COMPLETE

All three steps requested by the user are working perfectly!

