# Bug Reports Usage Guide

## 📁 Directory Structure

```
MRUP/
├── bug_reports/                          # All bug reports stored here
│   ├── INDEX.md                         # Index of all bug reports
│   ├── bug_report_XXXXX.sql            # Bug reproduction SQL script
│   ├── bug_report_XXXXX_README.md      # Human-readable report
│   └── ...
├── generate_bug_report_readme.sh        # Generate README for one bug
├── generate_all_bug_readmes.sh          # Generate READMEs for all bugs
└── generate_bug_reports_index.sh        # Generate bug reports index
```

---

## 🚀 Quick Start

### 1. Run the MRUP Oracle

```bash
# Build the project
mvn package -DskipTests -q

# Run the oracle (generates bug reports automatically)
java -jar target/sqlancer-2.0.0.jar --num-queries 20 sqlite3 --oracle MRUP
```

**Output**: Bug reports are automatically saved to `bug_reports/bug_report_XXXXX.sql`

---

### 2. Verify a Bug Report

```bash
# Run a specific bug report
sqlite3 < bug_reports/bug_report_1764909464068.sql
```

**Look for**: Differences between "Expected" and "Actual" sections

---

### 3. Generate Human-Readable README

```bash
# For a single bug report
./generate_bug_report_readme.sh bug_reports/bug_report_1764909464068.sql

# For all bug reports
./generate_all_bug_readmes.sh

# Force regenerate all (even if README exists)
./generate_all_bug_readmes.sh --force
```

**Output**: Creates `bug_report_XXXXX_README.md` with formatted analysis

---

### 4. Generate Bug Reports Index

```bash
./generate_bug_reports_index.sh
```

**Output**: Creates `bug_reports/INDEX.md` with a table of all bug reports

---

## 📖 Understanding Bug Reports

### Bug Report SQL File (`bug_report_XXXXX.sql`)

**Purpose**: Standalone SQL script to reproduce the bug

**Contents**:
- Table creation (t1 and t2 with same schema)
- Data insertion (minimal data to trigger bug)
- Test queries (Q1, Q2, Q_union)
- Verification queries

**Usage**:
```bash
sqlite3 < bug_reports/bug_report_XXXXX.sql
```

---

### Bug Report README (`bug_report_XXXXX_README.md`)

**Purpose**: Human-readable analysis of the bug

**Contents**:
- 🐛 Bug summary and description
- 📊 Test results and statistics
- 🔍 Detailed table data
- ⚠️ Bug demonstration with actual results
- 📈 Side-by-side comparison (Expected vs Actual)
- 🎯 Analysis and root cause hypothesis
- 🚀 Reproduction steps

**Usage**:
```bash
cat bug_reports/bug_report_XXXXX_README.md
# Or open in your favorite markdown viewer
```

---

### Bug Reports Index (`bug_reports/INDEX.md`)

**Purpose**: Overview of all bug reports

**Contents**:
- Total bug count
- Table of all bugs with links
- Statistics
- Quick actions guide

**Usage**:
```bash
cat bug_reports/INDEX.md
# Or open in browser/editor
```

---

## 🔄 Workflow

### Complete Workflow (Automated)

```bash
# Step 1: Run oracle to find bugs
java -jar target/sqlancer-2.0.0.jar --num-queries 50 sqlite3 --oracle MRUP

# Step 2: Generate READMEs for all bug reports
./generate_all_bug_readmes.sh

# Step 3: Generate index
./generate_bug_reports_index.sh

# Step 4: View the index
cat bug_reports/INDEX.md
```

---

### Manual Workflow (Single Bug)

```bash
# Step 1: Run oracle to find a bug
java -jar target/sqlancer-2.0.0.jar --num-queries 10 sqlite3 --oracle MRUP

# Step 2: Identify the bug report
ls -lt bug_reports/*.sql | head -1

# Step 3: Verify the bug
sqlite3 < bug_reports/bug_report_XXXXX.sql

# Step 4: Generate README
./generate_bug_report_readme.sh bug_reports/bug_report_XXXXX.sql

# Step 5: Read the analysis
cat bug_reports/bug_report_XXXXX_README.md
```

---

## 📊 Example Output

### After Running Oracle

