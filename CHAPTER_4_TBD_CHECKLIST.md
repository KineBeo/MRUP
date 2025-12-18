# Chapter 4 [TBD] Checklist

Quick reference for all placeholders that need to be filled after experiments.

---

## Table 1: Constraint Satisfaction (Line ~222-245)

**Location**: Section 4.4.1 (RQ1)

**What to fill**:
```latex
C0: PARTITION BY bắt buộc & [TBD] & [TBD] & [TBD]\% \\
C1: Chỉ dùng dept & [TBD] & [TBD] & [TBD]\% \\
C2: Chỉ dùng salary/age & [TBD] & [TBD] & [TBD]\% \\
C3: Không frame cho ranking & [TBD] & [TBD] & [TBD]\% \\
C4: RANGE với 1 cột ORDER BY & [TBD] & [TBD] & [TBD]\% \\
C5: Hàm xác định & [TBD] & [TBD] & [TBD]\% \\
\textbf{Tổng thể} & [TBD] & [TBD] & [TBD]\% \\
```

**Data needed**: For each constraint, count:
- Satisfied (should be 10,000)
- Violated (should be 0)
- Rate (should be 100.0%)

**Analysis paragraph** (Line ~247):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

**Meaning paragraph** (Line ~251):
```latex
\textbf{Ý nghĩa cho RQ1:} [TBD]
```

---

## Table 2: Mutation Application Rates (Line ~261-277)

**Location**: Section 4.4.2.1 (RQ2)

**What to fill**:
```latex
Window Spec & [TBD] & [TBD] & [TBD]\% & \textasciitilde90\% \\
Identity Wrapper & [TBD] & [TBD] & [TBD]\% & \textasciitilde98\% \\
CASE WHEN & [TBD] & [TBD] & [TBD]\% & 100\% \\
```

**Data needed**: For each mutation type:
- Applied (count)
- Skipped (count)
- Rate (%)
- Target (already filled)

**Analysis paragraph** (Line ~279):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

---

## Table 3: CASE WHEN Strategy Distribution (Line ~287-309)

**Location**: Section 4.4.2.2 (RQ2)

**What to fill**:
```latex
Constant Condition & [TBD] & [TBD]\% & 30\% \\
Window Function in WHEN & [TBD] & [TBD]\% & 25\% \\
Different Functions & [TBD] & [TBD]\% & 20\% \\
Identical Branches & [TBD] & [TBD]\% & 15\% \\
NULL Handling & [TBD] & [TBD]\% & 10\% \\
\textbf{Tổng} & [TBD] & \textbf{100\%} & \textbf{100\%} \\
```

**Data needed**: For each strategy:
- Count
- Rate (%)
- Target (already filled)

**Analysis paragraph** (Line ~311):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

---

## Table 4: Schema and Query Diversity (Line ~319-361)

**Location**: Section 4.4.2.3 (RQ2)

**What to fill** (14 rows):
```latex
Số cột (3-7) & [TBD] & 4-5 trung bình & [TBD] \\
Kiểu: INTEGER & [TBD]\% & 40\% & [TBD] \\
Kiểu: REAL & [TBD]\% & 30\% & [TBD] \\
Kiểu: TEXT & [TBD]\% & 30\% & [TBD] \\
Tỷ lệ NULL & [TBD]\% & \textasciitilde30\% & [TBD] \\
Tỷ lệ edge case & [TBD]\% & \textasciitilde15\% & [TBD] \\
Aggregate function & [TBD]\% & 98\% & [TBD] \\
Ranking function & [TBD]\% & 2\% & [TBD] \\
ORDER BY: 1 cột & [TBD]\% & \textasciitilde33\% & [TBD] \\
ORDER BY: 2 cột & [TBD]\% & \textasciitilde44\% & [TBD] \\
ORDER BY: 3 cột & [TBD]\% & \textasciitilde22\% & [TBD] \\
Có frame & [TBD]\% & \textasciitilde50\% & [TBD] \\
Frame: ROWS & [TBD]\% & varies & [TBD] \\
Frame: RANGE & [TBD]\% & varies & [TBD] \\
```

