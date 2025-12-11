#!/usr/bin/env python3
"""
Convert MRUP log file to standalone SQL reproduction script.
Usage: python3 log_to_sql.py <log_file>
"""

import sys
import re
from pathlib import Path

def extract_table_data(content, table_marker, num_columns):
    """Extract table data from log content."""
    pattern = rf'{table_marker}.*?:\n(.*?)Total rows:'
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        return []
    
    table_content = match.group(1)
    rows = []
    
    for line in table_content.strip().split('\n'):
        line = line.strip()
        # Skip empty lines, separators, and headers
        if not line or '---' in line or '│' in line or '┌' in line or '└' in line:
            continue
        if 'dept' in line.lower() and 'salary' in line.lower():
            continue
        
        # Parse data row
        parts = line.split()
        if len(parts) >= num_columns:
            # Extract exactly num_columns values
            row_values = parts[:num_columns]
            rows.append(tuple(row_values))
    
    return rows

def format_value(val):
    """Format a value for SQL INSERT."""
    # Check for NULL representation (case-insensitive, with or without angle brackets)
    if val.upper() == 'NULL' or val == '' or val == '<NULL>':
        return 'NULL'
    # Try to parse as number (integer or float)
    try:
        # Try integer first
        int(val)
        return val
    except ValueError:
        try:
            # Try float
            float(val)
            return val
        except ValueError:
            # It's a string, quote it (but escape single quotes)
            escaped_val = val.replace("'", "''")
            return f"'{escaped_val}'"

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 log_to_sql.py <mrup_log_file>")
        print("Example: python3 log_to_sql.py mrup_logs/mrup_20251210_075620_708.log")
        sys.exit(1)
    
    log_file = Path(sys.argv[1])
    if not log_file.exists():
        print(f"Error: Log file not found: {log_file}")
        sys.exit(1)
    
    # Read log file
    content = log_file.read_text()
    
    # Extract information
    test_id = log_file.stem
    output_file = f"reproduction_{test_id}.sql"
    
    # Extract table names
    t1_match = re.search(r'Table t1 \(([^)]+)\)', content)
    t2_match = re.search(r'Table t2 \(([^)]+)\)', content)
    t1_name = t1_match.group(1) if t1_match else 't1'
    t2_name = t2_match.group(1) if t2_match else 't2'
    
    # Extract schema
    schema_match = re.search(r'📋 Schema.*?:\n\s*(.+)', content)
    schema = schema_match.group(1).strip() if schema_match else 'dept TEXT, salary INT, age INT'
    
    # Count number of columns from schema
    num_columns = len([col.strip() for col in schema.split(',') if col.strip()])
    
    # Extract queries
    q1_match = re.search(r'📝 Q1.*?:\n\s*(.+)', content)
    q2_match = re.search(r'📝 Q2.*?:\n\s*(.+)', content)
    qunion_match = re.search(r'📝 Q_union.*?:\n\s*(.+)', content)
    
    q1 = q1_match.group(1).strip() if q1_match else ''
    q2 = q2_match.group(1).strip() if q2_match else ''
    q_union = qunion_match.group(1).strip() if qunion_match else ''
    
    # Extract table data
    t1_rows = extract_table_data(content, '📊 Table t1', num_columns)
    t2_rows = extract_table_data(content, '📊 Table t2', num_columns)
    
    # Generate SQL script
    sql = f"""-- ═══════════════════════════════════════════════════════════════════
-- MRUP Oracle - Manual Verification Script
-- ═══════════════════════════════════════════════════════════════════
-- Test Case: {test_id}
-- Generated from: {log_file.name}
--
-- This script allows you to manually verify the MRUP metamorphic relation:
--   H(t1 ∪ t2) = H(t1) ∪ H(t2)
--
-- Instructions:
--   1. Run: sqlite3 < {output_file}
--   2. Compare Q1, Q2, and Q_union results
--   3. Verify that H(t_union) = H(t1) ∪ H(t2)
-- ═══════════════════════════════════════════════════════════════════

.mode column
.headers on
.nullvalue <NULL>

-- ───────────────────────────────────────────────────────────────────
-- STEP 1: Create Tables
-- ───────────────────────────────────────────────────────────────────

DROP TABLE IF EXISTS {t1_name};
DROP TABLE IF EXISTS {t2_name};

CREATE TABLE {t1_name} ({schema});
CREATE TABLE {t2_name} ({schema});

-- ───────────────────────────────────────────────────────────────────
-- STEP 2: Insert Data into t1 ({t1_name})
-- ───────────────────────────────────────────────────────────────────

"""
    
    # Add t1 inserts
    for row in t1_rows:
        formatted_values = [format_value(val) for val in row]
        values_str = ', '.join(formatted_values)
        sql += f"INSERT INTO {t1_name} VALUES ({values_str});\n"
    
    sql += f"""
-- ───────────────────────────────────────────────────────────────────
-- STEP 3: Insert Data into t2 ({t2_name})
-- ───────────────────────────────────────────────────────────────────

"""
    
    # Add t2 inserts
    for row in t2_rows:
        formatted_values = [format_value(val) for val in row]
        values_str = ', '.join(formatted_values)
        sql += f"INSERT INTO {t2_name} VALUES ({values_str});\n"
    
    sql += f"""
-- ───────────────────────────────────────────────────────────────────
-- STEP 4: Verify Table Contents
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "TABLE CONTENTS"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "--- Table t1 ({t1_name}) - {len(t1_rows)} rows ---"
SELECT * FROM {t1_name} ORDER BY dept, salary, age;

.print ""
.print "--- Table t2 ({t2_name}) - {len(t2_rows)} rows ---"
SELECT * FROM {t2_name} ORDER BY dept, salary, age;

-- ───────────────────────────────────────────────────────────────────
-- STEP 5: Execute Window Function Queries
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "WINDOW FUNCTION QUERIES"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "Window Function: {q1.split('OVER')[1].split('FROM')[0].strip() if 'OVER' in q1 else 'N/A'}"
.print ""
.print "--- Q1: Window function on t1 ---"
{q1};

.print ""
.print "--- Q2: Window function on t2 ---"
{q2};

.print ""
.print "--- Q_union: Window function on (t1 UNION ALL t2) ---"
{q_union};

-- ───────────────────────────────────────────────────────────────────
-- STEP 6: Manual Verification
-- ───────────────────────────────────────────────────────────────────

.print ""
.print "═══════════════════════════════════════════════════════════════════"
.print "MANUAL VERIFICATION GUIDE"
.print "═══════════════════════════════════════════════════════════════════"
.print ""
.print "To verify the MRUP metamorphic relation:"
.print ""
.print "1. Look at Q1 results (window function on t1)"
.print "2. Look at Q2 results (window function on t2)"
.print "3. Mentally compute: H(t1) ∪ H(t2) = Q1 results + Q2 results"
.print "4. Compare with Q_union results"
.print "5. They should match exactly (same rows, possibly different order)"
.print ""
.print "Key points:"
.print "  - t1 and t2 have DISJOINT partitions (no overlap in dept values)"
.print "  - Window function operates per partition"
.print "  - Therefore: H(t1 ∪ t2) should equal H(t1) ∪ H(t2)"
.print ""
.print "If they don't match, you've found a bug in SQLite3!"
.print ""

.print "═══════════════════════════════════════════════════════════════════"
.print "END OF REPRODUCTION SCRIPT"
.print "═══════════════════════════════════════════════════════════════════"
"""
    
    # Write output file
    Path(output_file).write_text(sql)
    
    print(f"✅ SQL reproduction script generated: {output_file}")
    print(f"   - Table t1 ({t1_name}): {len(t1_rows)} rows")
    print(f"   - Table t2 ({t2_name}): {len(t2_rows)} rows")
    print(f"   - Total: {len(t1_rows) + len(t2_rows)} rows")
    print()
    print("To run:")
    print(f"  sqlite3 < {output_file}")
    print()
    print("Or interactively:")
    print("  sqlite3")
    print(f"  .read {output_file}")

if __name__ == '__main__':
    main()

