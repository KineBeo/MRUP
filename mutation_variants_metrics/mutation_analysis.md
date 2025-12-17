# MRUP ORACLE - MUTATION STATISTICS DASHBOARD

**Generated:** 2025-12-17 07:37:54

📊 **Total Queries Analyzed:** 5123

---

## 📍 PHASE 1: Window Spec Mutations

| Indicator | Variant | Percentage | Count | Visual |
|-----------|---------|------------|-------|--------|
| ✅ | Redundant PARTITION BY | 44.17% | 2263x | ██████████████████████ |
| ✅ | Window Spec Mutation | 25.79% | 1321x | ████████████ |
| ✅ | Order-Preserving Transform | 24.93% | 1277x | ████████████ |
| ✅ | None |  5.11% |  262x | ██ |
| ⚠️ | NULLS FIRST/LAST Toggle |  0.00% |    0x |  |

---

## 📍 STAGE 1: Identity Wrapper Mutations

| Indicator | Variant | Percentage | Count | Visual |
|-----------|---------|------------|-------|--------|
| ✅ | Arithmetic Identity (+ 0) | 10.87% |  557x | █████ |
| ✅ | Arithmetic Identity (- 0) | 10.76% |  551x | █████ |
| ✅ | Arithmetic Identity (* 1) | 10.66% |  546x | █████ |
| ✅ | Arithmetic Identity (/ 1) |  6.03% |  309x | ███ |
| ✅ | Chained Identity (+ 0 - 0) |  5.66% |  290x | ██ |
| ✅ | Parentheses Wrapping (single) |  5.47% |  280x | ██ |
| ✅ | NULL-Safe Identity (IFNULL) |  5.33% |  273x | ██ |
| ✅ | Chained Identity (* 1 * 1) |  5.33% |  273x | ██ |
| ✅ | Type Cast Identity (REAL) |  5.27% |  270x | ██ |
| ✅ | Parentheses Wrapping (double) |  5.25% |  269x | ██ |
| ✅ | Type Cast Identity (INTEGER) |  5.15% |  264x | ██ |
| ✅ | Arithmetic Identity (1 *) |  5.13% |  263x | ██ |
| ✅ | Arithmetic Identity (0 +) |  5.09% |  261x | ██ |
| ✅ | NULL-Safe Identity (COALESCE) |  5.02% |  257x | ██ |
| 🔹 | Rounding Identity |  4.88% |  250x | ██ |
| 🔹 | None |  4.10% |  210x | ██ |

---

## 📍 PHASE 3: CASE WHEN Mutations

| Indicator | Variant | Percentage | Count | Visual |
|-----------|---------|------------|-------|--------|
| ✅ | Window Function in WHEN | 27.52% | 1410x | █████████████ |
| ✅ | Constant Condition | 26.92% | 1379x | █████████████ |
| ✅ | Identical Branches | 18.52% |  949x | █████████ |
| ✅ | Different Window Functions | 17.45% |  894x | ████████ |
| ✅ | NULL Handling |  9.58% |  491x | ████ |
| ⚠️ | Constant Condition (fallback) |  0.00% |    0x |  |

---


---

## 🔗 TOP 10 MUTATION COMBINATIONS

1. **[ 1.50%]** ( 77x) Redundant PARTITION BY + Arithmetic Identity (+ 0) + Window Function in WHEN
2. **[ 1.46%]** ( 75x) Redundant PARTITION BY + Arithmetic Identity (* 1) + Window Function in WHEN
3. **[ 1.37%]** ( 70x) Redundant PARTITION BY + Arithmetic Identity (- 0) + Window Function in WHEN
4. **[ 1.33%]** ( 68x) Redundant PARTITION BY + Arithmetic Identity (* 1) + Constant Condition
5. **[ 1.31%]** ( 67x) Redundant PARTITION BY + Arithmetic Identity (- 0) + Constant Condition
6. **[ 1.07%]** ( 55x) Redundant PARTITION BY + Arithmetic Identity (+ 0) + Constant Condition
7. **[ 1.00%]** ( 51x) Redundant PARTITION BY + Arithmetic Identity (+ 0) + Different Window Functions
8. **[ 0.86%]** ( 44x) Order-Preserving Transform + Arithmetic Identity (- 0) + Window Function in WHEN
9. **[ 0.86%]** ( 44x) Window Spec Mutation + Arithmetic Identity (- 0) + Window Function in WHEN
10. **[ 0.84%]** ( 43x) Redundant PARTITION BY + Arithmetic Identity (- 0) + Identical Branches

---

## 💡 RECOMMENDATIONS

1. 🔸 **CASE WHEN variant 'NULL Handling' at 9.6%** (expected ~20.0%)
   - Check weighted random selection in Phase 3


---

## 🎯 BUG-FINDING POTENTIAL

- **Identity Variants:** 15/15 active (100.0%)
- **CASE WHEN Variants:** 5/5 active (100.0%)
- **Window Spec Variants:** 3/3 active (100.0%)

✅ **Excellent coverage (100.0%)** - All mutation types exercised!

---