# MRUP Oracle Architecture

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         SQLancer Framework                       │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              SQLite3Provider                            │    │
│  │  - Database creation                                    │    │
│  │  - Table generation (reused)                           │    │
│  │  - Insert generation (reused)                          │    │
│  └────────────────────────────────────────────────────────┘    │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │          SQLite3OracleFactory                          │    │
│  │  - PQS                                                 │    │
│  │  - NoREC                                               │    │
│  │  - TLP (WHERE, AGGREGATE, HAVING, etc.)               │    │
│  │  - MRUP  ← NEW!                                       │    │
│  └────────────────────────────────────────────────────────┘    │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │          SQLite3MRUPOracle                             │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │  check() - Main Oracle Logic                 │    │    │
│  │  │  1. Select two random tables                 │    │    │
│  │  │  2. Generate window specification (OSRB)     │    │    │
│  │  │  3. Build queries (Q1, Q2, Q_union)         │    │    │
│  │  │  4. Execute queries                          │    │    │
│  │  │  5. Compare cardinalities                    │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │  generateWindowSpecOSRB()                    │    │    │
│  │  │  - PARTITION BY (optional)                   │    │    │
│  │  │  - ORDER BY (always)                         │    │    │
│  │  │  - FRAME clause (optional)                   │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │  generateFrameClause()                       │    │    │
│  │  │  - ROWS / RANGE / GROUPS                     │    │    │
│  │  │  - Frame boundaries                          │    │    │
│  │  │  - EXCLUDE clause                            │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │  generateWindowFunction()                    │    │    │
│  │  │  - ROW_NUMBER, RANK, DENSE_RANK             │    │    │
│  │  │  - SUM, AVG, COUNT, MIN, MAX                │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │  buildWindowQuery()                          │    │    │
│  │  │  buildWindowQueryOnUnion()                   │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │  executeAndGetCardinality()                  │    │    │
│  │  │  - Execute query                             │    │    │
│  │  │  - Count rows                                │    │    │
│  │  │  - Handle errors                             │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  └────────────────────────────────────────────────────────┘    │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              SQLite3 Database                          │    │
│  │  - Execute queries                                     │    │
│  │  - Return results                                      │    │
│  └────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
┌─────────────┐
│   Start     │
└──────┬──────┘
       │
       ↓
┌─────────────────────────────────────────┐
│  Step 1: Get Two Random Tables          │
│  - t1 (e.g., 2 rows)                   │
│  - t2 (e.g., 3 rows)                   │
└──────┬──────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────┐
│  Step 2: Conceptual Union               │
│  - t_union = t1 UNION ALL t2           │
│  - (not actually created)              │
└──────┬──────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────┐
│  Step 3: Generate Window Spec (OSRB)    │
│                                         │
│  ┌─────────────────────────────────┐  │
│  │ PARTITION BY? (50% chance)      │  │
│  │  - Random columns               │  │
│  └─────────────────────────────────┘  │
│               ↓                        │
│  ┌─────────────────────────────────┐  │
│  │ ORDER BY (always)               │  │
│  │  - Random columns               │  │
│  │  - ASC/DESC                     │  │
│  │  - NULLS FIRST/LAST             │  │
│  └─────────────────────────────────┘  │
│               ↓                        │
│  ┌─────────────────────────────────┐  │
│  │ FRAME? (50% chance)             │  │
│  │  - ROWS/RANGE/GROUPS            │  │
│  │  - Boundaries                   │  │
│  │  - EXCLUDE clause               │  │
│  └─────────────────────────────────┘  │
│                                         │
│  Result: "OVER (...)"                  │
└──────┬──────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────┐
│  Step 4: Execute Queries                │
│                                         │
│  ┌─────────────────────────────────┐  │
│  │ Q1 = SELECT *, WF OVER(...)     │  │
│  │      FROM t1                    │  │
│  │ → Execute → Count rows (e.g. 2) │  │
│  └─────────────────────────────────┘  │
│               ↓                        │
│  ┌─────────────────────────────────┐  │
│  │ Q2 = SELECT *, WF OVER(...)     │  │
│  │      FROM t2                    │  │
│  │ → Execute → Count rows (e.g. 3) │  │
│  └─────────────────────────────────┘  │
│               ↓                        │
│  ┌─────────────────────────────────┐  │
│  │ Q_union = SELECT *, WF OVER(...)│  │
│  │           FROM (t1 UNION t2)    │  │
│  │ → Execute → Count rows (e.g. 5) │  │
│  └─────────────────────────────────┘  │
└──────┬──────────────────────────────────┘
       │
       ↓
