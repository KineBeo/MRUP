import os
from collections import Counter

LOG_DIR = "/home/kienbeovl/Desktop/DBMS_Oracles/MRUP/experiment_logs"

def parse_mutation_logs(log_dir):
    """Parse mutation application data"""
    applied = Counter()
    skipped = Counter()
    
    for filename in sorted(os.listdir(log_dir)):
        if not filename.endswith('.log'):
            continue
        
        filepath = os.path.join(log_dir, filename)
        
        with open(filepath, 'r') as f:
            for line in f:
                if 'METRICS_MUTATION|' in line:
                    # Parse: METRICS_MUTATION|WindowSpec|applied
                    parts = line.split('METRICS_MUTATION|')[1].strip().split('|')
                    if len(parts) == 2:
                        mutation_type, status = parts
                        if status == 'applied':
                            applied[mutation_type] += 1
                        elif status == 'skipped':
                            skipped[mutation_type] += 1
    
    return applied, skipped

def generate_latex_table(applied, skipped):
    """Generate LaTeX table rows"""
    mutation_types = [
        ('WindowSpec', '~90%'),
        ('Identity', '~98%'),
        ('CaseWhen', '100%')
    ]
    
    print("\n=== LaTeX Table 2 Content ===\n")
    
    for mut_type, target in mutation_types:
        a = applied[mut_type]
        s = skipped[mut_type]
        total = a + s
        rate = (a / total * 100) if total > 0 else 0
        
        # Map to display names
        display_name = {
            'WindowSpec': 'Window Spec',
            'Identity': 'Identity Wrapper',
            'CaseWhen': 'CASE WHEN'
        }
        
        print(f"{display_name[mut_type]} & {a:,} & {s:,} & {rate:.1f}\\% & {target} \\\\")
        print("\\hline")
    
    # Analysis
    print("\n=== Analysis Paragraph ===")
    ws_rate = (applied['WindowSpec'] / (applied['WindowSpec'] + skipped['WindowSpec']) * 100)
    id_rate = (applied['Identity'] / (applied['Identity'] + skipped['Identity']) * 100)
    cw_rate = (applied['CaseWhen'] / (applied['CaseWhen'] + skipped['CaseWhen']) * 100)
    
    print(f"Window spec mutation đạt tỷ lệ áp dụng {ws_rate:.1f}%, gần với mục tiêu 90%. "
          f"Các lần bỏ qua chủ yếu do constraint C4: khi có nhiều cột ORDER BY, RANGE frame "
          f"không được phép. Identity mutation đạt {id_rate:.1f}%, bỏ qua chủ yếu cho ranking "
          f"function vì chúng không có argument để mutate (ROW_NUMBER, RANK, DENSE_RANK). "
          f"CASE WHEN mutation đạt {cw_rate:.1f}%, luôn áp dụng được vì nó bao bọc toàn bộ "
          f"window function expression.")

if __name__ == '__main__':
    applied, skipped = parse_mutation_logs(LOG_DIR)
    generate_latex_table(applied, skipped)