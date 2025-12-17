#!/usr/bin/env python3
"""
Mutation Statistics Analyzer for MRUP Oracle

Analyzes mutation distribution across all stages to identify:
- Which mutation variants are underutilized
- Which combinations are most common
- Opportunities to increase bug-finding coverage
"""

import os
import re
from collections import defaultdict, Counter
from pathlib import Path
from datetime import datetime

class MutationAnalyzer:
    def __init__(self, log_dir="mrup_logs", output_file="mutation_analysis.md"):
        self.log_dir = log_dir
        self.output_file = output_file
        self.stats = {
            'window_spec': Counter(),
            'identity': Counter(),
            'case_when': Counter(),
            'combinations': Counter()
        }
        self.total_queries = 0
        self.output_lines = []
        
    def write(self, text=""):
        """Add line to output buffer."""
        self.output_lines.append(text)
        
    def parse_log_file(self, filepath):
        """Parse a single log file and extract mutation information."""
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        # Extract mutation pipeline section
        pipeline_match = re.search(
            r'STEP 3: Mutation Pipeline.*?(?=STEP 4:|STEP 3B:|$)', 
            content, 
            re.DOTALL
        )
        
        if not pipeline_match:
            return None
        
        pipeline = pipeline_match.group(0)
        
        # Extract Window Spec mutation
        window_spec = "None"
        if "PHASE 1: Window Spec Mutations" in pipeline:
            if "✓ Applied:" in pipeline.split("PHASE 1:")[1].split("STAGE 1:")[0]:
                match = re.search(r'PHASE 1:.*?✓ Applied: ([^\n]+)', pipeline, re.DOTALL)
                if match:
                    window_spec = match.group(1).strip()
        
        # Extract Identity mutation
        identity = "None"
        if "STAGE 1: Identity Wrapper Mutations" in pipeline:
            if "✓ Applied:" in pipeline.split("STAGE 1:")[1].split("PHASE 3:")[0]:
                match = re.search(r'STAGE 1:.*?✓ Applied: ([^\n]+)', pipeline, re.DOTALL)
                if match:
                    identity = match.group(1).strip()
        
        # Extract CASE WHEN mutation
        case_when = "None"
        if "PHASE 3: CASE WHEN Mutations" in pipeline:
            match = re.search(r'PHASE 3:.*?✓ Applied: ([^\n]+)', pipeline, re.DOTALL)
            if match:
                case_when = match.group(1).strip()
        
        return {
            'window_spec': window_spec,
            'identity': identity,
            'case_when': case_when
        }
    
    def analyze_all_logs(self):
        """Analyze all log files in the directory."""
        log_files = list(Path(self.log_dir).glob("*.log"))
        
        if not log_files:
            self.write(f"❌ No log files found in {self.log_dir}/")
            return
        
        print(f"📊 Analyzing {len(log_files)} log files...")
        
        for log_file in log_files:
            result = self.parse_log_file(log_file)
            if result:
                self.total_queries += 1
                self.stats['window_spec'][result['window_spec']] += 1
                self.stats['identity'][result['identity']] += 1
                self.stats['case_when'][result['case_when']] += 1
                
                # Track combinations
                combo = f"{result['window_spec']} + {result['identity']} + {result['case_when']}"
                self.stats['combinations'][combo] += 1
    
    def print_dashboard(self):
        """Generate markdown dashboard of mutation statistics."""
        if self.total_queries == 0:
            self.write("❌ No queries analyzed")
            return
        
        self.write("# MRUP ORACLE - MUTATION STATISTICS DASHBOARD")
        self.write()
        self.write(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        self.write()
        self.write(f"📊 **Total Queries Analyzed:** {self.total_queries}")
        self.write()
        self.write("---")
        self.write()
        
        # Phase 1: Window Spec Mutations
        self._print_stage_stats(
            "PHASE 1: Window Spec Mutations",
            self.stats['window_spec'],
            [
                "None",
                "Order-Preserving Transform",
                "Redundant PARTITION BY",
                "NULLS FIRST/LAST Toggle"
            ]
        )
        
        # Stage 1: Identity Wrapper Mutations
        self._print_stage_stats(
            "STAGE 1: Identity Wrapper Mutations",
            self.stats['identity'],
            [
                "None",
                "Arithmetic Identity (+ 0)",
                "Arithmetic Identity (- 0)",
                "Arithmetic Identity (* 1)",
                "Arithmetic Identity (/ 1)",
                "Arithmetic Identity (0 +)",
                "Arithmetic Identity (1 *)",
                "Type Cast Identity (INTEGER)",
                "Type Cast Identity (REAL)",
                "Rounding Identity",
                "NULL-Safe Identity (COALESCE)",
                "NULL-Safe Identity (IFNULL)",
                "Parentheses Wrapping (single)",
                "Parentheses Wrapping (double)",
                "Chained Identity (+ 0 - 0)",
                "Chained Identity (* 1 * 1)"
            ]
        )
        
        # Phase 3: CASE WHEN Mutations
        self._print_stage_stats(
            "PHASE 3: CASE WHEN Mutations",
            self.stats['case_when'],
            [
                "Constant Condition",
                "Window Function in WHEN",
                "Different Window Functions",
                "Identical Branches",
                "NULL Handling",
                "Constant Condition (fallback)"
            ]
        )
        
        # Top Combinations
        self.write()
        self.write("---")
        self.write()
        self.write("## 🔗 TOP 10 MUTATION COMBINATIONS")
        self.write()
        
        for i, (combo, count) in enumerate(self.stats['combinations'].most_common(10), 1):
            pct = (count / self.total_queries) * 100
            self.write(f"{i}. **[{pct:5.2f}%]** ({count:3d}x) {combo}")
        
        self.write()
        self.write("---")
        self.write()
        
        # Analysis and Recommendations
        self._print_recommendations()
    
    def _print_stage_stats(self, stage_name, counter, expected_variants):
        """Generate statistics for a single mutation stage."""
        self.write(f"## 📍 {stage_name}")
        self.write()
        
        # Calculate percentages
        stats = []
        for variant in expected_variants:
            count = counter.get(variant, 0)
            pct = (count / self.total_queries) * 100 if self.total_queries > 0 else 0
            stats.append((variant, count, pct))
        
        # Add any unexpected variants
        for variant, count in counter.items():
            if variant not in expected_variants:
                pct = (count / self.total_queries) * 100
                stats.append((variant, count, pct))
        
        # Sort by count (descending)
        stats.sort(key=lambda x: x[1], reverse=True)
        
        # Print table header
        self.write("| Indicator | Variant | Percentage | Count | Visual |")
        self.write("|-----------|---------|------------|-------|--------|")
        
        for name, count, pct in stats:
            # Visual bar
            bar_len = int(pct / 2)  # Scale to fit
            bar = "█" * bar_len
            
            # Color coding
            if pct == 0:
                indicator = "⚠️"
            elif pct < 2:
                indicator = "🔸"
            elif pct < 5:
                indicator = "🔹"
            else:
                indicator = "✅"
            
            self.write(f"| {indicator} | {name} | {pct:5.2f}% | {count:4d}x | {bar} |")
        
        self.write()
        self.write("---")
        self.write()
    
    def _print_recommendations(self):
        """Generate recommendations based on mutation distribution."""
        self.write("## 💡 RECOMMENDATIONS")
        self.write()
        
        recommendations = []
        
        # Check Identity mutations
        identity_none_pct = (self.stats['identity'].get('None', 0) / self.total_queries) * 100
        if identity_none_pct > 50:
            recommendations.append(
                f"⚠️  **Identity mutations not applied in {identity_none_pct:.1f}% of queries**\n"
                f"   - Consider increasing identity mutation probability (currently 60%)"
            )
        
        # Check for underutilized identity variants
        identity_variants = [k for k in self.stats['identity'].keys() if k != 'None']
        if identity_variants:
            min_variant = min(identity_variants, key=lambda k: self.stats['identity'][k])
            min_count = self.stats['identity'][min_variant]
            max_variant = max(identity_variants, key=lambda k: self.stats['identity'][k])
            max_count = self.stats['identity'][max_variant]
            
            if max_count > min_count * 3:  # Significant imbalance
                recommendations.append(
                    f"⚠️  **Identity variant imbalance detected:**\n"
                    f"   - Most common: '{max_variant}' ({max_count}x)\n"
                    f"   - Least common: '{min_variant}' ({min_count}x)\n"
                    f"   - Consider adjusting Randomly.fromOptions() weights"
                )
        
        # Check Window Spec mutations
        window_none_pct = (self.stats['window_spec'].get('None', 0) / self.total_queries) * 100
        if window_none_pct > 80:
            recommendations.append(
                f"⚠️  **Window Spec mutations not applied in {window_none_pct:.1f}% of queries**\n"
                f"   - Consider increasing window spec mutation probability"
            )
        
        # Check CASE WHEN distribution
        case_variants = list(self.stats['case_when'].keys())
        if len(case_variants) > 1:
            expected_pct = 100 / len(case_variants)
            for variant in case_variants:
                actual_pct = (self.stats['case_when'][variant] / self.total_queries) * 100
                if abs(actual_pct - expected_pct) > 10:  # More than 10% deviation
                    recommendations.append(
                        f"🔸 **CASE WHEN variant '{variant}' at {actual_pct:.1f}%** "
                        f"(expected ~{expected_pct:.1f}%)\n"
                        f"   - Check weighted random selection in Phase 3"
                    )
        
        if not recommendations:
            self.write("- ✅ Mutation distribution looks balanced!")
            self.write("- ✅ All variants are being exercised")
            self.write("- ✅ No immediate adjustments needed")
        else:
            for i, rec in enumerate(recommendations, 1):
                self.write(f"{i}. {rec}")
                self.write()
        
        self.write()
        self.write("---")
        self.write()
        
        # Bug-finding potential
        self.write("## 🎯 BUG-FINDING POTENTIAL")
        self.write()
        
        # Calculate coverage scores
        identity_coverage = len([k for k in self.stats['identity'].keys() if k != 'None' and self.stats['identity'][k] > 0])
        identity_total = 15  # Total identity variants
        
        case_coverage = len([k for k in self.stats['case_when'].keys() if self.stats['case_when'][k] > 0])
        case_total = 5  # Total CASE variants (excluding fallback)
        
        window_coverage = len([k for k in self.stats['window_spec'].keys() if k != 'None' and self.stats['window_spec'][k] > 0])
        window_total = 3  # Total window spec variants
        
        self.write(f"- **Identity Variants:** {identity_coverage}/{identity_total} active ({identity_coverage/identity_total*100:.1f}%)")
        self.write(f"- **CASE WHEN Variants:** {case_coverage}/{case_total} active ({case_coverage/case_total*100:.1f}%)")
        self.write(f"- **Window Spec Variants:** {window_coverage}/{window_total} active ({window_coverage/window_total*100:.1f}%)")
        self.write()
        
        avg_coverage = (identity_coverage/identity_total + case_coverage/case_total + window_coverage/window_total) / 3 * 100
        
        if avg_coverage > 90:
            self.write(f"✅ **Excellent coverage ({avg_coverage:.1f}%)** - All mutation types exercised!")
        elif avg_coverage > 70:
            self.write(f"🔹 **Good coverage ({avg_coverage:.1f}%)** - Most mutations active")
        else:
            self.write(f"⚠️  **Limited coverage ({avg_coverage:.1f}%)** - Many mutations underutilized")
        
        self.write()
        self.write("---")
    
    def save_to_file(self):
        """Save the output buffer to markdown file."""
        with open(self.output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(self.output_lines))
        print(f"✅ Analysis saved to: {self.output_file}")

def main():
    analyzer = MutationAnalyzer("mrup_logs", "mutation_variants_metrics/mutation_analysis.md")
    analyzer.analyze_all_logs()
    analyzer.print_dashboard()
    analyzer.save_to_file()

if __name__ == "__main__":
    main()