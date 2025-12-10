-- Minimal Bug Reproduction for SQLite3 Window Function
-- Bug: SUM() window function produces incorrect results with UNION ALL
-- 
-- Expected: H(t1 UNION ALL t2) == H(t1) UNION ALL H(t2)
-- Actual: They differ!

DROP TABLE IF EXISTS t1;
DROP TABLE IF EXISTS t2;

-- Create two simple tables
CREATE TABLE t1 (c0 INT, c1 REAL, c2 TEXT, c3 TEXT);
CREATE TABLE t2 (c0 INT, c1 REAL, c2 TEXT, c3 TEXT);

-- Insert test data
INSERT INTO t1 VALUES (467965356, 1468024308.0, '', 'kS|i{n?J');
INSERT INTO t1 VALUES ('t{D', NULL, '0.0393764902993792', '1468024308');
INSERT INTO t1 VALUES (0.9110783143780562, x'', NULL, '');
INSERT INTO t1 VALUES (NULL, 1261821004.0, '/&', '0.705999575628964');

INSERT INTO t2 VALUES (NULL, NULL, NULL, x'1af5adf1');

.print "=== BUG DEMONSTRATION ==="
.print ""
.print "Q1: Window function on t1 only"
SELECT c0, c3, SUM(c0) OVER (ORDER BY c3 NULLS LAST) AS wf_result FROM t1;

.print ""
.print "Q2: Window function on t2 only"
SELECT c0, c3, SUM(c0) OVER (ORDER BY c3 NULLS LAST) AS wf_result FROM t2;

.print ""
.print "Expected: Q1 UNION ALL Q2 (window function applied separately)"
SELECT c0, c3, SUM(c0) OVER (ORDER BY c3 NULLS LAST) AS wf_result FROM t1
UNION ALL
SELECT c0, c3, SUM(c0) OVER (ORDER BY c3 NULLS LAST) AS wf_result FROM t2;

.print ""
.print "Actual: Window function on (t1 UNION ALL t2)"
SELECT c0, c3, SUM(c0) OVER (ORDER BY c3 NULLS LAST) AS wf_result 
FROM (SELECT * FROM t1 UNION ALL SELECT * FROM t2) AS t_union;

.print ""
.print "=== ANALYSIS ==="
.print "Look at the last row (from t2):"
.print "  - Expected: NULL (because c0 is NULL in t2)"
.print "  - Actual: 467965356.911078 (incorrect - includes values from t1!)"
.print ""
.print "This violates the metamorphic relation:"
.print "  H(t1 ∪ t2) should equal H(t1) ∪ H(t2)"
.print "  where H is the window function SUM(c0) OVER (...)"

