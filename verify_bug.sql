-- MRUP Bug Verification Script
-- This script reproduces a potential bug found by the MRUP oracle
-- Bug: Window function produces different results for UNION ALL vs separate queries

-- Clean up
DROP TABLE IF EXISTS t2;
create table t2 (vkey int, pkey int, c19 int, c20 double, c22 int);
insert into t2 values (135, 145000, -13, 17.61, 0);


.print ""
.print "=== Table t2 ==="
SELECT * FROM t2;

.print ""
.print "=== test case 1 ==="
select  
  case when true then null else (DENSE_RANK() over (partition by ref_0.pkey order by ref_0.vkey desc, ref_0.pkey asc)) end as c_0, 
  case when ((LAST_VALUE(ref_0.pkey) over (partition by ref_0.c19 order by ref_0.vkey desc, ref_0.pkey desc)) in (select  
          ref_0.c22 as c_0
        from 
          t2 as ref_8
        )) then ref_0.c20 else ref_0.c20 end
       as c_1 
from 
  t2 as ref_0
order by c_1 desc;


-- Expected result: Q1 ∪ Q2 should equal Q_union
.print ""
.print "=== test case 2 ==="
select  
  case when true then null else (DENSE_RANK() over (partition by ref_0.pkey order by ref_0.vkey desc, ref_0.pkey asc)) end as c_0, 
  case when ((LAST_VALUE(ref_0.pkey) over (partition by ref_0.c19 order by ref_0.vkey desc, ref_0.pkey desc)) in (select  
          ref_0.c22 as c_0
        from 
          t2 as ref_8
        )) then ref_0.c20 else ref_0.c20 end
       as c_1 
from 
  t2 as ref_0
order by c_1 desc;
