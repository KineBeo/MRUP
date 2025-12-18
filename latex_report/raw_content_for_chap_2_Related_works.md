Hiện tại, tôi sẽ cung cấp các nội dung chi tiết về các oracle PQS, NoREC, TLP và EET dựa trên các tài liệu đã cho để bạn sử dụng trong báo cáo của mình.

---

### I. Pivoted Query Synthesis (PQS)

#### Overview Approach
PQS là một phương pháp tiếp cận mới và tổng quát được thiết kế để phát hiện các lỗi logic trong Hệ thống Quản lý Cơ sở Dữ liệu (DBMS). Ý tưởng cốt lõi của PQS là giải quyết vấn đề oracle cho một hàng đơn lẻ, được chọn ngẫu nhiên, gọi là **hàng trục (pivot row)**, bằng cách **tổng hợp một truy vấn mà tập kết quả của nó bắt buộc phải chứa hàng trục đó**,. Nếu DBMS không thể truy xuất hàng trục, điều đó cho thấy một lỗi logic đã được phát hiện.

PQS hoạt động bằng cách:
1. Tạo một cơ sở dữ liệu ngẫu nhiên.
2. Chọn ngẫu nhiên một hàng trục từ các bảng/view.
3. Tạo ra các biểu thức ngẫu nhiên (dưới dạng cây cú pháp trừu tượng - AST).
4. **Đánh giá các biểu thức này dựa trên hàng trục** và điều chỉnh chúng để đảm bảo chúng trả về giá trị TRUE (quá trình chỉnh sửa - rectification),.
5. Sử dụng các biểu thức đã được chỉnh sửa này trong các mệnh đề `WHERE` và/hoặc `JOIN` của một truy vấn được tạo ngẫu nhiên,.
6. So sánh kết quả trả về của DBMS với kỳ vọng: nếu hàng trục không được chứa trong tập kết quả, một lỗi logic đã được phát hiện,.

**Minh họa cách tiếp cận (Dựa trên Hình 1 trong tài liệu gốc):**
Quy trình PQS bao gồm 7 bước chính:
*   Bước 1: Tạo ngẫu nhiên các bảng và hàng trong cơ sở dữ liệu,.
*   Bước 2: Chọn ngẫu nhiên một hàng từ mỗi bảng để tạo thành hàng trục,.
*   Bước 3: Tạo các biểu thức ngẫu nhiên và đánh giá chúng dựa trên các hàng đã chọn.
*   Bước 4: Chỉnh sửa các biểu thức để chúng luôn trả về TRUE cho hàng trục đó.
*   Bước 5: Tạo truy vấn ngẫu nhiên sử dụng các biểu thức này trong mệnh đề `WHERE` hoặc `JOIN`.
*   Bước 6: Thực thi truy vấn bằng DBMS.
*   Bước 7: Xác minh rằng hàng trục được chứa trong tập kết quả.

#### Small Example
Ví dụ minh họa dựa trên lỗi nghiêm trọng được tìm thấy trong SQLite (Listing 1):
Lỗi được phát hiện khi sử dụng hàng trục `c0=NULL`.
*   Truy vấn chứa mệnh đề `WHERE c0 IS NOT 1`.
*   Vì `NULL IS NOT 1` đánh giá là TRUE, hàng `NULL` được kỳ vọng sẽ nằm trong kết quả.
*   Tuy nhiên, do lỗi logic trong tối ưu hóa (SQLite sử dụng partial index dựa trên giả định `c0 IS NOT 1` ngụ ý `c0 NOT NULL`), hàng `NULL` đã bị bỏ qua, dẫn đến kết quả chỉ có `{0}` thay vì `{0, NULL}`,.

#### Result
PQS đã chứng minh tính hiệu quả cao trong việc tìm kiếm lỗi.

**Điểm mạnh:**
*   **Tổng quát và hiệu quả cao** trong việc tìm kiếm lỗi logic.
*   PQS cung cấp **một oracle chính xác (exact oracle)** vì nó dựa trên việc đánh giá biểu thức của chính nó (sử dụng trình thông dịch AST) để xác định kết quả kỳ vọng cho hàng trục,.
*   Nỗ lực triển khai cho PQS thấp khi so sánh với kích thước của DBMS được kiểm tra, vì nó chỉ cần xử lý một hàng trục (thay vì toàn bộ cơ sở dữ liệu),.

