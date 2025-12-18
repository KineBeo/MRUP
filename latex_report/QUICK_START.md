# MRUP Oracle Report - Quick Start Guide

## ✅ What's Been Done

All main chapters have been written:
- ✅ Chap0_Introduction.tex (Introduction)
- ✅ Chap1_Background.tex (Window Functions background)
- ✅ Chap2_RelatedWork.tex (Survey of DBMS testing)
- ✅ Chap3_Design.tex (MRUP Oracle design & implementation)
- ✅ Chap4_Experiments.tex (Experiments and evaluation)
- ✅ Chap5_Conclusion.tex (Conclusion and future work)
- ✅ main.tex (Updated with new references)

## 🚀 Quick Compilation (3 Steps)

### Step 1: Create Minimal Cover Files

Create a `cover/` directory and add these minimal files:

**cover/Biangoai.tex:**
```latex
\begin{center}
\vspace{2cm}
{\Large\bfseries MRUP ORACLE REPORT}\\
\vspace{1cm}
{\large Testing Window Functions in SQLite}\\
\vspace{2cm}
{\normalsize [Your Name]}\\
\vspace{1cm}
{\normalsize 2024}
\end{center}
\clearpage
```

**cover/Biatrong.tex:**
```latex
\clearpage
```

**cover/loicamdoan.tex:**
```latex
\chapter*{Declaration}
This is my original work.
\clearpage
```

**cover/loicamon.tex:**
```latex
\chapter*{Acknowledgments}
I would like to thank my advisor and family.
\clearpage
```

**cover/tomtat.tex:**
```latex
\chapter*{Abstract}
This report presents the MRUP Oracle for testing window functions in SQLite.
\clearpage
```

**cover/phuluc.tex:**
```latex
\chapter*{Appendix}
% Add supplementary materials here
\clearpage
```

### Step 2: Create Empty Figures Directory

```bash
mkdir -p latex_report/figures
```

### Step 3: Compile

```bash
cd latex_report
pdflatex main.tex
bibtex main
pdflatex main.tex
pdflatex main.tex
```

## 📊 Report Statistics

- **Total Pages**: ~75-95 pages
- **Chapters**: 6 (0-5)
- **Sections**: ~25
- **Tables**: ~13
- **Algorithms**: ~3
- **References**: ~10+

## 📝 Key Content Summary

### Chapter 0: Introduction (4-5 pages)
- Why test window functions?
- MRUP Oracle objectives
- Research methodology
- Contributions

### Chapter 1: Background (12-15 pages)
- Window Functions 101
- SQLite specifics
- Testing challenges

### Chapter 2: Related Work (10-12 pages)
- PQS, TLP, NoREC, EET
- Research gap
- Why MRUP is needed

### Chapter 3: Design (15-18 pages)
- MRUP metamorphic relation
- Constraint system (C0-C5)
- Three-layer comparator
- Implementation in SQLancer

### Chapter 4: Experiments (12-15 pages)
- 4 experiments
- Metrics: diversity, performance, effectiveness
- Results: 98.47% unique, 0% FP, 70.3 q/s
- Future roadmap (Tier 1-4)

### Chapter 5: Conclusion (8-10 pages)
- Achievements: 5/6 objectives
- Limitations: no bugs found yet
- Future work: 25-47 bugs expected
- Impact on DBMS testing

## 🎯 Main Results

| Metric | Result | Target | Status |
|--------|--------|--------|--------|
| Unique Queries | 98.47% | >90% | ✅ |
| Mutation Coverage | 94.2% | >90% | ✅ |
| False Positives | 0% | <1% | ✅ |
| Throughput | 70.3 q/s | >50 q/s | ✅ |
| Bugs Found | 0 | >0 | ❌ |

## 🔧 Customization Checklist

Before final submission, customize these:

- [ ] Add your name in cover pages
- [ ] Add student ID
- [ ] Add advisor name
- [ ] Add university logo (if required)
- [ ] Update dates
- [ ] Add acknowledgments
- [ ] Add Vietnamese abstract (if required)
- [ ] Add specific experiment dates
- [ ] Review all technical content
- [ ] Check for typos

## 📚 Bibliography

The report includes references to:
- Manuel Rigger & Zhendong Su (PQS, TLP, NoREC, SQLancer)
- SQL standard documentation
- SQLite documentation
- Metamorphic testing literature

## 🐛 Common Issues & Solutions

### Issue 1: Missing vietnam package
**Solution:** Install texlive-lang-other
```bash
sudo apt-get install texlive-lang-other
```

### Issue 2: Missing algorithm package
**Solution:** Install texlive-science
```bash
sudo apt-get install texlive-science
```

### Issue 3: Missing tikz
**Solution:** Install texlive-pictures
```bash
sudo apt-get install texlive-pictures
```

### Issue 4: Bibliography not showing
**Solution:** Run bibtex after first pdflatex
```bash
pdflatex main.tex
bibtex main
pdflatex main.tex
pdflatex main.tex
```

## 🎨 Optional Enhancements

### Add Diagrams
Use TikZ to create:
- MRUP workflow diagram
- Three-layer comparator flowchart
- Constraint system visualization

### Add Charts
Use pgfplots to create:
- Performance comparison bar chart
- Query diversity pie chart
- Cross-version testing line chart

### Add Code Snippets
In appendix, add:
- Sample MRUP queries
- Constraint checking code
- Comparator pseudocode

## 📖 Reading Order for Review

1. **Chapter 0** - Get overview
2. **Chapter 1** - Understand window functions
3. **Chapter 3** - See MRUP design (skip Chapter 2 if in hurry)
4. **Chapter 4** - Check experimental results
5. **Chapter 5** - Read conclusions
6. **Chapter 2** - Read related work for completeness

## 🚀 Next Steps After Compilation

1. **Review PDF**
   - Check all pages render correctly
   - Verify table of contents
   - Check all references

2. **Get Feedback**
   - Share with advisor
   - Get peer review
   - Incorporate suggestions

3. **Final Polish**
   - Fix any typos
   - Improve figures
   - Enhance explanations

4. **Submit**
   - Print if required
   - Submit PDF
   - Archive source files

## 📞 Support

If you encounter issues:
1. Check README_COMPILE.md for detailed instructions
2. Check REPORT_SUMMARY.md for content overview
3. Review LaTeX error messages carefully
4. Google specific LaTeX errors
5. Ask on TeX StackExchange if needed

## ✨ Final Notes

This report represents a complete documentation of the MRUP Oracle project. It:
- ✅ Follows academic standards
- ✅ Includes theoretical foundations
- ✅ Documents implementation details
- ✅ Presents experimental results
- ✅ Discusses limitations honestly
- ✅ Proposes concrete future work

**The report is ready for compilation and submission!**

Good luck with your project! 🎓

