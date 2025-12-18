# Rewrite Notes - Natural Academic Style

## Style Guidelines from Original Template (Chap2_GNUSDR.tex)

### Key Characteristics:
1. **Flowing paragraphs** - Not bullet points for main content
2. **Natural transitions** - Sentences connect smoothly
3. **Academic tone** - Formal but readable Vietnamese
4. **Itemize only for lists** - Use \begin{itemize} sparingly, mainly for technical lists
5. **Descriptive writing** - Explain concepts in full sentences
6. **Citations integrated naturally** - \cite{} within sentences
7. **Figures and tables** - Referenced naturally in text flow

### What to AVOID:
- ❌ Starting sections with bullet points
- ❌ "Ưu điểm:" followed by bullets
- ❌ "Hạn chế:" followed by bullets  
- ❌ "Đặc điểm:" followed by bullets
- ❌ Too many nested bullet points
- ❌ Short, choppy sentences

### What to DO:
- ✅ Write in flowing paragraphs
- ✅ Use transitional phrases
- ✅ Integrate lists naturally into prose
- ✅ Use itemize only for actual technical lists (like function names, parameters)
- ✅ Write like a human researcher, not AI

### Example Transformation:

**BAD (AI-style):**
```
\textbf{Ưu điểm:}
\begin{itemize}
    \item Không cần oracle
    \item Phát hiện lỗi WHERE clause
    \item Kết quả ấn tượng
\end{itemize}
```

**GOOD (Human-style):**
```
PQS có nhiều ưu điểm nổi bật. Trước hết, phương pháp này không cần một oracle phức tạp mà chỉ cần so sánh kết quả giữa các query được phân vùng. PQS đặc biệt hiệu quả trong việc phát hiện lỗi optimization của WHERE clause và đã đạt được kết quả ấn tượng với 92 bugs được phát hiện trong các DBMS phổ biến như SQLite, MySQL, và PostgreSQL.
```

## Rewrite Status:
- [ ] Chap1_Background.tex
- [ ] Chap2_RelatedWork.tex  
- [ ] Chap3_Design.tex
- [ ] Chap4_Experiments.tex
- [ ] Chap5_Conclusion.tex
