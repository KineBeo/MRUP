Here is the **complete, clean, and fully formatted Markdown version** of your entire `MRUP (MR-UNsoc-PARTITION) Oracle Design.pdf` document, ready to be used in Cursor, Obsidian, or any Markdown editor:

```markdown
# MRUP (MR-UNION-PARTITION) Oracle Design

Dưới đây là bản **FULL – HOÀN CHỈNH – CHUẨN HOÁ** danh sách Mutation Strategies dành riêng cho oracle **MR-UNION-PARTITION**, tập trung hoàn toàn vào phần `OVER()`.

Bạn có thể dùng danh sách này để:
- chọn mutation hiệu quả nhất cho PoC 1 tuần,
- sau đó chuẩn hoá thành oracle mới,
- đưa vào thesis như một Metamorphic Relation + Mutation Space rõ ràng và có giá trị học thuật.

## 1. Foundation Knowledge

### 1.1 Window Function là gì (hiểu cực dễ)

**Window function** = hàm hoạt động trên một “cửa sổ” gồm nhiều dòng, nhưng  
- không gom dữ liệu thành 1 dòng như `GROUP BY`  
- không loại bỏ dòng như `GROUP BY`  
- giữ nguyên toàn bộ số dòng, và chỉ thêm 1 giá trị tính toán cho mỗi dòng.

**Ví dụ:**
```sql
SELECT 
    salary,
    AVG(salary) OVER ()
FROM employees;
```
Dù tính `AVG` trên toàn bảng → số dòng **không thay đổi**.

### 1.2 Tại sao Window functions quan trọng?
(Và tại sao chúng hay có bug logic trong DBMS)

Window functions phức tạp vì:
a. **Ordering + Partitioning + Frame** cấu thành một bộ quy tắc nhiều tầng → dễ sai.  
b. **3-valued logic với NULL** → nguồn gốc của rất nhiều bug.  
c. `ORDER BY` trong window function **khác** `ORDER BY` của query.  
d. Frame có nhiều mode: `RANGE`, `ROWS`, `GROUPS` → rất nhiều special cases.  
e. **Interdependency**: window function có thể chứa hàm khác, hoặc được lồng trong subquery.

→ Chính vì độ phức tạp cao, nhiều DBMS có **logic bug** (thuộc dạng PQS khó cover).  
SQLancer cũng **không cover** window functions (như paper OSDI'20 chỉ ra).

> **Research gap**: Table 1 cho thấy các approach hiện tại chưa hỗ trợ Window function quá nhiều.

### 1.3 Các thành phần cốt lõi của Window Function

```sql
<Window Function> OVER (
      [PARTITION BY ...]     -- Divide the data into groups
      [ORDER BY ...]          -- specify the order of rows within each group
      [FRAME CLAUSE]
)
```

```sql
SELECT column_name1, 
       window_function(column_name2) 
       OVER ([PARTITION BY column_name3] [ORDER BY column_name4]) AS new_column
FROM table_name;
```

- `window_function`: Any aggregate or ranking function (`SUM()`, `AVG()`, `ROW_NUMBER()`, etc.)
- `column_name1`: Regular column(s) to be selected in the output
- `column_name2`: Column on which the window function is applied
- `column_name3`: Column used for dividing rows into groups (`PARTITION BY`)
- `column_name4`: Column used to define order of rows within each partition (`ORDER BY`)
- `new_column`: Alias for calculated result
- `table_name`: table from which data is selected

#### 1.3.1 Window Function

Gồm 3 nhóm:

##### (A) Aggregate window functions
- `SUM()`, `AVG()`, `COUNT()`, `MIN()`, `MAX()`
- Chạy như aggregate nhưng **không gom nhóm**.

**Ví dụ:**
```sql
SELECT Name, Age, Department, Salary, 
       AVG(Salary) OVER(PARTITION BY Department) AS Avg_Salary
FROM employee
```

**Output & Giải thích:**
- Finance: (50,000 + 50,000 + 20,000) / 3 = 40,000
- Sales: (30,000 + 20,000) / 2 = 25,000
- Giá trị trung bình được lặp lại cho mỗi nhân viên trong cùng phòng ban.

##### (B) Ranking functions
- `ROW_NUMBER()`: Assigns a unique number to each row
- `RANK()`: Skips ranks for duplicates
- `DENSE_RANK()`: No skipping
- `NTILE(n)`

###### 1.3.1.1 RANK() Function
```sql
SELECT Name, Department, Salary,
       RANK() OVER(PARTITION BY Department ORDER BY Salary DESC) AS emp_rank
