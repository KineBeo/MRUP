# MRUP Oracle Report - Compilation Guide

## Overview

This LaTeX report documents the MRUP (MR-Union-Partition) Oracle for testing window functions in SQLite. The report is structured as a mini graduate thesis ("Du An Cong Nghe") following Vietnamese academic standards.

## File Structure

```
latex_report/
├── main.tex                    # Main document file
├── Chap0_Introduction.tex      # Introduction (Mở đầu)
├── Chap1_Background.tex        # Background (Kiến thức nền tảng)
├── Chap2_RelatedWork.tex       # Related Work (Công trình liên quan)
├── Chap3_Design.tex            # Design & Implementation (Thiết kế và triển khai)
├── Chap4_Experiments.tex       # Experiments (Thí nghiệm và đánh giá)
├── Chap5_Conclusion.tex        # Conclusion (Kết luận)
├── cover/                      # Cover pages (need to be created)
│   ├── Biangoai.tex           # Front cover
│   ├── Biatrong.tex           # Inner cover
│   ├── loicamdoan.tex         # Declaration
│   ├── loicamon.tex           # Acknowledgments
│   ├── tomtat.tex             # Abstract
│   └── phuluc.tex             # Appendix
└── figures/                    # Figures directory (need to add images)
```

## Required LaTeX Packages

The report uses the following main packages:
- `times` - Font
- `vietnam` - Vietnamese language support
- `amsmath, amstext, amssymb` - Math symbols
- `tikz, pgfplots` - Diagrams
- `algorithm, algpseudocode` - Algorithms
- `natbib` - Bibliography
- `hyperref` - Hyperlinks

## Compilation Steps

### Method 1: Using pdflatex (Recommended)

```bash
cd latex_report
pdflatex main.tex
bibtex main
pdflatex main.tex
pdflatex main.tex
```

### Method 2: Using latexmk (Automated)

```bash
cd latex_report
latexmk -pdf main.tex
```

### Method 3: Using Overleaf

1. Upload all `.tex` files to Overleaf
2. Set main document to `main.tex`
3. Compile with pdfLaTeX

## Missing Files to Create

You need to create the following files in the `cover/` directory:

### 1. Biangoai.tex (Front Cover)
```latex
\begin{center}
{\fontsize{13}{15}\selectfont ĐẠI HỌC QUỐC GIA HÀ NỘI}\\
{\fontsize{13}{15}\selectfont TRƯỜNG ĐẠI HỌC CÔNG NGHỆ}\\
\vspace{1cm}
\includegraphics[width=3cm]{logo.png}\\
\vspace{1cm}
{\fontsize{16}{18}\selectfont\bfseries [YOUR NAME]}\\
\vspace{2cm}
{\fontsize{18}{20}\selectfont\bfseries PHÁT TRIỂN ORACLE KIỂM THỬ LỖI LOGIC\\
WINDOW FUNCTIONS TRONG SQLITE\\
SỬ DỤNG QUAN HỆ METAMORPHIC MRUP}\\
\vspace{1cm}
{\fontsize{14}{16}\selectfont BÁO CÁO DỰ ÁN CÔNG NGHỆ}\\
\vspace{2cm}
{\fontsize{13}{15}\selectfont Hà Nội - 2024}
\end{center}
```

### 2. Biatrong.tex (Inner Cover)
Similar to Biangoai.tex but with more details (advisor, student ID, etc.)

### 3. loicamdoan.tex (Declaration)
```latex
\chapter*{Lời cam đoan}
Tôi xin cam đoan đây là công trình nghiên cứu của riêng tôi...
```

### 4. loicamon.tex (Acknowledgments)
```latex
\chapter*{Lời cảm ơn}
Tôi xin chân thành cảm ơn...
```

### 5. tomtat.tex (Abstract)
```latex
\chapter*{Tóm tắt}
Đề tài nghiên cứu về phát triển MRUP Oracle...
```

### 6. phuluc.tex (Appendix)
```latex
\chapter*{Phụ lục}
% Add supplementary materials
```

## Report Structure

### Chapter 0: Introduction (Mở đầu)
- Lý do chọn đề tài (Motivation)
- Mục tiêu nghiên cứu (Objectives)
- Phương pháp nghiên cứu (Methodology)
- Đóng góp của đề tài (Contributions)
- Bố cục của báo cáo (Structure)