**Số lượng và Phân loại Lỗi:**
PQS đã tìm thấy **121 lỗi duy nhất**, trong đó **96 lỗi đã được sửa hoặc xác minh**. Tổng cộng, **61 lỗi là lỗi logic**.

Bảng 2: Tình trạng các lỗi được báo cáo bởi PQS:
| DBMS | Fixed (Đã Sửa) | Verified (Đã Xác Minh) | Intended (Đúng ý đồ) | Duplicate (Trùng lặp) |
| :--- | :--- | :--- | :--- | :--- |
| SQLite | 64 | 0 | 4 | 2 |
| MySQL | 17 | 7 | 2 | 4 |
| PostgreSQL | 5 | 3 | 7 | 6 |
| **Tổng** | **86** | **10** | **13** | **12** |
*(Lưu ý: Bảng 2 chỉ liệt kê trạng thái đóng của các báo cáo lỗi, trong đó 78 lỗi đã được sửa bằng mã nguồn, 8 lỗi được sửa bằng tài liệu, và 10 lỗi đã được xác minh nhưng chưa sửa, tạo nên 96 lỗi thực sự,.)*

**Phân loại lỗi thực sự (Tổng cộng 96 lỗi):**
| DBMS | Logic (Lỗi logic) | Error (Lỗi bất ngờ) | SEGFAULT (Lỗi crash) |
| :--- | :--- | :--- | :--- |
| SQLite | 46 | 16 | 2 |
| MySQL | 14 | 9 | 1 |
| PostgreSQL | 1 | 7 | 0 |
| **Tổng** | **61** | **32** | **3** |

#### Điểm hạn chế
*   **Nỗ lực triển khai cao:** PQS đòi hỏi phải **triển khai lại thủ công** các toán tử và hàm được DBMS hỗ trợ để xác định xem một biểu thức ngẫu nhiên có đánh giá là TRUE hay không,,.
*   **Chỉ xác thực một phần kết quả:** PQS chỉ xác thực dựa trên hàng trục và **không thể phát hiện các hàng trùng lặp** bị bỏ sót hoặc được thêm vào một cách sai lầm. Do đó, nó không thể xác thực tính đúng đắn của **hàm tổng hợp (aggregate functions)**, **hàm cửa sổ (window functions)**, kích thước tập kết quả, hoặc thứ tự kết quả.
*   Không phù hợp để kiểm tra mệnh đề `OFFSET` và `LIMIT` vì chúng có thể loại trừ hàng trục khỏi tập kết quả.

---

### II. Non-Optimizing Reference Engine Construction (NoREC)

#### Overview Approach
NoREC là một kỹ thuật mới, tổng quát và hiệu quả về chi phí được đề xuất để tìm kiếm **lỗi tối ưu hóa (optimization bugs)** trong DBMS,. Lỗi tối ưu hóa là một loại lỗi logic khiến trình tối ưu hóa truy vấn tính toán tập kết quả không chính xác.

Ý tưởng cấp cao của NoREC là **so sánh kết quả của phiên bản DBMS có tối ưu hóa với phiên bản không thực hiện bất kỳ tối ưu hóa nào**. Vì việc tắt tối ưu hóa là khó khăn, ý tưởng cốt lõi của NoREC là **viết lại một truy vấn được tối ưu hóa (optimized query) thành một truy vấn mà DBMS không thể tối ưu hóa hiệu quả (unoptimized query)**,.

Cách tiếp cận cụ thể:
1. Tạo ngẫu nhiên một truy vấn tối ưu hóa, thường có dạng `SELECT * FROM t0 WHERE ϕ`.
2. **Biến đổi** truy vấn này thành truy vấn không tối ưu hóa có dạng `SELECT (ϕ IS TRUE) FROM t0`. Việc di chuyển mệnh đề `WHERE` đến sau từ khóa `SELECT` khiến DBMS phải truy xuất mọi bản ghi trong bảng, vô hiệu hóa hầu hết các cơ chế tối ưu hóa,.
3. **So sánh hai tập kết quả:** Đếm số lượng bản ghi trả về từ truy vấn tối ưu hóa và so sánh nó với số lần giá trị TRUE xuất hiện trong tập kết quả của truy vấn không tối ưu hóa. Nếu số lượng này khác nhau, lỗi tối ưu hóa được phát hiện.

