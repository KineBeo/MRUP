# MRUP Oracle - Documentation Index

## 📚 Complete Documentation Guide

This index provides a comprehensive overview of all MRUP oracle documentation.

---

## 🚀 Quick Start

**New to MRUP?** Start here:

1. **[MRUP_README.md](MRUP_README.md)** - Quick start guide
2. **[MRUP_EXAMPLE.md](MRUP_EXAMPLE.md)** - Concrete examples
3. **[test_mrup_oracle.sh](test_mrup_oracle.sh)** - Run a test

**Time required**: 15 minutes

---

## 📖 Documentation Files

### 1. MRUP_README.md
**Purpose**: Quick start and overview  
**Audience**: All users  
**Length**: ~400 lines  

**Contents**:
- What is MRUP?
- Installation & running
- Key features
- How it works (5-step process)
- Window functions tested
- Usage examples
- Performance benchmarks

**When to read**: First document to read

---

### 2. MRUP.md
**Purpose**: Complete research document  
**Audience**: Researchers, developers  
**Length**: 386 lines  

**Contents**:
- Foundation knowledge of window functions
- Overview of MRUP approach
- 50+ mutation strategies
- 3 generation algorithms (OSRB, OMTG, GGSG)
- Top 10 high-impact mutations
- Algorithm comparisons

**When to read**: For deep understanding of the research

---

### 3. MRUP_IMPLEMENTATION.md
**Purpose**: Detailed implementation guide  
**Audience**: Developers, maintainers  
**Length**: ~450 lines  

**Contents**:
- Architecture overview
- 5-step process detailed explanation
- OSRB algorithm specification
- File structure
- Key classes and methods
- Usage instructions
- Expected errors handling
- Limitations and future work
- Research context

**When to read**: Before modifying the code

---

### 4. MRUP_EXAMPLE.md
**Purpose**: Concrete execution examples  
**Audience**: All users  
**Length**: ~350 lines  

**Contents**:
- Step-by-step test case walkthrough
- Simple example (ROW_NUMBER)
- Example with PARTITION BY
- Example with FRAME clause
- Bug detection scenario
- Real-world testing output

**When to read**: To understand how the oracle works in practice

---

### 5. OSRB_ALGORITHM.md
**Purpose**: Algorithm reference  
**Audience**: Developers, researchers  
**Length**: ~500 lines  

**Contents**:
- OSRB algorithm design philosophy
- Grammar specification
- Pseudocode and Java implementation
- Example outputs
- Probability distributions
- Advantages and comparisons
- Mutation extensions
- Performance characteristics
- Best practices

**When to read**: To understand or modify the generation algorithm

---

### 6. ARCHITECTURE.md
**Purpose**: System architecture documentation  
**Audience**: Developers, architects  
**Length**: ~400 lines  

**Contents**:
- System architecture diagram
- Data flow diagram
- Component interaction diagram
- OSRB algorithm flow
- Error handling flow
- Class hierarchy
- Integration points
- Deployment architecture

**When to read**: To understand the system design

---

### 7. IMPLEMENTATION_SUMMARY.md
**Purpose**: Project completion summary  
**Audience**: Project managers, stakeholders  
**Length**: ~500 lines  

**Contents**:
- What was implemented
- Testing results
- Documentation created
- File structure
- How to use
- Key achievements
- Constraints met
- Future work
- Research paper potential

**When to read**: For project overview and status

---

### 8. INDEX.md
**Purpose**: Documentation navigation  
**Audience**: All users  
**Length**: This file  

**Contents**:
- Complete documentation index
- Reading paths for different audiences
- Quick reference
- File organization

**When to read**: To navigate the documentation

---

## 🎯 Reading Paths

### For First-Time Users

```
1. MRUP_README.md (15 min)
   ↓
2. MRUP_EXAMPLE.md (20 min)
   ↓
3. Run: ./test_mrup_oracle.sh (5 min)
   ↓
4. IMPLEMENTATION_SUMMARY.md (optional, 15 min)
```

