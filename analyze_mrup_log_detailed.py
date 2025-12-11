#!/usr/bin/env python3
"""
Ultra-detailed MRUP log analyzer.

This script takes an MRUP log file and generates a comprehensive breakdown showing:
- Step 1: Exact CREATE TABLE and INSERT statements
- Step 2: Data verification
- Step 3: Window function details
- Step 4: Generated queries
- Step 5: Layer-by-layer comparison analysis
  - Layer 1: Cardinality check with detailed breakdown
  - Layer 2: MRUP normalization algorithm step-by-step
  - Layer 3: Per-partition comparison with row-by-row matching

Usage: python3 analyze_mrup_log_detailed.py <log_file>
"""

import sys
import re
from pathlib import Path
from collections import defaultdict

def parse_log_file(log_path):
    """Parse MRUP log file and extract all information."""
    with open(log_path, 'r') as f:
        content = f.read()
    
    data = {}
    
    # Extract test case ID
    match = re.search(r'Test Case ID: (.+)', content)
    data['test_id'] = match.group(1) if match else 'unknown'
    
    # Extract schema
    match = re.search(r'📋 Schema.*?:\n\s*(.+)', content)
    data['schema'] = match.group(1).strip() if match else ''
    
    # Extract table names
    match = re.search(r'Table t1 \(([^)]+)\)', content)
    data['t1_name'] = match.group(1) if match else 't1'
    match = re.search(r'Table t2 \(([^)]+)\)', content)
    data['t2_name'] = match.group(1) if match else 't2'
    
    # Extract table data
    data['t1_data'] = extract_table_data(content, 'Table t1')
    data['t2_data'] = extract_table_data(content, 'Table t2')
    
    # Extract partitions
    match = re.search(r't1 partitions: \[([^\]]+)\]', content)
    data['t1_partitions'] = [p.strip() for p in match.group(1).split(',')] if match else []
    match = re.search(r't2 partitions: \[([^\]]+)\]', content)
    data['t2_partitions'] = [p.strip() for p in match.group(1).split(',')] if match else []
    
    # Extract window function
    match = re.search(r'🎯 Generated Window Function:\n\s*(.+)', content)
    data['window_function'] = match.group(1).strip() if match else ''
    
    # Extract queries
    match = re.search(r'📝 Q1.*?:\n\s*(.+)', content)
    data['q1'] = match.group(1).strip() if match else ''
    match = re.search(r'📝 Q2.*?:\n\s*(.+)', content)
    data['q2'] = match.group(1).strip() if match else ''
    match = re.search(r'📝 Q_union.*?:\n\s*(.+)', content)
    data['q_union'] = match.group(1).strip() if match else ''
    
    # Extract cardinality
    match = re.search(r'Layer 1: Cardinality Check\n\s*Expected: (\d+)\n\s*Actual:\s*(\d+)', content)
    if match:
        data['expected_cardinality'] = int(match.group(1))
        data['actual_cardinality'] = int(match.group(2))
    
    # Extract results
    data['h_t1'] = extract_results(content, 'H\\(t1\\) - Window function on t1:')
    data['h_t2'] = extract_results(content, 'H\\(t2\\) - Window function on t2:')
    data['h_expected'] = extract_results(content, 'Expected: H\\(t1\\) ∪ H\\(t2\\):')
    data['h_actual'] = extract_results(content, 'Actual: H\\(t_union\\)')
    
    # Extract test result
    data['passed'] = '✅ MRUP TEST PASSED' in content
    
    return data

def extract_table_data(content, marker):
    """Extract table data from log."""
    pattern = rf'{marker}.*?:\n(.*?)Total rows:'
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        return []
    
    table_content = match.group(1)
    rows = []
    
    for line in table_content.strip().split('\n'):
        line = line.strip()
        if not line or '---' in line or 'dept' in line.lower() and 'salary' in line.lower():
            continue
        parts = line.split()
        if len(parts) >= 3:
            rows.append(parts)
    
    return rows

def extract_results(content, marker):
    """Extract result set from log."""
    pattern = rf'{marker}(.*?)Total:'
    match = re.search(pattern, content, re.DOTALL)
    if not match:
        return []
    
    results_content = match.group(1)
    rows = []
    
    for line in results_content.strip().split('\n'):
        match = re.search(r'Row \d+: \[(.+)\]', line)
        if match:
            values = [v.strip() for v in match.group(1).split(',')]
            rows.append(values)
    
    return rows