FROM employee;
```
→ Ramesh & Suresh cùng lương 50,000 → rank 1 → Ram 20,000 → rank 3 (bỏ qua rank 2)

###### 1.3.1.2 DENSE_RANK() Function
```sql
SELECT Name, Department, Salary,
       DENSE_RANK() OVER(PARTITION BY Department ORDER BY Salary DESC) AS emp_dense_rank
FROM employee;
```
→ Không bỏ qua rank → Ram được rank 2

###### 1.3.1.3 ROW_NUMBER() Function

##### (C) Analytic functions
- `LAG(expr [, offset] [, default])`
- `LEAD(expr ...)`
- `FIRST_VALUE()` / `LAST_VALUE()`
- `NTH_VALUE()`

#### 1.3.2 Over Clause

##### 1.3.2.1 PARTITION BY
→ chia bảng thành nhiều nhóm (như `GROUP BY` nhưng **không gom dòng**)

```sql
PARTITION BY department
```
→ Mỗi phòng ban là một “window”.

##### 1.3.2.2 ORDER BY
→ xác định thứ tự trong mỗi window  
**Không liên quan** đến `ORDER BY` của query bên ngoài.

Bug thường xảy ra tại đây:
- `ORDER BY` có `NULL` (`NULLS FIRST` / `NULLS LAST`)
- `ORDER BY` nhiều cột
- `ORDER BY` với collation
- `ORDER BY` với biểu thức (computed expression)

##### 1.3.2.3 FRAME – phần phức tạp nhất (In Progress)

- Frame là sub-set record của window/partition
- Syntax (MySQL):

```sql
frame_clause:
    frame_units frame_extent

frame_units:
    {ROWS | RANGE}

frame_extent:
    {frame_start | frame_between}

frame_between:
    BETWEEN frame_start AND frame_end

frame_start, frame_end: {
    CURRENT ROW|
    UNBOUNDED PRECEDING|
    UNBOUNDED FOLLOWING|
    expr PRECEDING|
    expr FOLLOWING
}
```

**Ví dụ:**
```sql
10 PRECEDING
INTERVAL 5 DAY PRECEDING
5 FOLLOWING
INTERVAL '2:30' MINUTE_SECOND FOLLOWING
```

**Giới hạn:**
- `UNBOUNDED PRECEDING`: bắt đầu từ dòng đầu partition
- `N PRECEDING`: dòng thứ N trước current row
- `CURRENT ROW`
- `UNBOUNDED FOLLOWING`: dòng cuối partition
- `N FOLLOWING`

**ROWS vs RANGE**:
- `ROWS`: đếm theo số dòng (physical rows)
- `RANGE`: đếm theo giá trị (logical difference)

**Ví dụ ROWS**:
```sql
SELECT day, amount,
       SUM(amount) OVER (
           ORDER BY day
           ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
       ) AS moving_3_days
FROM sales;
```
→ rolling window cố định 3 dòng

**Ví dụ RANGE**:
```sql
SELECT score,
       SUM(score) OVER (
           ORDER BY score
           RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
       ) AS cumulative_score
