# Mutation Statistics Dashboard - User Guide

## Overview

The mutation statistics dashboard provides real-time insights into how your MRUP oracle is exercising different mutation strategies. This helps you:

1. **Verify coverage** - Ensure all mutation variants are being tested
2. **Identify gaps** - Find underutilized mutations that may need adjustment
3. **Optimize bug-finding** - Focus on high-yield mutation patterns
4. **Track improvements** - Monitor changes over time

---

## Quick Start

```bash
# Generate test cases with logging enabled
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 1000 sqlite3 --oracle MRUP

# Analyze mutation distribution
python3 analyze_mutations.py
```

---

## Dashboard Sections

### 1. Overall Statistics
```
📊 Total Queries Analyzed: 727
```
Shows how many MRUP test cases were analyzed.

### 2. Phase 1: Window Spec Mutations
```
📍 PHASE 1: Window Spec Mutations

  ✅ None                       │ 55.43% │  403x │ ███████████████████████████
  ✅ Redundant PARTITION BY     │ 22.70% │  165x │ ███████████
  ✅ Order-Preserving Transform │ 12.38% │   90x │ ██████
```

**What it shows:**
- Mutations applied to the `OVER` clause
- Percentage and count for each variant
- Visual bar chart for quick comparison

**Indicators:**
- ✅ Green: Good usage (>5%)
- 🔹 Blue: Moderate usage (2-5%)
- 🔸 Orange: Low usage (1-2%)
- ⚠️ Yellow: No usage (0%)

### 3. Stage 1: Identity Wrapper Mutations
```
📍 STAGE 1: Identity Wrapper Mutations

  ✅ Arithmetic Identity (+ 0)     │ 11.42% │   83x │ █████
  ✅ Arithmetic Identity (* 1)     │  6.05% │   44x │ ███
  🔹 Chained Identity (+ 0 - 0)    │  3.71% │   27x │ █
```

**What it shows:**
- Identity transformations applied to window functions
- 15 different variants (arithmetic, type cast, NULL-safe, etc.)
- Distribution across all variants

**Key metrics:**
- **None**: Percentage of queries without identity mutation
- **Most common**: Should be arithmetic identity (+ 0, * 1)
- **Coverage**: Should be 15/15 variants active (100%)

### 4. Phase 3: CASE WHEN Mutations
```
📍 PHASE 3: CASE WHEN Mutations

  ✅ Window Function in WHEN       │ 26.82% │  195x │ █████████████
  ✅ Constant Condition            │ 26.41% │  192x │ █████████████
```

**What it shows:**
- CASE WHEN patterns that wrap window functions
- 5 different variants (constant condition, window in WHEN, etc.)
- Distribution should be relatively uniform

### 5. Top Mutation Combinations
```
🔗 TOP 10 MUTATION COMBINATIONS:

 1. [ 6.05%] ( 44x) None + None + Constant Condition
 2. [ 4.68%] ( 34x) None + None + Window Function in WHEN
```

**What it shows:**
- Most common combinations of mutations across all phases
- Format: `Window Spec + Identity + CASE WHEN`
- Helps identify which mutation paths are most exercised

### 6. Recommendations
```
💡 RECOMMENDATIONS:

1. ⚠️  Identity variant imbalance detected:
    Most common: 'Arithmetic Identity (+ 0)' (83x)
    Least common: 'NULL-Safe Identity (COALESCE)' (13x)
    → Consider adjusting Randomly.fromOptions() weights
```

**What it shows:**
- Actionable suggestions for improving mutation distribution
- Identifies imbalances and missing variants
- Provides specific code changes to make

### 7. Bug-Finding Potential
```
🎯 BUG-FINDING POTENTIAL:

  Identity Variants:   15/15 active (100.0%)
  CASE WHEN Variants:  5/5 active (100.0%)
  Window Spec Variants: 3/3 active (100.0%)

  ✅ Excellent coverage (100.0%) - All mutation types exercised!
```

**What it shows:**
- Overall coverage score (percentage of variants active)
- Breakdown by mutation stage
- Assessment of bug-finding readiness

---

## Interpreting Results

### ✅ Healthy Distribution

**Indicators:**
- All variants show >0% usage
- Most common variant is arithmetic identity (+ 0)
- Coverage score >90%
- No major imbalances (ratio <10:1)