**Minh họa cách tiếp cận (Dựa trên Hình 1 trong tài liệu gốc):**
*   Bước 1: Tạo truy vấn được tối ưu hóa ($Q$) (`SELECT * FROM t0 WHERE ϕ`). Trình tối ưu hóa có thể trả về $rs2$ (kết quả sai) thay vì $rs1$ (kết quả đúng),.
*   Bước 2: Dịch truy vấn tối ưu hóa sang truy vấn không tối ưu hóa ($Q'$) (`SELECT (ϕ IS TRUE) FROM t0`). Truy vấn này được mong đợi là có tiềm năng tối ưu hóa thấp,.
*   Bước 3: So sánh kết quả của $Q$ (vd: $|rs2|=3$) với số lượng giá trị TRUE trong $Q'$ (vd: $|rs3|=2$). Sự khác biệt (3 $\ne$ 2) chỉ ra lỗi,.

#### Small Example
Ví dụ minh họa dựa trên lỗi trong SQLite (Listing 1):
*   Truy vấn 1 (tối ưu hóa): `SELECT * FROM t0 WHERE t0.c0 GLOB '-*';` (dùng mệnh đề WHERE),. Kết quả mong đợi là một hàng, nhưng SQLite trả về `{}` (tập rỗng) do tối ưu hóa `LIKE` sai.
*   Truy vấn 2 (không tối ưu hóa): `SELECT t0.c0 GLOB '-*' FROM t0;` (di chuyển predicate sang SELECT). Truy vấn này đánh giá predicate trên mọi bản ghi và trả về `{TRUE}`.
*   Vì truy vấn 1 trả về 0 hàng, trong khi truy vấn 2 cho thấy predicate là TRUE 1 lần, sự không nhất quán này phát hiện ra lỗi.

#### Result
NoREC đã chứng minh tính hiệu quả, đặc biệt trong việc tìm kiếm các lỗi tối ưu hóa,.

**Điểm mạnh:**
*   **Phát hiện hiệu quả các lỗi tối ưu hóa**,.
*   **Yêu cầu nỗ lực triển khai thấp** và rất tổng quát vì nó dựa trên một quy trình dịch thuật đơn giản,,.
*   Đã tìm thấy hơn 100 lỗi bổ sung trong SQLite, mặc dù SQLite đã được PQS kiểm tra kỹ lưỡng.

**Số lượng và Phân loại Lỗi:**
NoREC đã tìm thấy **159 lỗi chưa từng được biết đến**, trong đó **141 lỗi đã được sửa**,.

Bảng 3: Phân loại lỗi được tìm thấy bởi NoREC (chỉ sử dụng NoREC oracle):
| DBMS | Logic (Optimization Bugs) | Error (Lỗi bất ngờ) | Crash (Lỗi crash/Assertion Failure) |
| :--- | :--- | :--- | :--- |
| SQLite | 39 | 30 | 41 (15 Crash, 26 Debug Assertion) |
| MariaDB | 5 | 0 | 1 (Crash) |
| PostgreSQL | 0 | 4 | 3 (Crash) |
| CockroachDB | 7 | 24 | 4 (Crash) |
| **Tổng** | **51** | **58** | **74** |

#### Điểm hạn chế
*   **Hạn chế về tính tổng quát:** Phương pháp này chủ yếu **chỉ áp dụng để kiểm tra các mệnh đề `WHERE`**,,.
*   Không áp dụng trực tiếp cho các mệnh đề `DISTINCT`, hàm tổng hợp (aggregate functions), hoặc hàm cửa sổ (window functions).
*   Không thể phát hiện lỗi nếu tối ưu hóa làm mất đi một lỗi/ngoại lệ được mong đợi (ví dụ: do đánh giá rút gọn `short-circuit evaluation` không nhất quán).
*   Không thể phát hiện lỗi nếu DBMS trả về tập kết quả sai nhưng có đúng số lượng bản ghi.

---

### III. Ternary Logic Partitioning (TLP)

#### Overview Approach
TLP là một sự cụ thể hóa của ý tưởng **Phân vùng Truy vấn (Query Partitioning)**, một kỹ thuật tổng quát và hiệu quả để tìm lỗi logic,.

Ý tưởng cốt lõi: Bắt đầu từ một truy vấn gốc ($Q$), TLP chia truy vấn đó thành nhiều **truy vấn phân vùng (partitioning queries)** ($Q'_0...Q'_{n-1}$), mỗi truy vấn tính toán một phần kết quả. Sau đó, các phần kết quả này được **kết hợp** bằng một toán tử ($RS(Q')$). Nếu kết quả kết hợp này khác với kết quả của truy vấn gốc ($RS(Q) \ne RS(Q')$), một lỗi logic được phát hiện.

TLP tận dụng logic ba giá trị của SQL (Ternary Logic): một predicate $\phi$ luôn đánh giá thành TRUE, FALSE, hoặc NULL,. TLP phân vùng truy vấn thành **ba biến thể predicate ternary**:
1. Truy vấn dựa trên predicate $\phi$ (TRUE)
2. Truy vấn dựa trên predicate NOT $\phi$ (FALSE)
3. Truy vấn dựa trên predicate $\phi$ IS NULL (NULL)

Các truy vấn phân vùng này sau đó được kết hợp bằng toán tử $\diamond$ (thường là `UNION ALL` hoặc `UNION`),.

**Minh họa cách tiếp cận (Dựa trên Hình 1 trong tài liệu gốc):**
Quy trình TLP minh họa:
*   Tạo truy vấn ngẫu nhiên $Q$.
*   $Q$ được phân vùng thành ba truy vấn con: $Q'_{p}$, $Q'_{\neg p}$, và $Q'_{p IS NULL}$ (sử dụng các biến thể predicate ternary).
*   Các tập kết quả $RS(Q')$ của chúng được kết hợp ($\diamond$) để tạo ra $RS(Q')$.
*   $RS(Q')$ được so sánh với $RS(Q)$. Nếu khác nhau, lỗi được phát hiện.

#### Small Example
Ví dụ minh họa dựa trên lỗi MySQL (Listing 1):
*   **Truy vấn gốc (O):** `SELECT * FROM t0, t1;` (Không có WHERE, trả về cross product). Kết quả: {0, -0} (Đúng).
*   **Predicate được chọn:** `t0.c0 = t1.c0`.
*   **Truy vấn Phân vùng (P):** Kết hợp ba truy vấn con bằng `UNION ALL`.
    1. `SELECT * FROM t0, t1 WHERE t0.c0 = t1.c0`
    2. `SELECT * FROM t0, t1 WHERE NOT(t0.c0 = t1.c0)`
    3. `SELECT * FROM t0, t1 WHERE (t0.c0 = t1.c0) IS NULL`.
*   MySQL đã xử lý sai truy vấn con đầu tiên (vì `0 = -0` đánh giá thành FALSE), dẫn đến kết quả kết hợp của $P$ là `{}` (tập rỗng).
*   Do $RS(O) \ne RS(P)$, lỗi logic đã được phát hiện.

#### Result
TLP là phương pháp tổng quát, hiệu quả và đã tìm thấy nhiều lỗi trong các tính năng phức tạp mà PQS và NoREC không xử lý được,.

**Điểm mạnh:**
*   **Tính tổng quát cao:** TLP có thể kiểm tra **phạm vi tính năng rộng hơn** đáng kể so với PQS và NoREC,.
*   Có thể được sử dụng để kiểm tra **mệnh đề `WHERE`, `GROUP BY`, `HAVING`**, **hàm tổng hợp (aggregate functions)** và **truy vấn `DISTINCT`**,,.
*   Yêu cầu nỗ lực triển khai thấp.

**Số lượng và Phân loại Lỗi:**
TLP đã tìm thấy **175 lỗi chưa từng được biết đến**, trong đó **125 lỗi đã được sửa**,. Tổng cộng, **77 lỗi là lỗi logic**,.

Bảng 4: Số lượng lỗi logic được phát hiện bởi từng TLP Oracle:
| DBMS | WHERE | Aggregate | GROUP BY | HAVING | DISTINCT | Error | Crash |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| SQLite | 0 | 3 | 0 | 0 | 1 | 0 | 0 |
| CockroachDB | 3 | 3 | 0 | 1 | 0 | 22 | 2 |
| TiDB | 29 | 0 | 1 | 0 | 0 | 27 | 4 |
| MySQL | 7 | 0 | 0 | 0 | 0 | 0 | 0 |
| DuckDB | 21 | 4 | 1 | 2 | 1 | 13 | 19 |
| **Tổng Logic** | **60** | **10** | **2** | **3** | **2** | | |

#### Điểm hạn chế
*   **Không thể thiết lập ground truth:** Tương tự như NoREC, TLP là một phương pháp kiểm thử biến hình (metamorphic testing), vì vậy nó **không thể đảm bảo tính đúng đắn tuyệt đối (ground truth)**. Nếu truy vấn gốc và truy vấn phân vùng đều tính toán sai nhưng cùng trả về kết quả giống nhau, lỗi sẽ bị bỏ sót,,.
*   **Phạm vi áp dụng giới hạn:** TLP không áp dụng cho **giao dịch (transactions), hàm cửa sổ (window functions), sequences**, và các hàm không xác định (non-deterministic functions).
*   Hạn chế đối với các truy vấn có kết quả không rõ ràng (như subqueries).

---

### IV. Equivalent Expression Transformation (EET)

#### Overview Approach
EET là một phương pháp tiếp cận mới và tổng quát để phát hiện lỗi logic trong DBMS, sử dụng phương pháp **thao tác cấp độ biểu thức (expression-level manipulation)** thay vì thao tác cấp độ truy vấn (query-level manipulation) như các phương pháp hiện có,.

Ý tưởng cốt lõi của EET là **thao tác các biểu thức của một truy vấn theo cách bảo toàn ngữ nghĩa (semantic-preserving)**, do đó cũng bảo toàn ngữ nghĩa của toàn bộ truy vấn và độc lập với các mẫu truy vấn,. EET xác thực DBMS bằng cách kiểm tra xem các truy vấn đã biến đổi có trả về cùng kết quả với các truy vấn gốc tương ứng hay không.

Các loại biến đổi chính được sử dụng:
1. **Determined Boolean Expressions:** Áp dụng các luật tương đương logic để biến đổi các biểu thức boolean (ví dụ: $p \equiv \text{TRUE AND } p$),.
2. **Redundant Branch Structures:** Sử dụng các cấu trúc rẽ nhánh kiểu SQL như biểu thức `CASE WHEN` để biến đổi các biểu thức non-boolean, đảm bảo kết quả trả về tương đương với biểu thức gốc,.

**Minh họa cách tiếp cận (Dựa trên Hình 3 trong tài liệu gốc):**
Quy trình EET bao gồm:
1. Phân tích Truy vấn SQL thành AST.
2. Duyệt qua AST và thực hiện **Biến đổi Biểu thức**,.
3. Tạo ra Truy vấn SQL đã Biến đổi.
4. **So sánh Kết quả** giữa truy vấn gốc và truy vấn đã biến đổi. Nếu kết quả "Khác nhau", lỗi logic được phát hiện.

#### Small Example
Ví dụ minh họa dựa trên lỗi logic 20 năm tuổi trong PostgreSQL (Hình 2):
*   Truy vấn gốc (Original query) chứa các biểu thức như `t2.c2` và `t2.c3`,.
*   EET biến đổi các biểu thức này thành hai biểu thức `CASE WHEN` tương đương về mặt ngữ nghĩa (Redundant Branch Structures) trong Truy vấn đã Biến đổi (Transformed query),,.
*   Mặc dù hai truy vấn này về mặt ngữ nghĩa là như nhau, truy vấn gốc trả về `{0}` (1 hàng), trong khi truy vấn đã biến đổi trả về **tập rỗng (empty set)**, cho thấy một lỗi logic đã được kích hoạt,. Sự khác biệt này xảy ra vì các biểu thức biến đổi khiến PostgreSQL sử dụng logic thực thi khác (cơ chế hash-join bị lỗi),.

#### Result
EET đã chứng minh tính tổng quát và hiệu quả trong việc tìm kiếm lỗi logic, đặc biệt trong các truy vấn phức tạp,.

**Điểm mạnh:**
*   **Tính tổng quát vượt trội:** Áp dụng cho **các truy vấn tùy ý (arbitrary queries)** mà không giới hạn mẫu truy vấn,,.
*   Có khả năng hỗ trợ các tính năng SQL phức tạp như **subquery tương quan (correlated subqueries), phép JOIN, hàm cửa sổ (window functions)**, và DML (những tính năng mà các phương pháp khác gặp khó khăn),.
*   **Độ tin cậy (Soundness):** EET được đảm bảo bảo toàn ngữ nghĩa, và vì vậy, nó **không tạo ra dương tính giả** (false positives) trong việc phát hiện lỗi logic.

**Số lượng và Phân loại Lỗi:**
EET đã tìm thấy **66 lỗi duy nhất** trong 5 DBMS được kiểm tra,. Tổng cộng, **35 lỗi là lỗi logic**,.

Bảng 3: Tình trạng các lỗi được tìm thấy bởi EET:
| DBMS | Reported (Báo cáo) | Confirmed (Đã xác nhận) | Fixed (Đã sửa) |
| :--- | :--- | :--- | :--- |
| MySQL | 16 | 16 | 2 |
| PostgreSQL | 9 | 9 | 8 |
| SQLite | 10 | 10 | 10 |
| ClickHouse | 21 | 20 | 15 |
| TiDB | 10 | 10 | 2 |
| **Tổng** | **66** | **65** | **37** |

Bảng 4: Phân loại lỗi:
| DBMS | Logic (Lỗi logic) | Crash (Lỗi crash) | Error (Lỗi bất thường) |
| :--- | :--- | :--- | :--- |
| MySQL | 10 | 6 | 0 |
| PostgreSQL | 3 | 3 | 3 |
| SQLite | 9 | 0 | 1 |
| ClickHouse | 11 | 3 | 7 |
| TiDB | 2 | 7 | 1 |
| **Tổng** | **35** | **19** | **12** |

#### Điểm hạn chế
*   **Khả năng bỏ sót lỗi:** Nếu cả truy vấn gốc và truy vấn đã biến đổi đều tạo ra cùng một kết quả **không chính xác**, EET sẽ bỏ sót lỗi đó.
*   **Tốc độ xử lý thấp hơn:** Do EET hỗ trợ các truy vấn phức tạp, DBMS mất nhiều thời gian hơn để thực thi chúng so với các truy vấn đơn giản, dẫn đến thông lượng (throughput) thấp hơn so với các phương pháp hiện có.

---

### V. About Window Function Research Gap (Khoảng trống nghiên cứu về Hàm Cửa Sổ)

Dựa trên các nguồn, việc kiểm tra các tính năng SQL phức tạp như Hàm Cửa Sổ (Window Functions) là một thách thức lớn đối với các oracle truyền thống:

1.  **PQS:** PQS gặp khó khăn trong việc hỗ trợ các tính năng SQL nâng cao liên quan đến tính toán phức tạp (ví dụ: hàm cửa sổ), do nó yêu cầu kết quả của các truy vấn được tạo phải được dự đoán bằng trình thông dịch được triển khai thủ công. PQS **không thể tìm thấy lỗi** trong các hàm cửa sổ.
2.  **NoREC:** NoREC **không áp dụng trực tiếp** cho các truy vấn tính toán kết quả trên nhiều bản ghi, bao gồm cả hàm cửa sổ.
3.  **TLP:** TLP **không áp dụng** cho hàm cửa sổ.
4.  **EET:** EET đã giải quyết được khoảng trống nghiên cứu này bằng cách hỗ trợ các truy vấn có chứa hàm cửa sổ. Bảng 1 trong tài liệu EET xác nhận rằng **EET hỗ trợ tính năng Window**, trong khi PQS, NoREC và TLP thì không. Trong số 35 lỗi logic mà EET tìm thấy, có **4 truy vấn kích hoạt lỗi có sử dụng hàm cửa sổ** (ví dụ: `DENSE_RANK`, `FIRST_VALUE`).

**Kết luận về khoảng trống:** Trước EET, việc thiếu tính tổng quát trong các phương pháp tiếp cận hiện có (chủ yếu dựa trên thao tác cấp độ truy vấn) đã giới hạn khả năng của chúng trong việc kiểm tra các truy vấn phức tạp, bao gồm cả những truy vấn sử dụng hàm cửa sổ. EET, thông qua thao tác cấp độ biểu thức, đã lấp đầy khoảng trống này và chứng minh rằng hàm cửa sổ là một nguồn lỗi logic quan trọng trong DBMS.