FROM scores;
```
→ Khi có giá trị trùng → frame mở rộng theo giá trị, không theo dòng

## 2. Overview Approach

**MR-UNION-PARTITION** yêu cầu:

```sql
H(e_union) == H(e1) ∪ H(e2)
```



Trong đó `H()` = window operator `F() OVER(...)`

→ Vì vậy mutation lý tưởng là nhắm vào `OVER()` sao cho vẫn **tương đương về mặt semantics**, không làm thay đổi kết quả đúng, nhưng có khả năng kích hoạt lỗi khi DBMS thực thi sai.

Khái quát mutation cần sinh ra thay đổi trong 4 phần:
- `PARTITION BY`
- `ORDER BY`
- `FRAME` (`ROWS` / `RANGE` / `GROUPS`)
- `VALUE expressions` bên trong `ORDER BY` hoặc `FRAME`

## 3. MASTER LIST – 50+ Chiến lược Mutate phần OVER()

Hầu hết mutation áp dụng cho **TẤT CẢ** window functions.

### 1. PARTITION-BY Mutations (Partition semantics)

Mục tiêu: kích hoạt lỗi partition leakage, partition reset sai, hoặc engine gộp partition sai.

| ID | Tên | Ví dụ |
|----|-----|------|
| P1 | Add redundant partition key | `PARTITION BY dept` → `PARTITION BY dept, dept` |
| P2 | Add semantically redundant expression | `PARTITION BY dept` → `PARTITION BY dept, dept || ''` |
| P3 | Add unique column that does NOT change partition | `PARTITION BY dept` → `PARTITION BY dept, id` |
| P4 | Remove constant partition key | Nếu thêm cột constant thì bỏ đi không đổi output |
| P5 | Reorder partition columns | `PARTITION BY (a, b)` ↔ `PARTITION BY (b, a)` |
| P6 | Shuffle source table rows | Partition must stay same |
| P7 | Partition key with NULL | Test: NULL không thuộc group nào → group NULL phải riêng |

### 2. ORDER-BY Mutations (Ordering-semantics bugs)

| ID | Tên | Ví dụ |
|----|-----|------|
| O1 | Redundant ORDER key | `ORDER BY x` → `ORDER BY x, x` |
| O2 | Order-preserving transform | `ORDER BY x` → `ORDER BY x + 0`, `x * 1`, `ROUND(x, 10)` |
| O3 | Add tie-breaker unique key | `ORDER BY salary` → `ORDER BY salary, id` |
| O4 | Change ASC ⇆ DESC twice | `ORDER BY x ASC` → `ORDER BY -x DESC` |
| O5 | Add NULL ordering spec | `ORDER BY x` → `ORDER BY x NULLS FIRST` |
| O6 | Reorder ORDER BY columns when equal | `ORDER BY x, y` ↔ `ORDER BY y, x` |
| O7 | Shuffle input table | ORDER semantics phải giữ |
| O8 | Add deterministic expression | `ORDER BY x` → `ORDER BY (x + id*0)` |
| O9 | Inject stable hash key | `ORDER BY x` → `ORDER BY x, HASH(id)` |

### 3. FRAME Mutations (ROWS / RANGE / GROUPS)

#### A. Basic Frame Mutations
| ID | Tên | Ví dụ |
|----|-----|------|
| F1 | Shrink frame | `UNBOUNDED PRECEDING` → `1 PRECEDING` |
| F2 | Expand frame | `1 PRECEDING` → `UNBOUNDED PRECEDING` |
| F3 | CURRENT ROW equivalence | `ROWS BETWEEN 0 PRECEDING AND 0 FOLLOWING` ↔ `CURRENT ROW` |
| F4 | Shift by 1 | `1 PRECEDING` → `2 PRECEDING` |
| F5 | Switch FOLLOWING / PRECEDING | Valid khi data ordered đơn giản |
| F6 | Remove frame spec where default is equivalent | `ROWS UNBOUNDED PRECEDING` → no frame |
| F7 | Add redundant frame | `... AND CURRENT ROW + 0` |

#### B. RANGE-specific mutations
| ID | Tên | Ví dụ |
|----|-----|------|
| F8 | Switch ROWS ↔ RANGE | Nếu `ORDER BY` unique → semantics giống nhau |
| F9 | Add numeric range offset | `RANGE BETWEEN 1 PRECEDING AND CURRENT ROW` |
| F10| Change boundary sign | `1 PRECEDING` → `1 FOLLOWING` |
| F11| Change RANGE to literal comparison | `RANGE BETWEEN INTERVAL '1' DAY PRECEDING` |

#### C. GROUPS mode (PG15+, DuckDB, TiDB)
| ID | Tên | Ví dụ |
|----|-----|------|
| F12| GROUPS 1 PRECEDING == ROWS for 1-row groups | Nếu mỗi giá trị `ORDER BY` là unique |

### 4. Value-expression Mutations inside OVER()

| ID | Tên | Ví dụ |
|----|-----|------|
| V1 | Arithmetic identity | `ORDER BY x + 0`, `salary * 1` |
| V2 | Function identity | `ORDER BY ABS(x)` nếu `x ≥ 0` |
| V3 | Cast identity | `ORDER BY CAST(x AS INT)` nếu x là integer |
| V4 | Concat identity (strings) | `ORDER BY dept || ''` |
| V5 | Sign invariance | Nếu `x > 0`: `ORDER BY x` == `ORDER BY -(-x)` |
| V6 | Nested expression | `ORDER BY ((x))` |

### 5. Wrapping / Query-structure mutations

| ID | Tên | Ví dụ |
|----|-----|------|
| Q1 | Wrap in subquery | `SELECT * FROM (SELECT ... OVER(...) FROM t) s` |
| Q2 | Wrap window inside JOIN | `JOIN (SELECT 1) t2 ON true` |
| Q3 | Add UNION ALL wrapper | `FROM (t1 UNION ALL t2) u` |
| Q4 | Add ORDER BY outside | `ORDER BY random()` |
| Q5 | Add LIMIT/OFFSET outside | Window computed before LIMIT |
| Q6 | Add DISTINCT outside | Semantic window must not change |
| Q7 | Add WHERE filter outside window | Window precomputed |

### TOP 10 Mutation có hiệu quả cao nhất (nên làm cho PoC)

1. **O1** – Redundant ORDER BY column (`ORDER BY x, x`)
2. **O2** – Order-preserving transform (`x+0`)
3. **P1** – Add redundant PARTITION BY key
4. **P3** – Add unique column to PARTITION BY
5. **F1** – Shrink frame
6. **F3** – CURRENT ROW equivalence
7. **F8** – Switch ROWS ↔ RANGE (unique ORDER)
8. **V1** – Arithmetic identity
9. **Q1** – Wrap in subquery
10. **Q3** – UNION ALL wrapper

**Đề xuất mạnh nhất cho PoC nhanh:**  
**O1: `ORDER BY x → ORDER BY x, x`**  
→ Semantics 100% bất biến, DBMS hay tối ưu sai, ít effort, sinh query cực nhanh.

## 4. Generate Window Function Query Algorithm Idea

### Thuật toán 1: OVER-Spec Random Builder (OSRB) – “Build-from-Components”

Dễ nhất, hiệu quả cao, dễ nhúng vào SQLancer.

```text
OVER_SPEC ::= OVER ( PARTITION_CLAUSE? ORDER_CLAUSE? FRAME_CLAUSE? )
```

**Pseudocode:**
```pseudocode
Function generateWindowSpec():
    if randomBoolean(): add partition
    always add orderClause
    if randomBoolean(): add frameClause
