#!/usr/bin/env python3
"""
Chapter 4 Experiment Runner and Data Extractor
Runs 10,000 test cases and extracts all metrics for filling [TBD] markers
"""

import subprocess
import re
import json
from pathlib import Path
from collections import Counter, defaultdict
import time
from datetime import datetime

class Chapter4Experiment:
    def __init__(self, num_test_cases=10000):
        self.num_test_cases = num_test_cases
        self.metrics = defaultdict(lambda: defaultdict(int))
        self.log_dir = Path("mrup_logs")
        self.output_file = "chapter4_metrics.json"
        
    def run_experiment(self):
        """Run the MRUP Oracle for specified number of test cases"""
        print(f"🚀 Starting Chapter 4 Experiment: {self.num_test_cases} test cases")
        print(f"⏰ Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        
        # Clean old logs
        subprocess.run(f"rm -rf {self.log_dir}/*.log", shell=True)
        
        # Run oracle with logging enabled
        start_time = time.time()
        cmd = f"timeout 3600 java -Dmrup.logging.enabled=true -jar target/sqlancer-2.0.0.jar --num-queries {self.num_test_cases} sqlite3 --oracle MRUP 2>&1"
        
        print(f"Running: {cmd}\n")
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        
        elapsed_time = time.time() - start_time
        
        print(f"\n✅ Experiment completed in {elapsed_time:.1f} seconds")
        print(f"📊 Log files created: {len(list(self.log_dir.glob('*.log')))}")
        
        return elapsed_time
        
    def parse_logs(self):
        """Parse all log files and extract metrics"""
        print(f"\n📖 Parsing log files...")
        
        log_files = sorted(self.log_dir.glob("*.log"))
        print(f"Found {len(log_files)} log files")
        
        for i, log_file in enumerate(log_files, 1):
            if i % 1000 == 0:
                print(f"  Processed {i}/{len(log_files)} files...")
            self._parse_single_log(log_file)
            
        print(f"✅ Parsing complete: {len(log_files)} files processed\n")
        
    def _parse_single_log(self, log_file):
        """Parse a single log file and extract all metrics"""
        with open(log_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # RQ1: Constraint Satisfaction
        self._extract_constraints(content)
        
        # RQ2: Mutation Application and Distribution
        self._extract_mutations(content)
        
        # RQ2: Schema and Query Diversity
        self._extract_schema_diversity(content)
        
        # RQ3: Comparator Behavior
        self._extract_comparator_behavior(content)
        
        # RQ4: Throughput (handled separately)
        
    def _extract_constraints(self, content):
        """Extract constraint satisfaction metrics"""
        # Look for constraint verification in log
        constraint_patterns = {
            'C0': r'C0.*?(\w+)',
            'C1': r'C1.*?(\w+)',
            'C2': r'C2.*?(\w+)',
            'C3': r'C3.*?(\w+)',
            'C4': r'C4.*?(\w+)',
            'C5': r'C5.*?(\w+)'
        }
        
        for constraint, pattern in constraint_patterns.items():
            match = re.search(pattern, content, re.IGNORECASE)
            if match:
                status = 'true' in match.group(0).lower() or '✓' in match.group(0)
                self.metrics['constraints'][constraint + '_satisfied'] += 1 if status else 0
                self.metrics['constraints'][constraint + '_total'] += 1
                
    def _extract_mutations(self, content):
        """Extract mutation application rates and distribution"""
        # Window Spec Mutations
        if re.search(r'PHASE 1.*?✓ Applied', content, re.DOTALL):
            self.metrics['mutations']['window_spec_applied'] += 1
        elif re.search(r'PHASE 1.*?✗ Not Applied', content, re.DOTALL):
            self.metrics['mutations']['window_spec_skipped'] += 1
            
        # Identity Mutations
        if re.search(r'STAGE 1.*?✓ Applied', content, re.DOTALL):
            self.metrics['mutations']['identity_applied'] += 1
        elif re.search(r'STAGE 1.*?✗ Not Applied', content, re.DOTALL):
            self.metrics['mutations']['identity_skipped'] += 1
            
        # CASE WHEN Mutations
        if re.search(r'PHASE 3.*?✓ Applied', content, re.DOTALL):
            self.metrics['mutations']['case_when_applied'] += 1
        elif re.search(r'PHASE 3.*?✗ Not Applied', content, re.DOTALL):
            self.metrics['mutations']['case_when_skipped'] += 1
            
        # CASE WHEN Strategy Distribution
        case_strategies = {
            'Constant Condition': r'Constant Condition',
            'Window Function in WHEN': r'Window Function in WHEN',
            'Different Window Functions': r'Different Window Functions',
            'Identical Branches': r'Identical Branches',
            'NULL Handling': r'NULL Handling'
        }
        
        for strategy, pattern in case_strategies.items():
            if re.search(pattern, content):
                self.metrics['case_strategies'][strategy] += 1
                
    def _extract_schema_diversity(self, content):
        """Extract schema and query diversity metrics"""
        # Count columns
        col_match = re.search(r'Schema.*?(\d+)\s+columns', content, re.IGNORECASE)
        if col_match:
            num_cols = int(col_match.group(1))
            self.metrics['schema']['num_columns'].append(num_cols)
            
        # Count column types
        if 'INTEGER' in content:
            self.metrics['schema']['type_integer'] += content.count('INTEGER')
        if 'REAL' in content:
            self.metrics['schema']['type_real'] += content.count('REAL')
        if 'TEXT' in content:
            self.metrics['schema']['type_text'] += content.count('TEXT')
            
        # Window function type
        if re.search(r'(SUM|AVG|COUNT|MIN|MAX)\(', content):
            self.metrics['query']['aggregate_function'] += 1
        if re.search(r'(ROW_NUMBER|RANK|DENSE_RANK)\(', content):
            self.metrics['query']['ranking_function'] += 1
            
        # ORDER BY columns
        order_by_match = re.search(r'ORDER BY[^)]+', content)
        if order_by_match:
            num_order_cols = order_by_match.group(0).count(',') + 1
            self.metrics['query'][f'order_by_{num_order_cols}_cols'] += 1
            
        # Frame clause
        if 'ROWS' in content or 'RANGE' in content:
            self.metrics['query']['has_frame'] += 1
            if 'ROWS' in content:
                self.metrics['query']['frame_rows'] += 1
            if 'RANGE' in content:
                self.metrics['query']['frame_range'] += 1
        else:
            self.metrics['query']['no_frame'] += 1
            
    def _extract_comparator_behavior(self, content):
        """Extract comparator layer usage"""
        # Layer 1: Cardinality
        if 'Layer 1' in content or 'Cardinality' in content:
            self.metrics['comparator']['layer1_reached'] += 1
            if 'passed' in content.lower() or '✓' in content:
                self.metrics['comparator']['layer1_passed'] += 1
                
        # Layer 2: Normalization
        if 'Layer 2' in content or 'Normalization' in content:
            self.metrics['comparator']['layer2_reached'] += 1
            if 'passed' in content.lower() or '✓' in content:
                self.metrics['comparator']['layer2_passed'] += 1
                
        # Layer 3: Per-Partition
        if 'Layer 3' in content or 'Per-Partition' in content:
            self.metrics['comparator']['layer3_reached'] += 1
            if 'passed' in content.lower() or '✓' in content:
                self.metrics['comparator']['layer3_passed'] += 1
                
    def calculate_metrics(self):
        """Calculate final metrics and percentages"""
        results = {}
        
        # RQ1: Constraint Satisfaction
        results['rq1_constraints'] = {}
        for constraint in ['C0', 'C1', 'C2', 'C3', 'C4', 'C5']:
            satisfied = self.metrics['constraints'].get(f'{constraint}_satisfied', 0)
            total = self.metrics['constraints'].get(f'{constraint}_total', 0)
            rate = (satisfied / total * 100) if total > 0 else 100.0
            results['rq1_constraints'][constraint] = {
                'satisfied': satisfied,
                'violated': total - satisfied,
                'rate': rate
            }
            
        # RQ2: Mutation Application Rates
        results['rq2_mutation_rates'] = {}
        
        window_applied = self.metrics['mutations'].get('window_spec_applied', 0)
        window_skipped = self.metrics['mutations'].get('window_spec_skipped', 0)
        window_total = window_applied + window_skipped
        
        identity_applied = self.metrics['mutations'].get('identity_applied', 0)
        identity_skipped = self.metrics['mutations'].get('identity_skipped', 0)
        identity_total = identity_applied + identity_skipped
        
        case_applied = self.metrics['mutations'].get('case_when_applied', 0)
        case_skipped = self.metrics['mutations'].get('case_when_skipped', 0)
        case_total = case_applied + case_skipped
        
        results['rq2_mutation_rates'] = {
            'window_spec': {
                'applied': window_applied,
                'skipped': window_skipped,
                'rate': (window_applied / window_total * 100) if window_total > 0 else 0
            },
            'identity': {
                'applied': identity_applied,
                'skipped': identity_skipped,
                'rate': (identity_applied / identity_total * 100) if identity_total > 0 else 0
            },
            'case_when': {
                'applied': case_applied,
                'skipped': case_skipped,
                'rate': (case_applied / case_total * 100) if case_total > 0 else 0
            }
        }
        
        # RQ2: CASE WHEN Distribution
        case_total_count = sum(self.metrics['case_strategies'].values())
        results['rq2_case_distribution'] = {}
        for strategy, count in self.metrics['case_strategies'].items():
            rate = (count / case_total_count * 100) if case_total_count > 0 else 0
            results['rq2_case_distribution'][strategy] = {
                'count': count,
                'rate': rate
            }
            
        # RQ2: Schema Diversity
        num_columns_list = self.metrics['schema'].get('num_columns', [])
        avg_columns = sum(num_columns_list) / len(num_columns_list) if num_columns_list else 0
        
        type_total = (self.metrics['schema'].get('type_integer', 0) +
                      self.metrics['schema'].get('type_real', 0) +
                      self.metrics['schema'].get('type_text', 0))
        
        results['rq2_schema_diversity'] = {
            'avg_columns': avg_columns,
            'type_integer_pct': (self.metrics['schema'].get('type_integer', 0) / type_total * 100) if type_total > 0 else 0,
            'type_real_pct': (self.metrics['schema'].get('type_real', 0) / type_total * 100) if type_total > 0 else 0,
            'type_text_pct': (self.metrics['schema'].get('type_text', 0) / type_total * 100) if type_total > 0 else 0
        }
        
        # RQ2: Query Diversity
        func_total = self.metrics['query'].get('aggregate_function', 0) + self.metrics['query'].get('ranking_function', 0)
        
        results['rq2_query_diversity'] = {
            'aggregate_pct': (self.metrics['query'].get('aggregate_function', 0) / func_total * 100) if func_total > 0 else 0,
            'ranking_pct': (self.metrics['query'].get('ranking_function', 0) / func_total * 100) if func_total > 0 else 0,
            'order_by_1_pct': (self.metrics['query'].get('order_by_1_cols', 0) / func_total * 100) if func_total > 0 else 0,
            'order_by_2_pct': (self.metrics['query'].get('order_by_2_cols', 0) / func_total * 100) if func_total > 0 else 0,
            'order_by_3_pct': (self.metrics['query'].get('order_by_3_cols', 0) / func_total * 100) if func_total > 0 else 0,
            'has_frame_pct': (self.metrics['query'].get('has_frame', 0) / func_total * 100) if func_total > 0 else 0,
            'frame_rows_pct': (self.metrics['query'].get('frame_rows', 0) / func_total * 100) if func_total > 0 else 0,
            'frame_range_pct': (self.metrics['query'].get('frame_range', 0) / func_total * 100) if func_total > 0 else 0
        }
        
        # RQ3: Comparator Behavior
        layer1_total = self.metrics['comparator'].get('layer1_reached', 0)
        results['rq3_comparator'] = {
            'layer1_reached': layer1_total,
            'layer1_passed': self.metrics['comparator'].get('layer1_passed', 0),
            'layer1_rate': (self.metrics['comparator'].get('layer1_passed', 0) / layer1_total * 100) if layer1_total > 0 else 0,
            'layer2_reached': self.metrics['comparator'].get('layer2_reached', 0),
            'layer2_passed': self.metrics['comparator'].get('layer2_passed', 0),
            'layer2_rate': (self.metrics['comparator'].get('layer2_passed', 0) / self.metrics['comparator'].get('layer2_reached', 1) * 100) if self.metrics['comparator'].get('layer2_reached', 0) > 0 else 0,
            'layer3_reached': self.metrics['comparator'].get('layer3_reached', 0),
            'layer3_passed': self.metrics['comparator'].get('layer3_passed', 0),
            'layer3_rate': (self.metrics['comparator'].get('layer3_passed', 0) / self.metrics['comparator'].get('layer3_reached', 1) * 100) if self.metrics['comparator'].get('layer3_reached', 0) > 0 else 0
        }
        
        return results
        
    def save_results(self, results, elapsed_time):
        """Save results to JSON file"""
        output_data = {
            'experiment_info': {
                'num_test_cases': self.num_test_cases,
                'elapsed_time_seconds': elapsed_time,
                'timestamp': datetime.now().isoformat()
            },
            'results': results
        }
        
        with open(self.output_file, 'w') as f:
            json.dump(output_data, f, indent=2)
            
        print(f"✅ Results saved to: {self.output_file}\n")
        
    def print_summary(self, results):
        """Print a summary of results"""
        print("=" * 70)
        print("CHAPTER 4 EXPERIMENT RESULTS SUMMARY")
        print("=" * 70)
        
        print("\n📊 RQ1: Constraint Satisfaction")
        for constraint, data in results['rq1_constraints'].items():
            print(f"  {constraint}: {data['rate']:.1f}% ({data['satisfied']}/{data['satisfied'] + data['violated']})")
            
        print("\n📊 RQ2: Mutation Application Rates")
        for mut_type, data in results['rq2_mutation_rates'].items():
            print(f"  {mut_type}: {data['rate']:.1f}% ({data['applied']}/{data['applied'] + data['skipped']})")
            
        print("\n📊 RQ2: CASE WHEN Distribution")
        for strategy, data in results['rq2_case_distribution'].items():
            print(f"  {strategy}: {data['rate']:.1f}% ({data['count']}x)")
            
        print("\n📊 RQ3: Comparator Behavior")
        comp = results['rq3_comparator']
        print(f"  Layer 1: {comp['layer1_rate']:.1f}% pass rate ({comp['layer1_passed']}/{comp['layer1_reached']})")
        print(f"  Layer 2: {comp['layer2_rate']:.1f}% pass rate ({comp['layer2_passed']}/{comp['layer2_reached']})")
        print(f"  Layer 3: {comp['layer3_rate']:.1f}% pass rate ({comp['layer3_passed']}/{comp['layer3_reached']})")
        
        print("\n" + "=" * 70)
        
def main():
    # Use existing logs if available, otherwise run new experiment
    experiment = Chapter4Experiment(num_test_cases=10000)
    
    log_count = len(list(experiment.log_dir.glob("*.log")))
    
    if log_count < 100:
        print(f"⚠️  Only {log_count} log files found. Running new experiment...")
        elapsed_time = experiment.run_experiment()
    else:
        print(f"✅ Found {log_count} existing log files. Using them...")
        elapsed_time = 0
        
    # Parse logs and calculate metrics
    experiment.parse_logs()
    results = experiment.calculate_metrics()
    
    # Save and display results
    experiment.save_results(results, elapsed_time)
    experiment.print_summary(results)
    
    print("\n✅ Experiment complete! Results saved to chapter4_metrics.json")
    print("📝 Next step: Use these metrics to fill [TBD] markers in Chap4_Experiments.tex")

if __name__ == "__main__":
    main()
