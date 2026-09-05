# Bolt's Journal - Critical Learnings

## 2026-08-26 - Allocation-Free Search & Fuzzy Matching in SuperFlow
**Learning:** `Search.relevance` and `Fuzzy.bestMatch` are called frequently across workspace items. Higher-order collection chains (`filter` + `map`) and repeated `IntArray`/`Pair` allocations inside Levenshtein calculations were causing high heap churn during workspace search.
**Action:** Use single-pass loops over `vararg` fields with early returns for exact matches (`1.0f`), skip Levenshtein computation when prefix/contains matches exist, and reuse thread-local buffers for Levenshtein dynamic programming arrays.

## 2026-09-04 - Fuzzy Candidate Matching Pruning Bounds
**Learning:** In fuzzy string search across lists of candidates using Levenshtein distance, computing dynamic programming matrices for candidates with large string length differences is wasted computation. Since the Levenshtein edit distance between two strings $s$ and $t$ is lower-bounded by $|len(s) - len(t)|$, the maximum achievable similarity score is $1.0 - \frac{|len(s) - len(t)|}{\max(len(s), len(t))}$. Pruning candidates whose maximum possible similarity score cannot exceed the current `bestScore` or `minThreshold` eliminates up to 90%+ of Levenshtein matrix calculations in candidate ranking loops.

**Action:** Before invoking expensive $O(N \cdot M)$ string comparison algorithms like Levenshtein distance inside candidate ranking loops, compute cheap scalar length difference bounds to filter out ineligible candidates upfront.
## 2026-08-26 - Single-Pass Lazy Evaluation for Multi-Field Search Relevance
**Learning:** Evaluated search scoring functions like `Search.relevance` that take `vararg fields` often allocate intermediate collections via `filter` and `map` on every record. Iterating over fields in a single pass with early returns (e.g. `1.0f` on exact match) and guarding expensive calculations like Levenshtein distance (`maxScore < 0.2f`) avoids garbage collection overhead and multi-pass field scans.
**Action:** In search or filtering paths over large datasets, evaluate fields lazily in a single loop and skip expensive fuzzy/edit-distance functions when a higher relevance threshold is already satisfied.
