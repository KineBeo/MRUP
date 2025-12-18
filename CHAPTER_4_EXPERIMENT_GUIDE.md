# Chapter 4 Experiment Guide

## Overview

Chapter 4 (Evaluation) has been rewritten following the academic plan. The chapter structure is complete, but **experimental results are NOT filled in yet** and marked as `[TBD]` throughout.

This document provides instructions for running experiments and collecting data to fill in the `[TBD]` placeholders.

---

## What Has Been Done

### ✅ Complete Sections

1. **Section 4.1: Mục tiêu và phương pháp đánh giá**
   - 4 Research Questions (RQ1-RQ4) defined
   - Evaluation methodology explained
   - Academic framing: oracle-centric evaluation

2. **Section 4.2: Thiết lập thực nghiệm**
   - Hardware/software environment specified
   - Test corpus described (10,000 main + 100×10 stability tests)
   - Data collection methodology explained

3. **Section 4.3: Metrics đánh giá**
   - 8 metrics defined (M1-M8)
   - All metrics justified against 8-rule framework
   - Expected values specified for each metric

4. **Section 4.4-4.7: Kết quả và Thảo luận**
   - RQ1-RQ4 result sections with placeholder tables
   - Discussion section with academic framing
   - Threats to validity
   - Future work directions

5. **Section 4.8: Tóm tắt**
   - Chapter summary with [TBD] markers

---

## What Needs to Be Done

### 📊 Experimental Data Collection

You need to run the MRUP Oracle and collect data to fill in **7 tables** and **analysis paragraphs**:

| Table | Label | Purpose | Data Needed |
|-------|-------|---------|-------------|
| Table 1 | `tab:constraint_satisfaction` | Constraint adherence (RQ1) | Per-constraint satisfaction count |
| Table 2 | `tab:mutation_application` | Mutation application rates (RQ2) | Mutation type success/skip counts |
| Table 3 | `tab:case_when_distribution` | CASE WHEN strategy distribution (RQ2) | Strategy counts (5 strategies) |
| Table 4 | `tab:input_diversity` | Schema & query diversity (RQ2) | Schema/query characteristic distributions |
| Table 5 | `tab:comparator_behavior` | Comparator layer utilization (RQ3) | Layer reached/passed counts |
| Table 6 | `tab:repeated_execution` | Determinism (RQ3) | Variance, false positives from repeated runs |
| Table 7 | `tab:oracle_throughput` | Performance (RQ4) | Throughput, timing breakdown |

---

## Step-by-Step Experiment Instructions

### Prerequisites

1. **MRUP Oracle is integrated into SQLancer**
   - Verify: `SQLite3MRUPOracle.java` exists and compiles
   - Verify: `SQLite3MRUPTablePairGenerator.java` exists
   - Verify: SQLancer builds without errors

2. **Enable detailed logging**
   - You need to add logging to capture metrics
   - See "Required Code Modifications" section below

3. **Environment setup**
   - Java: OpenJDK 17
   - SQLite: 3.45.0 (or latest stable)
   - Hardware: Document your actual hardware specs

---

### Experiment 1: Main Evaluation (10,000 test cases)

**Goal**: Collect data for RQ1, RQ2, RQ4

**Command**:
```bash
cd /path/to/sqlancer
java -jar target/SQLancer-*.jar \
  --random-seed 42 \
  --num-queries 10000 \
  --timeout-seconds 3600 \
  --oracle mrup \
  sqlite3 \
  --log-file /path/to/mrup_main_10k.log
```

**Expected duration**: ~3-4 hours (at 50-70 tests/sec)

**Data to extract from logs**:

1. **For Table 1 (Constraint Satisfaction)**:
   - Count: How many test cases satisfy each constraint C0-C5?
   - Expected: All should be 10,000 (100%)
   - Log format: `verifyConstraints(): {C0: true, C1: true, ...}`

2. **For Table 2 (Mutation Application)**:
   - Count: Window Spec applied vs skipped
   - Count: Identity applied vs skipped
   - Count: CASE WHEN applied vs skipped
   - Log format: `Mutation applied: [type]` or `Mutation skipped: [type] (reason)`

3. **For Table 3 (CASE WHEN Distribution)**:
   - Count: How many times each of 5 strategies was used?
   - Log format: `CASE WHEN strategy: [1-5]`

4. **For Table 4 (Input Diversity)**:
   - Schema: Column count distribution, type distribution, NULL rate, edge case rate
   - Query: Function type, ORDER BY columns, frame presence, frame type
   - Log format: Parse schema and query structure from generated SQL

5. **For Table 7 (Throughput)**:
   - Measure: Total time, average time per test case
   - Breakdown: Time for each phase (table gen, query gen, mutation, execution, comparison)
   - Log format: Timestamps for each phase