**Total time**: 40-55 minutes

---

### For Developers

```
1. MRUP_README.md (15 min)
   ↓
2. ARCHITECTURE.md (25 min)
   ↓
3. MRUP_IMPLEMENTATION.md (30 min)
   ↓
4. OSRB_ALGORITHM.md (25 min)
   ↓
5. Read source code:
   - SQLite3MRUPOracle.java (20 min)
   - SQLite3OracleFactory.java (5 min)
```

**Total time**: 2 hours

---

### For Researchers

```
1. MRUP_README.md (15 min)
   ↓
2. MRUP.md (45 min)
   ↓
3. OSRB_ALGORITHM.md (25 min)
   ↓
4. MRUP_IMPLEMENTATION.md (30 min)
   ↓
5. IMPLEMENTATION_SUMMARY.md (15 min)
```

**Total time**: 2.5 hours

---

### For Project Managers

```
1. IMPLEMENTATION_SUMMARY.md (15 min)
   ↓
2. MRUP_README.md (15 min)
   ↓
3. MRUP_EXAMPLE.md (optional, 20 min)
```

**Total time**: 30-50 minutes

---

## 📁 File Organization

```
MRUP/
├── Documentation/
│   ├── INDEX.md                        ← You are here
│   ├── MRUP_README.md                  ← Start here
│   ├── MRUP.md                         ← Research document
│   ├── MRUP_IMPLEMENTATION.md          ← Implementation guide
│   ├── MRUP_EXAMPLE.md                 ← Examples
│   ├── OSRB_ALGORITHM.md               ← Algorithm reference
│   ├── ARCHITECTURE.md                 ← System architecture
│   └── IMPLEMENTATION_SUMMARY.md       ← Project summary
│
├── Source Code/
│   └── src/sqlancer/sqlite3/
│       ├── oracle/
│       │   └── SQLite3MRUPOracle.java  ← Main implementation
│       └── SQLite3OracleFactory.java   ← Oracle registration
│
├── Scripts/
│   └── test_mrup_oracle.sh             ← Test script
│
└── Build/
    ├── pom.xml                         ← Maven config
    └── target/sqlancer-*.jar           ← Compiled JAR
```

---

## 🔍 Quick Reference

### Key Concepts

| Concept | Description | Document |
|---------|-------------|----------|
| MRUP | MR-UNION-PARTITION metamorphic oracle | MRUP_README.md |
| OSRB | OVER-Spec Random Builder algorithm | OSRB_ALGORITHM.md |
| 5 Steps | Generate → Union → Spec → Execute → Compare | MRUP_IMPLEMENTATION.md |
| Window Functions | ROW_NUMBER, RANK, SUM, AVG, etc. | MRUP.md |
| Cardinality Check | \|Q_union\| = \|Q1\| + \|Q2\| | MRUP_EXAMPLE.md |

### Commands

| Command | Purpose | Document |
|---------|---------|----------|
| `mvn clean package` | Build project | MRUP_README.md |
| `java -jar ... --oracle MRUP` | Run oracle | MRUP_README.md |
| `./test_mrup_oracle.sh` | Run test script | test_mrup_oracle.sh |

### Files

| File | Purpose | Lines |
|------|---------|-------|
| SQLite3MRUPOracle.java | Main implementation | ~320 |
| SQLite3OracleFactory.java | Oracle registration | +15 |
| MRUP_README.md | Quick start | ~400 |
| MRUP.md | Research document | 386 |
| MRUP_IMPLEMENTATION.md | Implementation guide | ~450 |
| MRUP_EXAMPLE.md | Examples | ~350 |
| OSRB_ALGORITHM.md | Algorithm reference | ~500 |
| ARCHITECTURE.md | System architecture | ~400 |
| IMPLEMENTATION_SUMMARY.md | Project summary | ~500 |