**Data needed**: For each characteristic:
- Observed value (%, average, or count)
- Target (already filled)
- Status (✓ if within acceptable range)

**Analysis paragraph** (Line ~363):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

**Meaning paragraph** (Line ~365):
```latex
\textbf{Ý nghĩa cho RQ2:} [TBD]
```

---

## Table 5: Comparator Behavior (Line ~375-397)

**Location**: Section 4.4.3.1 (RQ3)

**What to fill**:
```latex
Tầng 1: Cardinality & [TBD] & [TBD] & [TBD]\% \\
Tầng 2: Normalization & [TBD] & [TBD] & [TBD]\% \\
Tầng 3: Per-Partition & [TBD] & [TBD] & [TBD]\% \\
Partition Disjointness & [TBD] & [TBD] & [TBD]\% \\
Type-Aware Comparison & [TBD] & [TBD] & [TBD]\% \\
```

**Data needed**: For each layer/check:
- Reached (count)
- Passed (count)
- Pass rate (%)

**Analysis paragraph** (Line ~399):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

---

## Table 6: Repeated Execution Consistency (Line ~407-425)

**Location**: Section 4.4.3.2 (RQ3)

**What to fill**:
```latex
Result variance & [TBD] \\
False positive & [TBD] \\
Constraint violation & [TBD] \\
Deterministic rate & [TBD]\% \\
```

**Data needed**: From 100 test cases × 10 runs:
- Result variance (should be 0.0)
- False positive count (should be 0)
- Constraint violation count (should be 0)
- Deterministic rate (should be 100%)

**Analysis paragraph** (Line ~427):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

**Meaning paragraph** (Line ~431):
```latex
\textbf{Ý nghĩa cho RQ3:} [TBD]
```

---

## Table 7: Oracle Throughput (Line ~441-477)

**Location**: Section 4.4.4.1 (RQ4)

**What to fill**:
```latex
Test case/giây (trung bình) & [TBD] \\
Test case/giây (median) & [TBD] \\
Thời gian/test case (trung bình) & [TBD] ms \\
Thời gian/test case (median) & [TBD] ms \\
Sinh bảng & [TBD] ms \\
Sinh truy vấn & [TBD] ms \\
Áp dụng đột biến & [TBD] ms \\
Thực thi truy vấn & [TBD] ms \\
So sánh kết quả & [TBD] ms \\
Thông lượng (1 giờ) & [TBD] test case \\
Thông lượng (24 giờ) & [TBD] test case \\
```

**Data needed**: Timing data from logs:
- Overall throughput (tests/sec)
- Time per test case (ms)
- Phase breakdown (ms per phase)
- Projected throughput (calculated)

**Analysis paragraph** (Line ~479):
```latex
\textbf{Phân tích:} [TBD sau khi chạy thí nghiệm]
```

**Meaning paragraph** (Line ~483):
```latex
\textbf{Ý nghĩa cho RQ4:} [TBD]
```

---

## Discussion Section Placeholders

### Section 4.5.1: Mức độ sẵn sàng của oracle

**Line ~492** (first paragraph):
```latex
[TBD: Điền sau khi có kết quả] chứng minh rằng oracle tuân thủ chính xác...
```

**What to write**: Synthesize RQ1-RQ4 findings. Example:
> "Kết quả từ 10,000 test case chứng minh rằng oracle tuân thủ chính xác các ràng buộc của nó (RQ1: 100% constraint satisfaction), đạt được sự đa dạng mục tiêu trong các chiến lược đột biến (RQ2: mutation rates within ±5% of target), duy trì bộ so sánh xác định không có false positive (RQ3: 0 false positives across 1,000 repeated executions), và đạt thông lượng thực tế cho kiểm thử liên tục (RQ4: X tests/sec)."

**Line ~501** (comparison paragraph):
```latex
[TBD: So sánh thông lượng] cho thấy hiệu suất của MRUP điển hình...
```