---

### Experiment 2: Stability Test (100 test cases × 10 runs)

**Goal**: Collect data for RQ3 (determinism)

**Command**:
```bash
for i in {1..10}; do
  java -jar target/SQLancer-*.jar \
    --random-seed 42 \
    --num-queries 100 \
    --oracle mrup \
    sqlite3 \
    --log-file /path/to/mrup_stability_run_$i.log
done
```

**Expected duration**: ~30 minutes total

**Data to extract**:
- Compare results across 10 runs
- Calculate: Result variance (should be 0.0)
- Count: False positives (should be 0)
- Count: Constraint violations (should be 0)

**For Table 6 (Repeated Execution)**:
- Result variance: Standard deviation of results across runs
- False positive: Any test case that reports mismatch inconsistently
- Deterministic rate: % of test cases with identical results across all runs

---

### Experiment 3: Comparator Behavior

**Goal**: Collect data for Table 5 (RQ3)

**Data source**: Main evaluation logs (10,000 test cases)

**Data to extract**:
- Count: How many test cases reached Layer 1 (Cardinality)? (Should be 10,000)
- Count: How many passed Layer 1 and reached Layer 2 (Normalization)?
- Count: How many passed Layer 2 and reached Layer 3 (Per-Partition)?
- Count: Partition disjointness validation pass/fail
- Count: Type-aware comparison invocations

**Log format** (needs to be added):
```
Comparator: Layer 1 (Cardinality) - PASSED
Comparator: Layer 2 (Normalization) - PASSED
Comparator: Layer 3 (Per-Partition) - PASSED
```

---

## Required Code Modifications

To collect the necessary data, you need to add logging statements to the MRUP Oracle code:

### 1. Add Constraint Logging in `SQLite3MRUPOracle.java`

```java
// In verifyConstraints() method
private Map<String, Boolean> verifyConstraints(...) {
    Map<String, Boolean> results = new HashMap<>();
    // ... existing constraint checks ...
    
    // Add logging
    logger.info("METRICS: Constraint satisfaction: " + 
                "C0=" + results.get("C0") + ", " +
                "C1=" + results.get("C1") + ", " +
                "C2=" + results.get("C2") + ", " +
                "C3=" + results.get("C3") + ", " +
                "C4=" + results.get("C4") + ", " +
                "C5=" + results.get("C5"));
    
    return results;
}
```

### 2. Add Mutation Logging

```java
// After each mutation attempt
if (mutationApplied) {
    logger.info("METRICS: Mutation applied: " + mutationType);
} else {
    logger.info("METRICS: Mutation skipped: " + mutationType + " (reason: " + reason + ")");
}

// For CASE WHEN strategy
logger.info("METRICS: CASE WHEN strategy: " + strategyNumber);
```

### 3. Add Schema/Query Logging

```java
// After schema generation
logger.info("METRICS: Schema: columns=" + numColumns + 
            ", types=" + typeDistribution + 
            ", nullRate=" + nullRate);

// After query generation
logger.info("METRICS: Query: function=" + functionType + 
            ", orderByColumns=" + numOrderByColumns + 
            ", hasFrame=" + hasFrame + 
            ", frameType=" + frameType);
```

### 4. Add Timing Logging

```java
// At each phase
long startTime = System.currentTimeMillis();
// ... do work ...
long endTime = System.currentTimeMillis();
logger.info("METRICS: Phase " + phaseName + " time: " + (endTime - startTime) + " ms");
```

### 5. Add Comparator Layer Logging

```java
// In comparison methods
logger.info("METRICS: Comparator Layer 1 (Cardinality) - " + (passed ? "PASSED" : "FAILED"));
logger.info("METRICS: Comparator Layer 2 (Normalization) - " + (passed ? "PASSED" : "FAILED"));
logger.info("METRICS: Comparator Layer 3 (Per-Partition) - " + (passed ? "PASSED" : "FAILED"));
```

---

## Log Parsing Scripts

After running experiments, you'll need to parse logs to extract metrics. Here's a Python template:

```python
import re
from collections import Counter

def parse_constraint_satisfaction(log_file):
    """Extract constraint satisfaction data for Table 1"""
    satisfied = Counter()
    violated = Counter()
    
    with open(log_file, 'r') as f:
        for line in f:
            if 'METRICS: Constraint satisfaction' in line:
                # Parse: C0=true, C1=true, ...
                for constraint in ['C0', 'C1', 'C2', 'C3', 'C4', 'C5']:
                    match = re.search(f'{constraint}=(true|false)', line)
                    if match:
                        if match.group(1) == 'true':
                            satisfied[constraint] += 1
                        else:
                            violated[constraint] += 1
    
    return satisfied, violated

def parse_mutation_application(log_file):
    """Extract mutation application data for Table 2"""
    applied = Counter()
    skipped = Counter()
    
    with open(log_file, 'r') as f:
        for line in f:
            if 'METRICS: Mutation applied:' in line:
                mutation_type = line.split('applied:')[1].strip()
                applied[mutation_type] += 1
            elif 'METRICS: Mutation skipped:' in line:
                mutation_type = line.split('skipped:')[1].split('(')[0].strip()
                skipped[mutation_type] += 1
    
    return applied, skipped

def parse_case_when_distribution(log_file):
    """Extract CASE WHEN strategy distribution for Table 3"""
    strategies = Counter()
    
    with open(log_file, 'r') as f:
        for line in f:
            if 'METRICS: CASE WHEN strategy:' in line:
                strategy = int(line.split('strategy:')[1].strip())
                strategies[strategy] += 1
    
    return strategies

# Add similar functions for other metrics...

# Usage
if __name__ == '__main__':
    log_file = '/path/to/mrup_main_10k.log'
    
    satisfied, violated = parse_constraint_satisfaction(log_file)
    print("Table 1 - Constraint Satisfaction:")
    for constraint in ['C0', 'C1', 'C2', 'C3', 'C4', 'C5']:
        print(f"  {constraint}: Satisfied={satisfied[constraint]}, Violated={violated[constraint]}")
    
    # ... parse other tables ...
```

---

## Filling in Chapter 4 Tables

Once you have the data, update the LaTeX file:

### Example: Table 1 (Constraint Satisfaction)

**Before**:
```latex
C0: PARTITION BY bắt buộc & [TBD] & [TBD] & [TBD]\% \\
```

**After** (assuming all constraints satisfied):
```latex
C0: PARTITION BY bắt buộc & 10,000 & 0 & 100.0\% \\
```

### Example: Analysis Paragraphs

**Before**:
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

**After**:
```latex
\textbf{Phân tích:} Kết quả cho thấy MRUP Oracle tuân thủ hoàn hảo 
hệ thống ràng buộc với tỷ lệ thỏa mãn 100\% cho tất cả 6 ràng buộc 
trên 10,000 test case. Không có vi phạm nào được ghi nhận, chứng minh 
rằng logic sinh truy vấn và áp dụng đột biến hoạt động chính xác. 
Đây là kết quả quan trọng vì bất kỳ vi phạm ràng buộc nào cũng sẽ 
làm mất hiệu lực quan hệ metamorphic và dẫn đến false positive.
```

---

## Summary of [TBD] Locations

The following need to be filled in after experiments:

1. **Section 4.4.1 (RQ1)**: Table 1 + analysis paragraph + "Ý nghĩa cho RQ1"
2. **Section 4.4.2 (RQ2)**: Tables 2, 3, 4 + analysis paragraphs + "Ý nghĩa cho RQ2"
3. **Section 4.4.3 (RQ3)**: Tables 5, 6 + analysis paragraphs + "Ý nghĩa cho RQ3"
4. **Section 4.4.4 (RQ4)**: Table 7 + analysis paragraph + "Ý nghĩa cho RQ4"
5. **Section 4.5.1 (Oracle Readiness)**: Replace first [TBD] with synthesis of RQ1-RQ4
6. **Section 4.5.1 (Comparisons)**: Replace [TBD: So sánh thông lượng] with actual comparison
7. **Section 4.6 (Tóm tắt)**: Replace all [TBD: Kết quả] and [TBD: Tổng hợp] with actual findings

---

## Checklist

Before finalizing Chapter 4:

- [ ] Run main evaluation (10,000 test cases)
- [ ] Run stability test (100 × 10 runs)
- [ ] Parse logs and extract all metrics
- [ ] Fill in all 7 tables with actual data
- [ ] Write analysis paragraphs for each RQ
- [ ] Write "Ý nghĩa cho RQ1-4" synthesis
- [ ] Update discussion section with concrete findings
- [ ] Update summary section with key results
- [ ] Verify no [TBD] markers remain
- [ ] Compile LaTeX to check formatting
- [ ] Proofread for consistency

---

## Questions?

If you encounter issues:

1. **Can't run experiments**: Check SQLancer build, Java version, SQLite installation
2. **Logs incomplete**: Verify logging statements were added to all required locations
3. **Data inconsistent**: Check random seed (should be 42 for reproducibility)
4. **Parsing errors**: Adjust regex patterns in parsing scripts to match actual log format

Good luck! The chapter structure is solid—you just need to fill in the experimental data.