```
[MRUP] Bug report saved to: bug_reports/bug_report_1764909464068.sql
java.lang.AssertionError: MRUP Oracle: Result set mismatch!
Expected rows: 8
Actual rows: 8
First difference at row: Row 1: Expected [, NULL, 4], Got [, NULL, 6]
```

### After Generating README

```
✅ README generated: bug_reports/bug_report_1764909464068_README.md

View with: cat bug_reports/bug_report_1764909464068_README.md
```

### After Generating Index

```
✅ Index generated: bug_reports/INDEX.md

📖 View with: cat bug_reports/INDEX.md
```

---

## 🎯 Use Cases

### For Researchers

1. **Collect Evidence**: Run oracle with high query count
   ```bash
   java -jar target/sqlancer-2.0.0.jar --num-queries 1000 sqlite3 --oracle MRUP
   ```

2. **Generate Documentation**: Create READMEs for all bugs
   ```bash
   ./generate_all_bug_readmes.sh
   ```

3. **Analyze Results**: Review index and individual reports
   ```bash
   cat bug_reports/INDEX.md
   ```

---

### For Bug Reporting

1. **Find a Bug**: Run oracle
2. **Verify It**: Run the SQL script
3. **Document It**: Generate README
4. **Report It**: Send both `.sql` and `_README.md` to SQLite developers

---

### For Thesis/Paper

1. **Generate Large Dataset**: Run oracle with 1000+ queries
2. **Document All Bugs**: Generate all READMEs
3. **Create Summary**: Use INDEX.md for statistics
4. **Include Examples**: Pick interesting bugs from READMEs

---

## 🛠️ Maintenance

### Clean Up Old Bug Reports

```bash
# Remove all bug reports
rm -rf bug_reports/*

# Or remove specific ones
rm bug_reports/bug_report_XXXXX.*
```

### Regenerate Everything

```bash
# Regenerate all READMEs
./generate_all_bug_readmes.sh --force

# Regenerate index
./generate_bug_reports_index.sh
```

---

## 📈 Statistics

### Current Status

```bash
# Count bug reports
ls -1 bug_reports/bug_report_*.sql | wc -l

# Count READMEs
ls -1 bug_reports/bug_report_*_README.md | wc -l

# View index
cat bug_reports/INDEX.md
```

---

## 🔍 Filtering and Searching

### Find Specific Bug Types

```bash
# Find bugs with ROW_NUMBER()
grep -l "ROW_NUMBER()" bug_reports/*.sql

# Find bugs with SUM()
grep -l "SUM(" bug_reports/*.sql

# Find bugs with PARTITION BY
grep -l "PARTITION BY" bug_reports/*.sql
```

### Search in READMEs

```bash
# Find bugs with specific error patterns
grep -l "Row 1: Expected" bug_reports/*_README.md

# Count bugs by window function type
grep "Window Function" bug_reports/*_README.md | sort | uniq -c
```

---

## 💡 Tips

1. **Always verify bugs manually** before reporting to SQLite developers
2. **Generate READMEs** for better understanding and documentation
3. **Use the index** to quickly navigate through many bug reports
4. **Keep bug reports** for future reference and research
5. **Share both SQL and README** when reporting bugs

---

## 🐛 Troubleshooting

### Script Not Executable

```bash
chmod +x generate_bug_report_readme.sh
chmod +x generate_all_bug_readmes.sh
chmod +x generate_bug_reports_index.sh
```

### Bug Reports Not Generated

- Check if `bug_reports/` folder exists
- Rebuild the project: `mvn package -DskipTests -q`
- Check oracle output for errors

### README Generation Fails

- Ensure `sqlite3` is installed and in PATH
- Check if bug report SQL file is valid
- Try running the SQL manually first

---

## 📚 Related Documentation

- **MRUP Oracle Design**: `MRUP.md`
- **Implementation Details**: `MRUP_IMPLEMENTATION.md`
- **Bug Verification**: `BUG_VERIFICATION_REPORT.md`
- **Quick Guide**: `QUICK_VERIFICATION_GUIDE.md`

---

**Generated**: 2025-12-05  
**MRUP Oracle Version**: v1.0  
**Status**: Production Ready ✅