**What to write**: Compare throughput with typical SQL testing tools. Example:
> "Thông lượng X test case/giây của MRUP cho thấy hiệu suất điển hình cho các SQL testing tool (PQS: ~85 q/s, TLP: ~125 q/s được báo cáo trong các công bố trước đây). Sự khác biệt chủ yếu do MRUP thực thi 3 truy vấn mỗi test case (t1, t2, union) trong khi một số oracle khác chỉ thực thi 1-2 truy vấn."

---

## Summary Section Placeholders

### Section 4.6: Tóm tắt

**Line ~532** (RQ1 result):
```latex
\item \textbf{RQ1} kiểm chứng rằng hệ thống ràng buộc được thực thi chính xác. [TBD: Kết quả]
```

**What to write**: Example:
> "100% constraint satisfaction trên 10,000 test case, không có vi phạm nào."

**Line ~533** (RQ2 result):
```latex
\item \textbf{RQ2} định lượng sự đa dạng và hiệu quả của các chiến lược đột biến. [TBD: Kết quả]
```

**What to write**: Example:
> "Tất cả các chiến lược đột biến đạt tỷ lệ mục tiêu (±5%), với mutation application rate: window spec 89.5%, identity 98.1%, CASE WHEN 100%."

**Line ~534** (RQ3 result):
```latex
\item \textbf{RQ3} chứng minh rằng bộ so sánh là xác định và không có false positive. [TBD: Kết quả]
```

**What to write**: Example:
> "Không có false positive nào trong 10,000 test case; tính xác định 100% qua 1,000 lần thực thi lặp lại."

**Line ~535** (RQ4 result):
```latex
\item \textbf{RQ4} thiết lập các đặc tính hiệu suất cơ bản cho kiểm thử liên tục. [TBD: Kết quả]
```

**What to write**: Example:
> "Thông lượng X test case/giây, cho phép chạy hàng triệu test case trong vài giờ. Oracle overhead chỉ chiếm 25% tổng thời gian."

**Line ~538** (synthesis paragraph):
```latex
Kết quả chứng minh rằng MRUP là một oracle có kỷ luật, ổn định, sẵn sàng cho triển khai. [TBD: Tổng hợp] xác nhận...
```

**What to write**: Example:
> "Kết quả từ cả 4 RQ"

---

## Quick Fill Template

For each table/paragraph, use this workflow:

1. **Run experiment** → Get log file
2. **Parse log** → Extract metric
3. **Calculate** → Compute rates/percentages
4. **Format** → Round to 1-2 decimal places
5. **Write analysis** → Interpret what the number means
6. **Write synthesis** → Connect to oracle quality

---

## Verification Checklist

Before considering Chapter 4 complete:

- [ ] All 7 tables filled (no [TBD] in tables)
- [ ] All 8 analysis paragraphs written
- [ ] All 4 "Ý nghĩa cho RQ" paragraphs written
- [ ] 2 discussion placeholders filled
- [ ] 5 summary placeholders filled
- [ ] Total [TBD] count: **0**
- [ ] LaTeX compiles without errors
- [ ] All numbers internally consistent
- [ ] Percentages add up to 100% where appropriate
- [ ] Analysis matches the data (not contradictory)

---

## Search Command

To find all remaining [TBD] markers:

```bash
cd /home/kienbeovl/Desktop/DBMS_Oracles/MRUP/latex_report
grep -n "\[TBD\]" Chap4_Experiments.tex
```

Expected initial count: **~40 [TBD] markers**  
Target final count: **0**

---

## Estimated Time

- Adding logging code: 2-4 hours
- Running experiments: 4-6 hours (mostly waiting)
- Parsing logs: 1-2 hours
- Filling tables: 1 hour
- Writing analysis: 2-3 hours
- Proofreading: 1 hour

**Total**: 11-17 hours (spread over 1-2 days)

---

Good luck! Systematically work through each [TBD] marker, and you'll have a publication-ready Chapter 4. 🎯

