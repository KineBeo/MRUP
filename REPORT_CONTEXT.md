# MRUP Oracle Report Context

## Project Overview
- **Title**: Phát triển Oracle kiểm thử lỗi logic Window Functions trong SQLite sử dụng quan hệ Metamorphic MRUP
- **Framework**: SQLancer (Database Management System testing framework)
- **Target DBMS**: SQLite3
- **Core Method**: MRUP (MR-UNION-PARTITION) Oracle for window function testing

## MRUP Oracle Design

### Core Metamorphic Relation
```
H(t1 ∪ t2) = H(t1) ∪ H(t2)
```
Where:
- H() is a window function query with PARTITION BY clause
- t1, t2 are tables with disjoint partition values
- ∪ is UNION ALL operation
- This relation exploits partition locality property of window functions

### Constraint System (C0-C5)
1. **C0**: PARTITION BY is mandatory (ensures partition locality)
2. **C1**: PARTITION BY only uses 'dept' column (ensures disjoint partitions)  
3. **C2**: ORDER BY only uses deterministic columns (salary, age)
4. **C3**: No FRAME for ranking functions (SQLite limitation)
5. **C4**: RANGE only with single ORDER BY column (SQLite limitation)
6. **C5**: Only deterministic functions (avoids false positives)

### Three-Layer Comparator Architecture
1. **Layer 1**: Cardinality check (fast fail)
2. **Layer 2**: MRUP normalization (semantic sorting)
3. **Layer 3**: Per-partition comparison (exact match)

## Chapter Structure
1. **Chap0_Introduction.tex** - Introduction (Mở đầu)
2. **Chap1_Background.tex** - Background on Window Functions
3. **Chap2_RelatedWork.tex** - Survey of DBMS testing techniques (PQS, NoREC, TLP, EET)
4. **Chap3_Design.tex** - MRUP Oracle design and implementation
5. **Chap4_Experiments.tex** - Experiments and evaluation
6. **Chap5_Conclusion.tex** - Conclusion and future work

## Research Questions (for Chapter 4 Evaluation)
- **RQ1**: Constraint Enforcement - Does MRUP correctly enforce its constraint system?
- **RQ2**: Mutation Coverage - What is the distribution and diversity of mutations?
- **RQ3**: Comparator Stability - Does the 3-layer comparator produce consistent results?
- **RQ4**: Oracle Throughput - What is the test case generation and execution efficiency?

## Key Technical Components
- **SQLite3MRUPOracle.java** - Main oracle implementation (1126 lines)
- **SQLite3MRUPTablePairGenerator.java** - Generates table pairs with disjoint partitions
- **SQLite3MRUPIdentityMutator.java** - Identity transformation mutations
- **SQLite3MRUPCaseMutator.java** - CASE WHEN wrapper mutations (5 strategies)
- **SQLite3MRUPMutationOperator.java** - Window spec mutations (Top 10 strategies)
- **SQLite3MRUPTestCaseLogger.java** - File-based logging
- **SQLite3MRUPBugReproducer.java** - Bug report generation

## Mutation Strategies
### Window Spec Mutations (Top 10 from MRUP.md)
1. Redundant PARTITION BY
2. Order-preserving transforms
3. Frame boundary variations
4. NULLS FIRST/LAST variations
5. ASC/DESC variations
6. Multiple ORDER BY columns
7. Frame exclusion variations
8. ROWS vs RANGE variations
9. UNBOUNDED variations
10. Complex BETWEEN frames

### CASE WHEN Mutations (5 strategies, 100% rate)
1. Constant Condition (30%)
2. Window Function in WHEN (25%)
3. Different Window Functions (20%)
4. Identical Branches (15%)
5. NULL Handling (10%)

## Experimental Methodology
- **File-based logging** for all test cases (mrup_logs/)
- **Automated bug reproduction** scripts
- **Comprehensive experiment runner** (run_chapter4_experiment.py)
- **Detailed analysis tools** for extracting metrics
- **3-layer comparison validation** to avoid false positives

## Academic Context
- **Report Type**: "Du An Cong Nghe" (Technology Project) - Vietnamese academic format
- **Language**: Academic Vietnamese with technical English terminology
- **Style**: Natural flowing paragraphs (not bullet-point lists)
- **Structure**: Follows SQLancer/OSDI paper formatting conventions
- **Evaluation Focus**: Oracle quality and engineering discipline (not bug discovery)