```

Ưu điểm: tối giản, sinh query hợp lệ 99%, dễ mutate.

### Thuật toán 2: OVER-Mutation Tree Generator (OMTG) – “Grow then Mutate”

Lấy cảm hứng từ PQS:
1. Sinh spec tối thiểu
2. Áp dụng chuỗi mutation operators

**Mutation Operators:**
- AddPartitionCol
- MutateOrderExpr (`x→x+0`)
- SwitchRowsRange
- ExpandFrame
- v.v.

Ưu điểm: cực mạnh cho tìm bug tối ưu hóa, giống phong cách PQS.

### Thuật toán 3: Grammar-Guided Stochastic Generator (GGSG)

Dùng grammar + xác suất như SQLsmith.

Ưu điểm: formal, dễ track coverage, phù hợp paper.

### So sánh & Gợi ý

| Mục tiêu                     | Chọn thuật toán |
|-----------------------------|-----------------|
| PoC 1 tuần, ít code         | OSRB            |
| Linh hoạt, giống PQS        | OMTG            |
| Thesis/paper, cần formalism | GGSG            |

**Bonus Recommendation:**
- Muốn chạy nhanh + tìm bug chắc chắn → **OSRB**
- Muốn linh hoạt + mutation đa dạng → **OMTG**
- Muốn viết paper có grammar → **GGSG**

---

**Done!** Toàn bộ tài liệu đã được chuyển hoàn toàn sang Markdown chuẩn, sạch, có cấu trúc rõ ràng, dễ đọc và dễ dùng trong Cursor.
``` 

Bạn chỉ cần copy toàn bộ đoạn trên dán vào file `.md` là xong!