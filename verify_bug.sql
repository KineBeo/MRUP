-- MRUP Bug Verification Script
-- This script reproduces a potential bug found by the MRUP oracle
-- Bug: Window function produces different results for UNION ALL vs separate queries

-- Clean up
DROP TABLE IF EXISTS t1;
DROP TABLE IF EXISTS t2;

-- Create two tables with the same schema
CREATE TABLE t1 (
    c0 INTEGER,
    c1 TEXT
);

CREATE TABLE t2 (
    c0 INTEGER,
    c1 TEXT
);

-- Insert test data into t1
INSERT INTO t1 (c0, c1) VALUES 
    (100, 'A'),
    (200, 'B');

-- Insert test data into t2 (same schema, different data)
INSERT INTO t2 (c0, c1) VALUES 
    (300, 'C'),
    (400, 'D');

-- Display the base tables
.print "=== Table t1 ==="
SELECT * FROM t1;

.print ""
.print "=== Table t2 ==="
SELECT * FROM t2;

-- Test Case 1: Window function on t1
.print ""
.print "=== Q1: Window function on t1 ==="
SELECT c0, c1, 
       DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
FROM t1;

-- Test Case 2: Window function on t2
.print ""
.print "=== Q2: Window function on t2 ==="
SELECT c0, c1, 
       DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
FROM t2;

-- Test Case 3: Window function on UNION ALL
.print ""
.print "=== Q_union: Window function on (t1 UNION ALL t2) ==="
SELECT c0, c1, 
       DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union;

-- Expected result: Q1 ∪ Q2 should equal Q_union
.print ""
.print "=== Expected (Q1 ∪ Q2) ==="
SELECT * FROM (
    SELECT c0, c1, 
           DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
    FROM t1
    UNION ALL
    SELECT c0, c1, 
           DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
    FROM t2
) ORDER BY c0, c1;

.print ""
.print "=== Actual (Q_union) ==="
SELECT c0, c1, 
       DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union
ORDER BY c0, c1;

-- Verification: Check if they match
.print ""
.print "=== Verification: Do they match? ==="
.print "If the following query returns 0 rows, they match (no bug)."
.print "If it returns rows, there's a mismatch (bug confirmed)."

-- This query finds differences
SELECT 'MISMATCH' AS status, * FROM (
    SELECT c0, c1, 
           DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
    FROM t1
    UNION ALL
    SELECT c0, c1, 
           DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
    FROM t2
)
EXCEPT
SELECT 'MISMATCH' AS status, * FROM (
    SELECT c0, c1, 
           DENSE_RANK() OVER (PARTITION BY c1 ORDER BY c0 DESC) AS wf_result 
    FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union
);