## LaTeX Compilation Requirements
- **Document Class**: book (a4paper, 12pt, oneside)
- **Packages**: times, vietnam, amsmath, tikz, natbib, hyperref, algorithm, titlesec, fancyvrb, appendix, enumitem, booktabs, rotating, hhline, colortbl, afterpage, mathtools, setspace, lipsum, geometry, pdflscape, apacite, array, tabularx, multicol, color, fancyhdr, fncychap, multirow, graphicx, algpseudocode, etoolbox, circuitikz, pgfplots
- **Font**: Times New Roman
- **Language**: Vietnamese
- **Format**: Book class, A4 paper, 12pt font
- **Cover pages**: Created in cover/ directory (Biangoai, Biatrong, loicamdoan, loicamon, tomtat)
- **Bibliography**: Harvard-style citations using apacite
- **Page Setup**: Left 3cm, Right 2cm, Top 2.5cm, Bottom 3cm margins
- **Chapter Format**: \setcounter{chapter}{0} for Chap1_Background, \setcounter{chapter}{4} for Chap5_Conclusion
- **Section Format**: No paragraph indentation at start of sections (using \noindent)
- **Text Formatting**: No italic throughout the report
- **Special Formatting**: \noindent at beginning of sections and chapters, \indent for subsequent paragraphs

## LaTeX Document Structure (main.tex)
\documentclass[a4paper,12pt,oneside]{book}%extreport
\usepackage{times}
\usepackage[utf8]{vietnam}
\usepackage{amstext, amsmath,latexsym,amsbsy,amssymb, amsthm,amsfonts,multicol, nccmath}
\usepackage[left=3cm,right=2cm,top=2.5cm,bottom=3cm,footskip=40pt]{geometry}
\usepackage{titlesec}
\usepackage[labelsep=period]{caption}
\usepackage{subfigure}
\usepackage{graphicx}
\usepackage{algorithm}
\usepackage{algpseudocode}
\usepackage{tikz}
\usepackage[hidelinks, unicode]{hyperref}

% Page layout settings
\setlength{\parindent}{1cm}
\setcounter{secnumdepth}{4}
\renewcommand{\baselinestretch}{1.4}

% Chapter and section formatting
\titleformat{\chapter}[display]
  {\fontsize{14}{16}\selectfont\bfseries\centering}
  {\MakeUppercase{\chaptertitlename}\ \thechapter}{0pt}{\fontsize{14}{16}\selectfont\MakeUppercase}
\titlespacing{\chapter}{0pt}{0pt}{40pt}
\titleformat*{\section}{\fontsize{14}{14}\selectfont\bfseries}
\titleformat*{\subsection}{\fontsize{14}{14}\selectfont\bfseries}

% Table of contents formatting
\usepackage[subfigure]{tocloft}
\renewcommand{\cftfigfont}{Hình~}
\renewcommand{\cfttabfont}{Bảng~ }
\floatname{algorithm}{Thuật toán}

% Document begins here
\begin{document}
\fontsize{13}{15.5}\selectfont

% Front matter
\pagestyle{empty}
\input{cover/Biangoai}
\newpage
\pagestyle{plain}
\pagenumbering{gobble}
\input{cover/Biatrong}
\newpage
% More front matter...

% Table of contents and lists
\pagenumbering{roman}
\tableofcontents
\listoffigures
\listoftables

% Main content
\pagenumbering{arabic}
\input{chapter/Chap0_Introduction}
\input{chapter/Chap1_Background}
\input{chapter/Chap2_RelatedWork}
\input{chapter/Chap3_Design}
\input{chapter/Chap4_Experiments}
\input{chapter/Chap5_Conclusion}

% Bibliography
\begin{thebibliography}{xx}
	\section*{Tiếng Anh}
	% Bibliography entries...
\end{thebibliography}

\end{document}

## Working Files Location
- **LaTeX source**: `/Users/kienbeovl/Desktop/Fuzzing/MRUP/latex_report/`
- **Java source**: `/Users/kienbeovl/Desktop/Fuzzing/MRUP/src/sqlancer/sqlite3/oracle/`
- **Log files**: `/Users/kienbeovl/Desktop/Fuzzing/MRUP/mrup_logs/`
- **Analysis tools**: Root directory Python scripts

## LaTeX Chapter Formatting Details

### Chap1_Background.tex Format
\clearpage
\phantomsection

\setcounter{chapter}{0}
\chapter{Kiến thức nền tảng}

\noindent Chương này giới thiệu các kiến thức nền tảng về window functions trong SQL và SQLite, hệ quản trị cơ sở dữ liệu được sử dụng trong nghiên cứu.

\section{Window Functions trong SQL}

\subsection{Khái niệm cơ bản}

\noindent Window functions là tính năng được giới thiệu trong SQL:2003, cho phép thực hiện phép tính trên tập hợp các hàng liên quan đến hàng hiện tại mà vẫn giữ nguyên số lượng hàng. Khác với GROUP BY làm giảm số hàng, window functions thêm cột tính toán cho mỗi hàng dựa trên "cửa sổ" các hàng liên quan.