---

## 📊 Documentation Statistics

### Total Documentation

- **Files**: 8 main documents + 1 test script
- **Lines**: ~3,000+ lines of documentation
- **Words**: ~25,000+ words
- **Code**: ~320 lines of implementation

### Coverage

✅ **Quick Start**: MRUP_README.md  
✅ **Research**: MRUP.md  
✅ **Implementation**: MRUP_IMPLEMENTATION.md  
✅ **Examples**: MRUP_EXAMPLE.md  
✅ **Algorithm**: OSRB_ALGORITHM.md  
✅ **Architecture**: ARCHITECTURE.md  
✅ **Summary**: IMPLEMENTATION_SUMMARY.md  
✅ **Navigation**: INDEX.md  

---

## 🎓 Learning Objectives

### After Reading All Documentation

You will understand:

1. ✅ What MRUP is and why it's needed
2. ✅ How window functions work
3. ✅ The 5-step metamorphic testing process
4. ✅ The OSRB algorithm design and implementation
5. ✅ How to use the oracle
6. ✅ How to extend the oracle
7. ✅ The system architecture
8. ✅ Future research directions

---

## 🔗 External Resources

### SQLancer

- **Repository**: https://github.com/sqlancer/sqlancer
- **Paper**: "Detecting Logic Bugs in DBMS" (OSDI'20)

### SQLite Window Functions

- **Documentation**: https://www.sqlite.org/windowfunctions.html
- **SQL:2003 Standard**: Window function specification

### Related Research

- **PQS**: Pivoted Query Synthesis
- **TLP**: Ternary Logic Partitioning
- **NoREC**: Non-Optimizing Reference Engine Construction

---

## 📝 Document Maintenance

### Last Updated

- **Date**: November 29, 2025
- **Version**: 1.0 (POC)
- **Status**: Complete

### Future Updates

When extending the oracle:

1. Update **MRUP_IMPLEMENTATION.md** with new features
2. Add examples to **MRUP_EXAMPLE.md**
3. Update **OSRB_ALGORITHM.md** if algorithm changes
4. Update **ARCHITECTURE.md** if structure changes
5. Update **IMPLEMENTATION_SUMMARY.md** with progress

---

## 🤝 Contributing

To contribute documentation:

1. Follow the existing style and structure
2. Update this INDEX.md when adding new documents
3. Keep documents focused and concise
4. Include examples and diagrams
5. Cross-reference related documents

---

## 📧 Contact

For questions about the documentation:

- Open an issue on GitHub
- Submit a pull request
- Contact the maintainers

---

## ✅ Checklist

Use this checklist to track your progress:

### First-Time Users
- [ ] Read MRUP_README.md
- [ ] Read MRUP_EXAMPLE.md
- [ ] Run test_mrup_oracle.sh
- [ ] Understand the 5-step process

### Developers
- [ ] Read MRUP_README.md
- [ ] Read ARCHITECTURE.md
- [ ] Read MRUP_IMPLEMENTATION.md
- [ ] Read OSRB_ALGORITHM.md
- [ ] Read source code
- [ ] Run and modify the oracle

### Researchers
- [ ] Read MRUP_README.md
- [ ] Read MRUP.md (full research document)
- [ ] Read OSRB_ALGORITHM.md
- [ ] Read MRUP_IMPLEMENTATION.md
- [ ] Understand mutation strategies
- [ ] Plan future research

---

## 🎉 Conclusion

This documentation provides comprehensive coverage of the MRUP oracle:

✅ **Complete**: All aspects documented  
✅ **Organized**: Clear structure and navigation  
✅ **Accessible**: Multiple reading paths  
✅ **Practical**: Examples and usage instructions  
✅ **Research-Ready**: Detailed algorithm and design  

Happy reading! 📚

---

**Document**: INDEX.md  
**Purpose**: Documentation navigation  
**Status**: Complete  
**Version**: 1.0

