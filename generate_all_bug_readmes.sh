#!/bin/bash
# Generate READMEs for all bug reports
# Usage: ./generate_all_bug_readmes.sh

BUG_REPORTS_DIR="bug_reports"

if [ ! -d "$BUG_REPORTS_DIR" ]; then
    echo "Error: $BUG_REPORTS_DIR directory not found"
    exit 1
fi

echo "🔍 Scanning for bug reports in $BUG_REPORTS_DIR..."
echo ""

# Count total bug reports
TOTAL=$(ls -1 "$BUG_REPORTS_DIR"/bug_report_*.sql 2>/dev/null | wc -l)

if [ "$TOTAL" -eq 0 ]; then
    echo "No bug reports found in $BUG_REPORTS_DIR"
    exit 0
fi

echo "Found $TOTAL bug report(s)"
echo ""

# Process each bug report
COUNT=0
for bug_file in "$BUG_REPORTS_DIR"/bug_report_*.sql; do
    # Skip if README already exists (unless --force flag is used)
    readme_file="${bug_file%.sql}_README.md"
    
    if [ "$1" != "--force" ] && [ -f "$readme_file" ]; then
        echo "⏭️  Skipping $(basename "$bug_file") (README exists)"
        continue
    fi
    
    COUNT=$((COUNT + 1))
    echo "[$COUNT/$TOTAL] Generating README for $(basename "$bug_file")..."
    ./generate_bug_report_readme.sh "$bug_file" > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo "    ✅ Success: $readme_file"
    else
        echo "    ❌ Failed: $bug_file"
    fi
done

echo ""
echo "✅ Done! Generated $COUNT README(s)"
echo ""
echo "📁 View reports in: $BUG_REPORTS_DIR/"
echo "📊 Generate index: ./generate_bug_reports_index.sh"