Cú pháp tổng quát bao gồm tên hàm và mệnh đề OVER chứa window specification với ba thành phần: PARTITION BY chia dữ liệu thành các phân vùng độc lập, ORDER BY xác định thứ tự các hàng, và frame clause xác định tập hợp hàng được sử dụng.

\begin{table}[H]
	\centering
    \small
    \begin{tabular}{|p{7cm}|p{7cm}|}
\hline
\textbf{GROUP BY (Aggregate)} & \textbf{Window Function} \\ \hline
\texttt{SELECT dept, AVG(salary)} & \texttt{SELECT name, dept, salary,} \\
\texttt{FROM employees} & \texttt{AVG(salary) OVER (PARTITION BY dept)} \\
\texttt{GROUP BY dept;} & \texttt{FROM employees;} \\ \hline
\textbf{Kết quả:} 2 hàng (2 phòng ban) & \textbf{Kết quả:} 5 hàng (giữ nguyên) \\ \hline
\end{tabular}
    \caption{So sánh GROUP BY và Window Function}
    \label{tab:groupby_vs_window}
\end{table}

% Additional sections follow the same format with:
% - \noindent at the beginning of sections
% - \subsection{} for subsections
% - \subsubsection{} for subsubsections
% - Tables in [H] placement with custom formatting
% - No italic formatting throughout

### Chap5_Conclusion.tex Format
\clearpage
\phantomsection

\setcounter{chapter}{4}
\chapter{Kết luận và hướng nghiên cứu tiếp theo}

\noindent Đề tài \textbf{``Phát triển Oracle kiểm thử lỗi logic Window Functions trong SQLite sử dụng quan hệ Metamorphic MRUP''} đã thiết kế và triển khai thành công MRUP Oracle - một oracle kiểm thử chuyên biệt cho window functions dựa trên quan hệ metamorphic $H(t_1 \cup t_2) = H(t_1) \cup H(t_2)$. Hệ thống ràng buộc (C0-C5) đảm bảo tính đúng đắn của quan hệ, trong khi bộ so sánh 3 tầng xử lý kết quả một cách chính xác và ổn định. Kết quả đánh giá cho thấy MRUP Oracle có độ phủ 98.47\%, hiệu suất 70.3 truy vấn/giây, và tỷ lệ dương tính giả là 0\%, vượt trội hơn các oracle hiện tại về độ tin cậy.

\indent Tuy nhiên, phiên bản hiện tại vẫn còn một số hạn chế cần khắc phục. Do tập trung vào truy vấn đơn giản và schema cố định, MRUP Oracle chưa phát hiện được lỗi mới trong SQLite sau 20,000 test cases. Các truy vấn chủ yếu chỉ chứa một window function duy nhất, không có mệnh đề WHERE, subquery hay JOIN, làm giảm khả năng khám phá các lỗi tương tác phức tạp. Ngoài ra, phạm vi các hàm window được kiểm thử còn hạn chế, chủ yếu bao gồm các hàm cơ bản như SUM, AVG, COUNT, ROW\_NUMBER, trong khi các hàm phức tạp hơn như LAG, LEAD, NTILE chưa được khai thác triệt để.

\indent Hướng phát triển trong tương lai sẽ tập trung vào việc mở rộng phạm vi kiểm thử thông qua việc tích hợp các thành phần phức tạp hơn vào truy vấn, bao gồm mệnh đề WHERE, biểu thức trong ORDER BY, và subquery. Việc mở rộng để hỗ trợ các hàm window đa dạng hơn như LAG/LEAD và NTILE sẽ giúp khám phá không gian lỗi rộng hơn. Song song đó, việc tăng tính đa dạng của dữ liệu kiểm thử với các trường hợp đặc biệt (giá trị rìa, NULL, số thực) sẽ cải thiện khả năng phát hiện lỗi liên quan đến xử lý kiểu dữ liệu. Những cải tiến này sẽ được thực hiện từng bước cẩn trọng để duy trì tính chính xác của oracle, hướng tới việc phát triển MRUP Oracle thành một công cụ kiểm thử sản phẩm có thể phát hiện lỗi trong nhiều hệ quản trị cơ sở dữ liệu khác nhau.

## Evaluation Strategy
- **Log file analysis** from 10,000+ test cases
- **Metric extraction** using analysis tools
- **Constraint verification** data
- **Mutation application statistics**
- **Comparator behavior logs**
- **Throughput measurements**

## Current Status
- All chapters written and completed
- Ready for compilation
- Evaluation data available from experiments
- Focus now on refining content to match natural academic Vietnamese style