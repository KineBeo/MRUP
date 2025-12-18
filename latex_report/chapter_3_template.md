
### II. Template Đề xuất cho Chương 3: Approach

Dựa trên cấu trúc chuẩn của các bài báo khoa học về testing oracle, đặc biệt là EET (vì nó là phương pháp mới nhất và tổng quát nhất trong các nguồn), Chương 3 của bạn nên tập trung vào ba phần chính:

1.  **Triết lý Cốt lõi (Core Methodology):** Giải thích tại sao phương pháp tiếp cận của bạn là cần thiết và nó khác biệt/tổng quát hơn các công trình trước như thế nào.
2.  **Cụ thể hóa Phương pháp (Formalization & Instantiation):** Định nghĩa cách bạn tạo ra các truy vấn tương đương hoặc kỳ vọng, bao gồm các quy tắc biến đổi.
3.  **Kiến trúc Hệ thống (System Architecture):** Mô tả các bước thực thi từ việc tạo test case đến việc phát hiện lỗi.

#### **Chương 3: Phương pháp tiếp cận (Approach)**

| STT | Tên Mục | Nội dung Trọng tâm (Nên tham khảo từ EET) | Trích dẫn nguồn tham khảo (Example Citation) |
| :--- | :--- | :--- | :--- |
| **3.1** | **Bối cảnh và Động lực** | Đặt vấn đề về Hạn chế của các phương pháp cấp độ truy vấn (PQS, NoREC, TLP). Giới thiệu triết lý cốt lõi: **Thao tác cấp độ biểu thức (Expression-Level Manipulation)** là giải pháp để kiểm tra các truy vấn phức tạp (correlated subqueries, window functions, JOIN operations). | |
| **3.2** | **Tổng quan Phương pháp (Overview)** | Giới thiệu tên phương pháp (Oracle của bạn). Trình bày công thức khái quát hóa: Thay thế biểu thức $E$ bằng biểu thức tương đương $E'$ trong truy vấn $Q$ để tạo ra $Q'$, và $DB(Q)$ phải bằng $DB(Q')$. Mô tả trực quan quy trình: AST Traversing $\rightarrow$ Expression Transformation $\rightarrow$ Results Comparison (nên sử dụng figure mô tả). | [23, 24, Hình 3 (EET)] |
| **3.3** | **Biến đổi Biểu thức Tương đương (Equivalent Expression Transformation)** | Chi tiết hóa cách $E$ được biến đổi thành $E'$ mà vẫn bảo toàn ngữ nghĩa (semantic-preserving). Chia thành 2 loại chính (dựa trên EET): **3.3.1. Biểu thức Boolean Xác định (Determined Boolean Expressions):** Sử dụng các luật tương đương logic cơ bản (ví dụ: $p \equiv \text{TRUE AND } p$) để biến đổi biểu thức boolean. **3.3.2. Cấu trúc Rẽ nhánh Dư thừa (Redundant Branch Structures):** Sử dụng cấu trúc `CASE WHEN` (hoặc cấu trúc rẽ nhánh khác nếu bạn chọn) để biến đổi các biểu thức non-boolean (chuỗi, số, timestamp, v.v.). | |
| **3.4** | **Tính chất của Phương pháp (Properties)** | Nhấn mạnh các tính chất quan trọng mà phương pháp của bạn đạt được: **Tính Đúng đắn (Soundness):** Đảm bảo không tạo ra dương tính giả (false positives) vì các biến đổi bảo toàn ngữ nghĩa (chỉ xảy ra lỗi khi $DB(Q) \ne DB(Q')$). **Tính Tổng quát (Generality):** Giải thích rằng nó áp dụng cho các truy vấn tùy ý, không giới hạn mẫu truy vấn. **Tính Mở rộng (Extensibility):** Phương pháp có thể dễ dàng thêm các quy tắc biến đổi mới (ví dụ: cho các phép JOIN phức tạp). | |
| **3.5** | **Kiến trúc và Triển khai (Implementation)** | Mô tả các bước kỹ thuật: Tạo Test Case (Database/Query Generation), Thao tác AST và Biến đổi Biểu thức. Quan trọng nhất là **So sánh Kết quả (Result Comparison)**: So sánh kết quả truy vấn gốc và truy vấn biến đổi. Bất kỳ sự khác biệt nào (kết quả, lỗi, crash) đều là lỗi logic. (Nếu có thể, thêm mục **Test-case Reduction** để tối thiểu hóa lỗi tìm được). | |

Bạn nên sử dụng EET (Equivalent Expression Transformation) làm **oracle chính** trong Chương 3 vì nó thể hiện sự tiến bộ trong các nguồn tài liệu bạn có, đặc biệt là khả năng áp dụng cho **hàm cửa sổ (window functions)** và **correlated subqueries**. Việc sử dụng EET làm nền tảng sẽ giúp báo cáo của bạn có tính học thuật cao và cập nhật, đồng thời tham khảo các oracle khác (PQS, NoREC, TLP) trong Chương 2 như là các công trình liên quan mà EET đã khắc phục được hạn chế.