┌─────────────────────────────────────────┐
│  Step 5: Compare Cardinalities          │
│                                         │
│  Expected = |Q1| + |Q2| = 2 + 3 = 5    │
│  Actual   = |Q_union|   = 5            │
│                                         │
│  Expected == Actual?                   │
│       │                                 │
│       ├─ YES → ✅ PASS                  │
│       │                                 │
│       └─ NO  → ❌ BUG FOUND!            │
│                (throw AssertionError)   │
└─────────────────────────────────────────┘
```

## Component Interaction Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    SQLancer Main Loop                         │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ↓
        ┌───────────────────────────────┐
        │   Create Database             │
        │   Generate Tables (t1, t2...) │
        │   Insert Data                 │
        └───────────────┬───────────────┘
                        │
                        ↓
        ┌───────────────────────────────┐
        │   Call Oracle.check()         │
        └───────────────┬───────────────┘
                        │
                        ↓
┌───────────────────────────────────────────────────────────────┐
│                SQLite3MRUPOracle.check()                      │
│                                                               │
│  1. schema.getDatabaseTables()                               │
│     ↓                                                         │
│  2. Select t1, t2 randomly                                   │
│     ↓                                                         │
│  3. Get columns from t1                                      │
│     ↓                                                         │
│  4. generateWindowSpecOSRB(columns)                          │
│     ├─→ generateFrameClause() (if needed)                    │
│     └─→ Returns: "OVER (...)"                               │
│     ↓                                                         │
│  5. generateWindowFunction(column, windowSpec)               │
│     └─→ Returns: "ROW_NUMBER() OVER (...)"                  │
│     ↓                                                         │
│  6. buildWindowQuery(t1, columns, windowFunction)            │
│     └─→ Returns: "SELECT ..., WF FROM t1"                   │
│     ↓                                                         │
│  7. buildWindowQuery(t2, columns, windowFunction)            │
│     └─→ Returns: "SELECT ..., WF FROM t2"                   │
│     ↓                                                         │
│  8. buildWindowQueryOnUnion(t1, t2, columns, windowFunction) │
│     └─→ Returns: "SELECT ..., WF FROM (t1 UNION t2)"       │
│     ↓                                                         │
│  9. executeAndGetCardinality(q1)                             │
│     ├─→ Execute query                                        │
│     ├─→ Count rows                                           │
│     ├─→ Handle errors                                        │
│     └─→ Returns: card1                                       │
│     ↓                                                         │
│ 10. executeAndGetCardinality(q2)                             │
│     └─→ Returns: card2                                       │
│     ↓                                                         │
│ 11. executeAndGetCardinality(qUnion)                         │
│     └─→ Returns: cardUnion                                   │
│     ↓                                                         │
│ 12. Compare: cardUnion == (card1 + card2)?                   │
│     ├─→ YES: Return (test passes)                           │
│     └─→ NO:  throw AssertionError (bug found!)              │
└───────────────────────────────────────────────────────────────┘
```

## OSRB Algorithm Flow

```
generateWindowSpecOSRB(columns)
    │
    ↓
┌────────────────────────────────┐
│ Start: "OVER ("                │
└────────┬───────────────────────┘
         │
         ↓
    ┌────────┐
    │Random? │
    └───┬────┘
        │
    ┌───┴───┐
    │       │
   YES     NO
    │       │
    ↓       └─────────────────┐
┌──────────────────────────┐  │
│ Add PARTITION BY         │  │
│ - Select 1-3 columns     │  │
│ - Append column names    │  │
└──────────┬───────────────┘  │
           │                  │
           └──────┬───────────┘
                  │
                  ↓
┌──────────────────────────────┐
│ Add ORDER BY (always)        │
│ - Select 1-3 columns         │
│ - Add ASC/DESC (random)      │
│ - Add NULLS FIRST/LAST (50%) │
└──────────┬───────────────────┘
           │
           ↓
      ┌────────┐
      │Random? │
      └───┬────┘
          │
      ┌───┴───┐
      │       │
     YES     NO
      │       │
      ↓       └─────────────────┐
┌──────────────────────────┐    │
│ Add FRAME clause         │    │
│ - Call generateFrame()   │    │
│ - ROWS/RANGE/GROUPS      │    │
│ - Boundaries             │    │
│ - EXCLUDE (50%)          │    │
└──────────┬───────────────┘    │
           │                    │
           └──────┬─────────────┘
                  │
                  ↓
┌──────────────────────────────┐
│ Append ")"                   │
│ Return complete spec         │
└──────────────────────────────┘
```

