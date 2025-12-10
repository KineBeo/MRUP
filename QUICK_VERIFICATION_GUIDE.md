# Quick Bug Verification Guide

## Run the Minimal Bug Reproduction

```bash
cd /home/kienbeovl/Desktop/DBMS_Oracles/MRUP
sqlite3 < minimal_bug_reproduction.sql
```

**Look for**: The last row should show `NULL` in Expected but `467965356.911078` in Actual.

---

## Run the MRUP Oracle

```bash
# Build the project
mvn package -DskipTests -q

# Run the oracle (generates bug reports)
java -jar target/sqlancer-2.0.0.jar --num-queries 20 sqlite3 --oracle MRUP
```

**Look for**: Messages like `[MRUP] Bug report saved to: bug_report_*.sql`

---

## Verify a Generated Bug Report

```bash
# List generated bug reports
ls -lht bug_report_*.sql | head -5

# Run the most recent one
sqlite3 < bug_report_1764908538998.sql
```

**Look for**: Differences between "Expected" and "Actual" sections.

---

## Check SQLite Version

```bash
sqlite3 --version
```

**Expected**: `3.50.4` or similar

---

## Clean Up Bug Reports

```bash
# Remove all generated bug reports
rm -f bug_report_*.sql
```

---

## Key Observations

### ✅ Bug is Real If:
1. **Cardinality matches** (same number of rows)
2. **Values differ** (different window function results)
3. **Reproducible** (happens consistently)
4. **Not BLOB comparison** (BLOBs show as `[B@...` which are memory addresses)

### ❌ False Positive If:
1. Only BLOB values differ (memory addresses)
2. Floating point precision differences only
3. Non-reproducible (random)

---

## Example Output (Bug Confirmed)

```
Expected: Q1 ∪ Q2
|||^Z���|                    ← NULL result (correct)

Actual: Q_union
|||^Z���|467965356.911078    ← Non-NULL result (WRONG!)
```

The window function should return NULL for the t2 row, but instead returns a cumulative sum including t1's values.

---

## Troubleshooting

### If No Bugs Found
- Run more queries: `--num-queries 100`
- The bug is probabilistic (depends on random data)

### If Too Many Bug Reports
- Reduce queries: `--num-queries 10`
- Clean up: `rm -f bug_report_*.sql`

### If Compilation Fails
```bash
mvn clean compile -DskipTests
```

---

## Files Reference

| File | Description |
|------|-------------|
| `minimal_bug_reproduction.sql` | 20-line minimal test case |
| `bug_report_*.sql` | Auto-generated bug reports |
| `BUG_VERIFICATION_REPORT.md` | Detailed analysis |
| `VERIFICATION_SUMMARY.md` | High-level summary |

---

**Quick Test** (30 seconds):
```bash
cd /home/kienbeovl/Desktop/DBMS_Oracles/MRUP
sqlite3 < minimal_bug_reproduction.sql | grep -A 1 "Actual:"
```

Should show the bug in the last row! 🐛