**Example:**
```
Identity Variants:   15/15 active (100.0%)
Arithmetic Identity (+ 0): 11.42% (highest)
NULL-Safe Identity (COALESCE): 1.79% (lowest)
Ratio: 6.4:1 ✅ Acceptable
```

### ⚠️ Issues to Address

**Missing Variants (0% usage):**
```
⚠️ Chained Identity (+ 0 - 0)    │  0.00% │    0x │
```
**Action:** Check if variant is implemented and reachable

**Severe Imbalance (ratio >10:1):**
```
Most common: 'Arithmetic Identity (+ 0)' (177x)
Least common: 'Type Cast Identity (INTEGER)' (17x)
Ratio: 10.4:1 ⚠️
```
**Action:** Adjust weighted selection in `Randomly.fromOptions()`

**Low Coverage (<80%):**
```
Identity Variants:   10/15 active (66.7%)
```
**Action:** Investigate why variants are not being selected

---

## Common Adjustments

### Increase Mutation Rate

**Current:**
```java
// 60% of queries have identity mutations
if (globalState.getRandomly().getInteger(0, 10) < 6) {
    windowFunction = SQLite3MRUPIdentityMutator.applyIdentityWrapper(...);
}
```

**Increase to 80%:**
```java
if (globalState.getRandomly().getInteger(0, 10) < 8) {
    windowFunction = SQLite3MRUPIdentityMutator.applyIdentityWrapper(...);
}
```

### Rebalance Variant Weights

**Current (prioritizes + 0):**
```java
int variant = Randomly.fromOptions(
    1, 1, 2, 2, 3, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
);
```

**Uniform distribution:**
```java
int variant = Randomly.fromOptions(
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
);
```

### Add Missing Variants

If a variant shows 0% usage but should be implemented:

1. Check if it's in the code
2. Verify it's in the weighted selection
3. Ensure it's not blocked by conditional logic
4. Add debug logging to track selection

---

## Monitoring Over Time

### Track Changes

```bash
# Run 1000 queries
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 1000 sqlite3 --oracle MRUP

# Analyze and save results
python3 analyze_mutations.py > stats_$(date +%Y%m%d).txt

# Compare with previous run
diff stats_20251215.txt stats_20251216.txt
```

### Key Metrics to Track

1. **Coverage Score**: Should stay at or near 100%
2. **Variant Distribution**: Should be stable over time
3. **Top Combinations**: Should show diversity
4. **Bug Yield**: Track bugs found per 1000 queries

---

## Troubleshooting

### Dashboard shows 0 queries analyzed

**Cause:** No log files in `mrup_logs/` directory

**Fix:**
```bash
# Ensure logging is enabled
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 100 sqlite3 --oracle MRUP

# Check log files were created
ls -l mrup_logs/
```

### Some variants show 0% but should be active

**Cause:** Labeling bug in `getMutationDescription()`

**Fix:** Check pattern matching order (most specific first)

### Severe imbalance in distribution

**Cause:** Weighted selection favors certain variants

**Fix:** Adjust `Randomly.fromOptions()` weights or use uniform distribution

---

## Best Practices

1. **Run analysis regularly** - After every 500-1000 queries
2. **Track trends** - Save results and compare over time
3. **Prioritize high-yield mutations** - Keep arithmetic identity common
4. **Ensure full coverage** - All variants should be >0%
5. **Balance vs. yield** - Some imbalance is OK if it aligns with bug patterns

---

## Example Workflow

```bash
# 1. Generate test cases
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 1000 sqlite3 --oracle MRUP

# 2. Analyze mutations
python3 analyze_mutations.py

# 3. Review recommendations
# Check for missing variants or imbalances

# 4. Make adjustments (if needed)
# Edit SQLite3MRUPIdentityMutator.java or SQLite3MRUPOracle.java

# 5. Rebuild and retest
mvn clean package -DskipTests
java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar \
     --num-queries 1000 sqlite3 --oracle MRUP

# 6. Verify improvements
python3 analyze_mutations.py
```

---

## Summary

The mutation statistics dashboard is your window into the oracle's behavior. Use it to:

- ✅ Verify all mutations are active
- ✅ Identify optimization opportunities
- ✅ Track improvements over time
- ✅ Maximize bug-finding potential

**Goal:** Achieve 100% coverage with a distribution that prioritizes high-yield mutations while ensuring all variants are exercised.

**Current Status:** ✅ Excellent (100% coverage, good distribution)
