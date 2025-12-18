# Chapter 3 Synchronization Plan
## Comparison with Oracle Specification

### Issues Found in Current Chapter 3

#### 1. **Table Generation Section** (Lines 102-127)
**Current**: Describes fixed schema with 5 columns (dept, salary, age, c0, c1)  
**Actual**: Variable schema with 3-7 columns, includes REAL type support  
**Fix**: Update to reflect variable column count (3-7) and type diversity

#### 2. **Mutation Strategies Section** (Lines 275-410)
**Current**: Describes window spec mutations and 5 CASE WHEN strategies  
**Actual**: Correctly describes strategies, but missing identity mutations  
**Fix**: Add identity mutation section (Stage 1, applied BEFORE CASE WHEN)

#### 3. **Workflow Description** (Lines 48)
**Current**: Says "Bước 4 áp dụng mutations để tăng diversity"  
**Actual**: Should specify 3 types: window spec (optional), identity (98%), CASE WHEN (100%)  
**Fix**: Be more specific about mutation pipeline

#### 4. **SQL Examples** (Multiple locations)
**Current**: Some examples use markdown-style comments  
**Actual**: Should use OSDI-style inline comments with ✓/✗  
**Fix**: Already mostly correct, verify all examples

#### 5. **Result Comparator** (Lines 412-477)
**Current**: Correctly describes 3-layer comparator  
**Actual**: Matches implementation  
**Fix**: No changes needed

#### 6. **Constraints C0-C5** (Lines 102-274)
**Current**: Correctly describes all constraints  
**Actual**: Matches implementation  
**Fix**: No changes needed

### Changes Required

#### Change 1: Update Table Generation Description
**Location**: Section 3.3, subsection "Table pair generation"  
**Current**: "schema cố định với 5 columns"  
**New**: "schema với 3-7 columns, type diversity"

#### Change 2: Add Identity Mutations Section
**Location**: Section 3.4, before CASE WHEN mutations  
**Add**: New subsection "Identity Mutations (Stage 1)"

#### Change 3: Update Workflow Description
**Location**: Section 3.2, subsection "Workflow tổng quan"  
**Current**: Generic "mutations"  
**New**: Specific "identity mutations (98%) + CASE WHEN mutations (100%)"

#### Change 4: Update Mutation Strategies Section Title
**Location**: Section 3.4  
**Current**: "Mutation strategies"  
**New**: "Mutation strategies" (keep, but restructure subsections)

### Sections That Are CORRECT (No Changes)
✅ Section 3.1: Động lực và bối cảnh  
✅ Section 3.2: Quan hệ metamorphic MRUP  
✅ Section 3.3: Chứng minh tính đúng đắn  
✅ Section 3.3: Constraints C0-C5 (all correct)  
✅ Section 3.5: Result comparator (3-layer)  
✅ Section 3.6: Kiến trúc và triển khai  
✅ Section 3.7: Tính chất của phương pháp  
✅ Section 3.8: So sánh với các phương pháp khác

### Summary
- **Total changes**: 3 major updates
- **Sections affected**: 2 (table generation, mutations)
- **New content**: 1 subsection (identity mutations)
- **Deletions**: 0 (all current content is accurate)
- **Approach**: Minimal changes, preserve existing structure