### Chapter 1: Background (Kiến thức nền tảng)
- Window Functions trong SQL
  - Khái niệm và ứng dụng
  - Các thành phần (PARTITION BY, ORDER BY, Frame)
  - Phân loại (Ranking, Aggregate, Value functions)
- SQLite và Window Functions
  - Tổng quan về SQLite
  - Hạn chế của SQLite
- Thách thức trong kiểm thử Window Functions

### Chapter 2: Related Work (Công trình liên quan)
- Các kỹ thuật kiểm thử DBMS hiện đại
  - Differential Testing
  - PQS (Pivoted Query Synthesis)
  - TLP (Ternary Logic Partitioning)
  - NoREC (Non-Optimizing Reference Engine)
  - EET (Equivalent Expression Testing)
- Khoảng trống nghiên cứu về kiểm thử Window Functions
- Lý do cần thiết phát triển MRUP Oracle

### Chapter 3: Design & Implementation (Thiết kế và triển khai)
- Quan hệ Metamorphic MRUP
  - Định nghĩa quan hệ
  - Chứng minh tính đúng đắn
  - So sánh với PQS
- Kiến trúc MRUP Oracle
  - Query Generator
  - Mutation Operators
  - Result Comparator
- Constraint System (C0-C5)
- Mutation Strategies
  - Window Spec Mutations
  - CASE WHEN Mutations
- Result Comparator
  - Three-Layer Comparison
  - Type-Aware Comparison
- Triển khai trong SQLancer

### Chapter 4: Experiments (Thí nghiệm và đánh giá)
- Thiết kế thí nghiệm
- Metrics đánh giá
  - Query Diversity Metrics
  - Performance Metrics
  - Effectiveness Metrics
- Kết quả thí nghiệm
  - Query Diversity
  - Execution Performance
  - Comparison với các Oracle khác
  - Cross-Version Testing
- Phân tích và thảo luận
  - Điểm mạnh
  - Hạn chế
- Hướng cải tiến (Roadmap Tier 1-4)

### Chapter 5: Conclusion (Kết luận)
- Tổng kết
- Hạn chế
- Hướng nghiên cứu tiếp theo
- Ý nghĩa thực tiễn

## Key Contributions Highlighted in Report

1. **MRUP Metamorphic Relation**: H(t₁ ∪ t₂) = H(t₁) ∪ H(t₂)
2. **Constraint System (C0-C5)**: Ensures soundness with zero false positives
3. **Partition-Aware Comparison**: Three-layer comparator with type awareness
4. **Evidence-Based Mutations**: CASE WHEN mutations based on real MySQL bugs
5. **Integration with SQLancer**: Production-ready oracle for continuous testing

## Experimental Results Summary

- **Query Diversity**: 98.47% unique queries, 94.2% mutation coverage
- **Performance**: 70.3 queries/second, 14.23 ms/query average
- **False Positives**: 0% (zero false positives in 10,000 test cases)
- **Constraint Satisfaction**: 100% (all queries satisfy C0-C5)

## Future Work Roadmap

### Tier 1 (CRITICAL): Expected 10-18 bugs
- Add WHERE clauses
- Add edge case data (MIN_INT, MAX_INT, NULL)
- Add REAL type support

### Tier 2 (HIGH): Expected 17-33 bugs
- Add subqueries in SELECT
- Variable schema complexity
- Multiple window functions per query

### Tier 3 (MEDIUM): Expected 22-41 bugs
- Add missing window functions (NTILE, LAG, LEAD, etc.)
- Expression-based ORDER BY
- Multi-column PARTITION BY

## Troubleshooting

### Common Issues

1. **Missing figures**: Create a `figures/` directory and add placeholder images
2. **Missing cover files**: Create the cover files as described above
3. **Vietnamese characters not displaying**: Ensure `vietnam` package is installed
4. **Bibliography not compiling**: Run `bibtex main` after first `pdflatex` run

### Required LaTeX Distribution

- TeX Live 2020 or later (recommended)
- MiKTeX 2.9 or later (Windows)

## Contact

For questions about the MRUP Oracle implementation, refer to:
- Source code: `src/sqlancer/sqlite3/oracle/SQLite3MRUPOracle.java`
- Review document: `ORACLE_REVIEW_AND_ROADMAP.md`
- SQLancer repository: https://github.com/sqlancer/sqlancer

## License

This report is part of an academic project. The MRUP Oracle code is open source under the SQLancer license.