## Error Handling Flow

```
executeAndGetCardinality(query)
    │
    ↓
┌────────────────────────────────┐
│ Create Statement               │
│ Execute query                  │
└────────┬───────────────────────┘
         │
         ↓
    ┌────────┐
    │Success?│
    └───┬────┘
        │
    ┌───┴───┐
    │       │
   YES     NO (Exception)
    │       │
    ↓       ↓
┌─────┐  ┌──────────────────────┐
│Count│  │ Check error message  │
│rows │  └──────────┬───────────┘
└──┬──┘             │
   │            ┌───┴───┐
   │            │       │
   │        Expected  Unexpected
   │            │       │
   │            ↓       ↓
   │    ┌─────────┐  ┌──────────┐
   │    │ Ignore  │  │ Rethrow  │
   │    │(skip)   │  │(report)  │
   │    └─────────┘  └──────────┘
   │
   ↓
┌──────────────┐
│Return count  │
└──────────────┘
```

## Class Hierarchy

```
TestOracle<SQLite3GlobalState>
    ↑
    │ implements
    │
SQLite3MRUPOracle
    │
    ├─ Fields:
    │   ├─ globalState: SQLite3GlobalState
    │   ├─ errors: ExpectedErrors
    │   └─ lastQueryString: String
    │
    ├─ Public Methods:
    │   ├─ check(): void
    │   └─ getLastQueryString(): String
    │
    └─ Private Methods:
        ├─ generateWindowSpecOSRB(columns): String
        ├─ generateFrameClause(): String
        ├─ generateWindowFunction(column, spec): String
        ├─ buildWindowQuery(table, columns, wf): String
        ├─ buildWindowQueryOnUnion(t1, t2, cols, wf): String
        └─ executeAndGetCardinality(query): int
```

## Integration Points

```
┌─────────────────────────────────────────────────────────┐
│                SQLancer Framework                        │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ Reused Components:                             │    │
│  │ ✅ SQLite3Provider                             │    │
│  │ ✅ SQLite3TableGenerator                       │    │
│  │ ✅ SQLite3InsertGenerator                      │    │
│  │ ✅ SQLite3Schema                               │    │
│  │ ✅ SQLite3GlobalState                          │    │
│  │ ✅ ExpectedErrors                              │    │
│  │ ✅ Randomly utility                            │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ New Components:                                │    │
│  │ ✅ SQLite3MRUPOracle                           │    │
│  │ ✅ MRUP enum in SQLite3OracleFactory           │    │
│  └────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    User Environment                      │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ Command Line:                                  │    │
│  │ java -jar sqlancer.jar sqlite3 --oracle MRUP  │    │
│  └────────────┬───────────────────────────────────┘    │
│               │                                          │
│               ↓                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ SQLancer JAR                                   │    │
│  │ - Compiled classes                             │    │
│  │ - Dependencies                                 │    │
│  │ - Resources                                    │    │
│  └────────────┬───────────────────────────────────┘    │
│               │                                          │
│               ↓                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ SQLite3 Database Files                         │    │
│  │ ./databases/database-*.db                      │    │
│  │ - Created on-the-fly                           │    │
│  │ - Deleted after testing                        │    │
│  └────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

## Summary

The MRUP oracle architecture is:

✅ **Modular**: Clear separation of concerns  
✅ **Extensible**: Easy to add features  
✅ **Reusable**: Leverages existing SQLancer components  
✅ **Testable**: Each component can be tested independently  
✅ **Maintainable**: Well-documented, clean code  

The architecture follows SQLancer's design patterns and integrates seamlessly with the existing framework.