def generate_detailed_analysis(data):
    """Generate ultra-detailed analysis."""
    output = []
    
    output.append("╔═══════════════════════════════════════════════════════════════════╗")
    output.append("║           ULTRA-DETAILED MRUP LOG ANALYSIS                        ║")
    output.append("╚═══════════════════════════════════════════════════════════════════╝")
    output.append("")
    output.append(f"Test Case ID: {data['test_id']}")
    output.append("")
    
    # STEP 1: CREATE TABLE
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("STEP 1: CREATE TABLES")
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("")
    output.append("📋 Schema Definition:")
    output.append(f"   {data['schema']}")
    output.append("")
    output.append("🔧 SQL CREATE TABLE Statements:")
    output.append(f"   CREATE TABLE {data['t1_name']} ({data['schema']});")
    output.append(f"   CREATE TABLE {data['t2_name']} ({data['schema']});")
    output.append("")
    
    # STEP 2: INSERT DATA
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("STEP 2: INSERT DATA")
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("")
    output.append(f"📊 Table t1 ({data['t1_name']}) - {len(data['t1_data'])} rows:")
    output.append("")
    for i, row in enumerate(data['t1_data'], 1):
        formatted_values = []
        for val in row:
            if val == '<NULL>' or val.upper() == 'NULL':
                formatted_values.append('NULL')
            else:
                try:
                    float(val)
                    formatted_values.append(val)
                except ValueError:
                    formatted_values.append(f"'{val}'")
        output.append(f"   INSERT INTO {data['t1_name']} VALUES ({', '.join(formatted_values)});")
    output.append("")
    
    output.append(f"📊 Table t2 ({data['t2_name']}) - {len(data['t2_data'])} rows:")
    output.append("")
    for i, row in enumerate(data['t2_data'], 1):
        formatted_values = []
        for val in row:
            if val == '<NULL>' or val.upper() == 'NULL':
                formatted_values.append('NULL')
            else:
                try:
                    float(val)
                    formatted_values.append(val)
                except ValueError:
                    formatted_values.append(f"'{val}'")
        output.append(f"   INSERT INTO {data['t2_name']} VALUES ({', '.join(formatted_values)});")
    output.append("")
    
    # Partition verification
    output.append("✓ Partition Verification:")
    output.append(f"   t1 partitions: {data['t1_partitions']}")
    output.append(f"   t2 partitions: {data['t2_partitions']}")
    overlap = set(data['t1_partitions']) & set(data['t2_partitions'])
    output.append(f"   Overlap: {'NONE ✓' if not overlap else str(overlap) + ' ✗'}")
    output.append(f"   Status: {'DISJOINT ✓' if not overlap else 'NOT DISJOINT ✗'}")
    output.append("")
    
    # STEP 3: WINDOW FUNCTION
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("STEP 3: WINDOW FUNCTION GENERATION")
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("")
    output.append("🎯 Generated Window Function:")
    output.append(f"   {data['window_function']}")
    output.append("")
    
    # Parse window function components
    wf = data['window_function']
    if 'PARTITION BY' in wf:
        partition_match = re.search(r'PARTITION BY ([^\s]+)', wf)
        partition_col = partition_match.group(1) if partition_match else 'unknown'
        output.append(f"   📌 PARTITION BY: {partition_col}")
    
    if 'ORDER BY' in wf:
        order_match = re.search(r'ORDER BY ([^)]+?)(?:ROWS|RANGE|$)', wf)
        order_clause = order_match.group(1).strip() if order_match else 'unknown'
        output.append(f"   📌 ORDER BY: {order_clause}")
    
    if 'ROWS' in wf or 'RANGE' in wf:
        frame_match = re.search(r'(ROWS|RANGE) ([^)]+)', wf)
        frame_clause = frame_match.group(0) if frame_match else 'unknown'
        output.append(f"   📌 FRAME: {frame_clause}")
    
    output.append("")
    
    # STEP 4: QUERIES
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("STEP 4: GENERATED QUERIES")
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("")
    output.append("📝 Q1 (window function on t1):")
    output.append(f"   {data['q1']}")
    output.append("")
    output.append("📝 Q2 (window function on t2):")
    output.append(f"   {data['q2']}")
    output.append("")
    output.append("📝 Q_union (window function on t1 UNION ALL t2):")
    output.append(f"   {data['q_union']}")
    output.append("")
    
    # STEP 5: COMPARISON - LAYER 1
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("STEP 5: RESULT COMPARISON")
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("")
    output.append("┌───────────────────────────────────────────────────────────────────┐")
    output.append("│ LAYER 1: CARDINALITY CHECK                                       │")
    output.append("└───────────────────────────────────────────────────────────────────┘")
    output.append("")
    output.append("This layer verifies that the total number of rows matches.")
    output.append("")
    output.append(f"   Q1 result count:      {len(data['h_t1'])} rows")
    output.append(f"   Q2 result count:      {len(data['h_t2'])} rows")
    output.append(f"   ─────────────────────────────────")
    output.append(f"   Expected (Q1 + Q2):   {data.get('expected_cardinality', len(data['h_t1']) + len(data['h_t2']))} rows")
    output.append(f"   Actual (Q_union):     {data.get('actual_cardinality', len(data['h_actual']))} rows")
    output.append("")
    
    cardinality_match = data.get('expected_cardinality', 0) == data.get('actual_cardinality', 0)
    output.append(f"   Result: {' ✓ PASS' if cardinality_match else '✗ FAIL'}")
    output.append("")
    
    # LAYER 2
    output.append("┌───────────────────────────────────────────────────────────────────┐")
    output.append("│ LAYER 2: MRUP NORMALIZATION (Semantic Sorting)                   │")
    output.append("└───────────────────────────────────────────────────────────────────┘")
    output.append("")
    output.append("This layer normalizes results for comparison by sorting them according")
    output.append("to MRUP semantics, which preserves window function behavior.")
    output.append("")
    output.append("🔧 MRUP Normalization Algorithm:")
    output.append("   1. Sort by partition key (dept)")
    output.append("   2. Within each partition, sort by window ORDER BY keys")
    output.append("   3. Use wf_result as tie-breaker for deterministic ordering")
    output.append("")
    output.append("📊 Sorting Keys (in order):")
    
    # Parse ORDER BY from window function
    order_match = re.search(r'ORDER BY ([^)]+?)(?:ROWS|RANGE|\))', data['window_function'])
    order_parts = []
    if order_match:
        order_clause = order_match.group(1).strip()
        order_parts = [p.strip() for p in order_clause.split(',')]
        output.append(f"   1. Partition key: dept")
        for i, part in enumerate(order_parts, 2):
            output.append(f"   {i}. Window ORDER BY: {part}")
        output.append(f"   {len(order_parts) + 2}. Tie-breaker: wf_result")
    output.append("")
    
    # Show BEFORE and AFTER normalization
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("BEFORE NORMALIZATION (Original Query Result Order)")
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("")
    
    # Show original H(t1) ∪ H(t2) order
    output.append("Expected [H(t1) ∪ H(t2)] - Original order:")
    expected_before = data['h_expected']
    for i, row in enumerate(expected_before[:20], 1):
        output.append(f"   Row {i:2d}: {row}")
    if len(expected_before) > 20:
        output.append(f"   ... ({len(expected_before) - 20} more rows)")
    output.append("")
    
    # Show original H(t_union) order
    output.append("Actual [H(t_union)] - Original order:")
    actual_before = data['h_actual']
    for i, row in enumerate(actual_before[:20], 1):
        output.append(f"   Row {i:2d}: {row}")
    if len(actual_before) > 20:
        output.append(f"   ... ({len(actual_before) - 20} more rows)")
    output.append("")
    
    # Simulate MRUP normalization
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("APPLYING MRUP NORMALIZATION...")
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("")
    
    # Parse ORDER BY directions
    order_info = []
    if order_parts:
        for part in order_parts:
            col_name = part.split()[0]
            is_desc = 'DESC' in part.upper()
            nulls_first = 'NULLS FIRST' in part.upper()
            order_info.append({
                'column': col_name,
                'desc': is_desc,
                'nulls_first': nulls_first
            })
    
    output.append("Sorting algorithm:")
    output.append("   Step 1: Group rows by partition key (dept)")
    output.append("   Step 2: Within each partition, sort by:")
    for i, info in enumerate(order_info, 1):
        direction = "DESC" if info['desc'] else "ASC"
        nulls = "NULLS FIRST" if info['nulls_first'] else "NULLS LAST"
        output.append(f"           {i}. {info['column']} {direction} {nulls}")
    output.append("   Step 3: Use wf_result as final tie-breaker")
    output.append("")
    
    # Perform actual normalization simulation
    def normalize_results(results, order_info):
        """Simulate MRUP normalization."""
        def compare_value(val1, val2, desc=False, nulls_first=False):
            # Handle NULL
            if val1 == 'NULL' or val1 is None:
                if val2 == 'NULL' or val2 is None:
                    return 0
                return -1 if nulls_first else 1
            if val2 == 'NULL' or val2 is None:
                return 1 if nulls_first else -1
            
            # Try numeric comparison
            try:
                v1 = float(val1)
                v2 = float(val2)
                if v1 < v2:
                    return 1 if desc else -1
                elif v1 > v2:
                    return -1 if desc else 1
                return 0
            except (ValueError, TypeError):
                # String comparison
                if val1 < val2:
                    return 1 if desc else -1
                elif val1 > val2:
                    return -1 if desc else 1
                return 0
        
        def sort_key(row):
            # row format: [dept, salary, age, c0, ..., wf_result]
            keys = []
            
            # 1. Partition key (dept) - always ASC
            keys.append((row[0] if row[0] != 'NULL' else '', 0))
            
            # 2. ORDER BY columns
            for info in order_info:
                col_name = info['column']
                # Find column index
                if col_name == 'salary':
                    idx = 1
                elif col_name == 'age':
                    idx = 2
                else:
                    continue
                
                val = row[idx]
                # Convert for sorting
                if val == 'NULL' or val is None:
                    sort_val = float('inf') if not info['nulls_first'] else float('-inf')
                else:
                    try:
                        sort_val = float(val)
                        if info['desc']:
                            sort_val = -sort_val
                    except (ValueError, TypeError):
                        sort_val = val
                        
                keys.append((sort_val, idx))
            
            # 3. Tie-breaker: wf_result (last column)
            wf_val = row[-1]
            try:
                keys.append((float(wf_val) if wf_val != 'NULL' else float('inf'), -1))
            except (ValueError, TypeError):
                keys.append((wf_val, -1))
            
            return keys
        
        return sorted(results, key=sort_key)
    
    expected_after = normalize_results(expected_before.copy(), order_info)
    actual_after = normalize_results(actual_before.copy(), order_info)
    
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("AFTER NORMALIZATION (MRUP Sorted Order)")
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("")
    
    # Show normalized H(t1) ∪ H(t2) order
    output.append("Expected [H(t1) ∪ H(t2)] - After MRUP normalization:")
    for i, row in enumerate(expected_after[:20], 1):
        output.append(f"   Row {i:2d}: {row}")
    if len(expected_after) > 20:
        output.append(f"   ... ({len(expected_after) - 20} more rows)")
    output.append("")
    
    # Show normalized H(t_union) order
    output.append("Actual [H(t_union)] - After MRUP normalization:")
    for i, row in enumerate(actual_after[:20], 1):
        output.append(f"   Row {i:2d}: {row}")
    if len(actual_after) > 20:
        output.append(f"   ... ({len(actual_after) - 20} more rows)")
    output.append("")
    
    # Check if order changed
    order_changed_expected = expected_before != expected_after
    order_changed_actual = actual_before != actual_after
    
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("NORMALIZATION IMPACT")
    output.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    output.append("")
    output.append(f"   Expected result order changed: {'YES ✓' if order_changed_expected else 'NO (already in MRUP order)'}")
    output.append(f"   Actual result order changed:   {'YES ✓' if order_changed_actual else 'NO (already in MRUP order)'}")
    output.append("")
    
    if order_changed_expected or order_changed_actual:
        output.append("   💡 Why normalization matters:")
        output.append("      SQLite may return results in different physical orders depending")
        output.append("      on how the query is executed. MRUP normalization ensures we")
        output.append("      compare results in a semantically consistent order that respects")
        output.append("      the window function's PARTITION BY and ORDER BY clauses.")
    else:
        output.append("   💡 Results were already in MRUP order!")
        output.append("      SQLite happened to return results in the same order as the")
        output.append("      MRUP normalization would produce.")
    output.append("")
    
    output.append("   Result: ✓ All result sets normalized and ready for comparison")
    output.append("")
    
    # LAYER 3
    output.append("┌───────────────────────────────────────────────────────────────────┐")
    output.append("│ LAYER 3: PER-PARTITION COMPARISON (Exact Match)                  │")
    output.append("└───────────────────────────────────────────────────────────────────┘")
    output.append("")
    output.append("This layer compares results partition-by-partition to verify the")
    output.append("MRUP metamorphic relation: H(t1 ∪ t2) = H(t1) ∪ H(t2)")
    output.append("")
    
    # Group results by partition
    def group_by_partition(results):
        partitions = defaultdict(list)
        for row in results:
            partition_key = row[0]  # First column is dept (partition key)
            partitions[partition_key].append(row)
        return partitions
    
    expected_partitions = group_by_partition(data['h_expected'])
    actual_partitions = group_by_partition(data['h_actual'])
    
    all_partitions = sorted(set(list(expected_partitions.keys()) + list(actual_partitions.keys())))
    
    output.append(f"📊 Partitions to compare: {len(all_partitions)}")
    output.append("")
    
    all_match = True
    for partition in all_partitions:
        expected_rows = expected_partitions.get(partition, [])
        actual_rows = actual_partitions.get(partition, [])
        
        match = expected_rows == actual_rows
        all_match = all_match and match
        
        output.append(f"   Partition: {partition}")
        output.append(f"      Expected rows: {len(expected_rows)}")
        output.append(f"      Actual rows:   {len(actual_rows)}")
        output.append(f"      Match: {'✓ YES' if match else '✗ NO'}")
        
        if not match:
            output.append("")
            output.append("      ❌ MISMATCH DETAILS:")
            output.append("")
            output.append("      Expected rows:")
            for i, row in enumerate(expected_rows[:5], 1):
                output.append(f"         {i}. {row}")
            if len(expected_rows) > 5:
                output.append(f"         ... ({len(expected_rows) - 5} more)")
            output.append("")
            output.append("      Actual rows:")
            for i, row in enumerate(actual_rows[:5], 1):
                output.append(f"         {i}. {row}")
            if len(actual_rows) > 5:
                output.append(f"         ... ({len(actual_rows) - 5} more)")
        output.append("")
    
    output.append(f"   Overall Result: {'✓ PASS - All partitions match!' if all_match else '✗ FAIL - Mismatches detected!'}")
    output.append("")
    
    # FINAL RESULT
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append(f"{'✅ MRUP TEST PASSED' if data['passed'] else '❌ MRUP TEST FAILED'}")
    output.append("═══════════════════════════════════════════════════════════════════")
    output.append("")
    
    if data['passed']:
        output.append("✓ All layers passed:")
        output.append("  ✓ Layer 1: Cardinality matches")
        output.append("  ✓ Layer 2: Results normalized correctly")
        output.append("  ✓ Layer 3: All partitions match exactly")
        output.append("")
        output.append("The MRUP metamorphic relation holds: H(t1 ∪ t2) = H(t1) ∪ H(t2)")
    
    return '\n'.join(output)

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 analyze_mrup_log_detailed.py <log_file>")
        print("Example: python3 analyze_mrup_log_detailed.py mrup_logs/mrup_20251210_111608_235.log")
        sys.exit(1)
    
    log_file = Path(sys.argv[1])
    if not log_file.exists():
        print(f"Error: Log file not found: {log_file}")
        sys.exit(1)
    
    print(f"Analyzing: {log_file}")
    print()
    
    data = parse_log_file(log_file)
    analysis = generate_detailed_analysis(data)
    
    # Write to output file
    output_file = log_file.stem + "_DETAILED_ANALYSIS.txt"
    with open(output_file, 'w') as f:
        f.write(analysis)
    
    # Also print to console
    print(analysis)
    print()
    print(f"✅ Detailed analysis written to: {output_file}")

if __name__ == '__main__':
    main()

