import os
import re
from collections import Counter

LOG_DIR = "/home/kienbeovl/Desktop/DBMS_Oracles/MRUP/experiment_logs"

def parse_constraint_logs(log_dir):
    """Parse all log files and count constraint satisfaction"""
    satisfied = Counter()
    violated = Counter()
    
    # Process all log files
    for filename in sorted(os.listdir(log_dir)):
        if not filename.endswith('.log'):
            continue
        
        filepath = os.path.join(log_dir, filename)
        
        with open(filepath, 'r') as f:
            for line in f:
                if 'METRICS_CONSTRAINT|' in line:
                    # Parse: C0:true|C1:true|...
                    parts = line.split('METRICS_CONSTRAINT|')[1].strip().split('|')
                    
                    for part in parts:
                        if ':' in part:
                            constraint, value = part.split(':')
                            if value == 'true':
                                satisfied[constraint] += 1
                            elif value == 'false':
                                violated[constraint] += 1
    
    return satisfied, violated

def generate_latex_table(satisfied, violated):
    """Generate LaTeX table rows"""
    constraints = ['C0', 'C1', 'C2', 'C3', 'C4', 'C5']
    total_satisfied = 0
    total_violated = 0
    
    print("\n=== LaTeX Table 1 Content ===\n")
    
    for c in constraints:
        s = satisfied[c]
        v = violated[c]
        total = s + v
        rate = (s / total * 100) if total > 0 else 0
        
        total_satisfied += s
        total_violated += v
        
        # Generate LaTeX row
        constraint_name = {
            'C0': 'PARTITION BY bắt buộc',
            'C1': 'Chỉ dùng dept',
            'C2': 'Chỉ dùng salary/age',
            'C3': 'Không frame cho ranking',
            'C4': 'RANGE với 1 cột ORDER BY',
            'C5': 'Hàm xác định'
        }
        
        print(f"{constraint_name[c]} & {s:,} & {v} & {rate:.1f}\\% \\\\")
        print("\\hline")
    
    # Total row
    total_checks = total_satisfied + total_violated
    total_rate = (total_satisfied / total_checks * 100) if total_checks > 0 else 0
    print(f"\\textbf{{Tổng thể}} & \\textbf{{{total_satisfied:,}}} & \\textbf{{{total_violated}}} & \\textbf{{{total_rate:.1f}\\%}} \\\\")
    
    # Summary statistics
    print("\n=== Summary ===")
    print(f"Total test cases processed: {satisfied['C0']:,}")
    print(f"Total constraint checks: {total_checks:,}")
    print(f"Overall satisfaction rate: {total_rate:.1f}%")
    
    # Analysis text
    print("\n=== Analysis Paragraph ===")
    if total_violated == 0:
        print(f"Kết quả cho thấy MRUP Oracle tuân thủ hoàn hảo hệ thống ràng buộc "
              f"với tỷ lệ thỏa mãn 100% cho tất cả 6 ràng buộc trên {satisfied['C0']:,} test case. "
              f"Không có vi phạm nào được ghi nhận, chứng minh rằng logic sinh truy vấn "
              f"và áp dụng đột biến hoạt động chính xác. Đây là kết quả quan trọng vì "
              f"bất kỳ vi phạm ràng buộc nào cũng sẽ làm mất hiệu lực quan hệ metamorphic "
              f"và dẫn đến false positive.")
    else:
        print(f"WARNING: {total_violated} constraint violations detected! "
              f"This indicates a bug in the oracle implementation.")
    
    print("\n=== RQ1 Meaning ===")
    print(f"MRUP Oracle thực thi chính xác hệ thống ràng buộc của nó, "
          f"với {total_rate:.1f}% compliance trên {satisfied['C0']:,} test case. "
          f"Điều này đảm bảo rằng mọi truy vấn được sinh đều thỏa mãn các điều kiện "
          f"cần thiết cho quan hệ metamorphic MRUP, từ đó đảm bảo soundness của oracle.")

if __name__ == '__main__':
    satisfied, violated = parse_constraint_logs(LOG_DIR)
    generate_latex_table(satisfied, violated)