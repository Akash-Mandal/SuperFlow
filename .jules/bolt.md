# Bolt's Journal - Critical Learnings

## 2026-08-26 - Allocation-Free Search & Fuzzy Matching in SuperFlow
**Learning:** `Search.relevance` and `Fuzzy.bestMatch` are called frequently across workspace items. Higher-order collection chains (`filter` + `map`) and repeated `IntArray`/`Pair` allocations inside Levenshtein calculations were causing high heap churn during workspace search.
**Action:** Use single-pass loops over `vararg` fields with early returns for exact matches (`1.0f`), skip Levenshtein computation when prefix/contains matches exist, and reuse thread-local buffers for Levenshtein dynamic programming arrays.

## 2026-09-04 - Fuzzy Candidate Matching Pruning Bounds
**Learning:** In fuzzy string search across lists of candidates using Levenshtein distance, computing dynamic programming matrices for candidates with large string length differences is wasted computation. Since the Levenshtein edit distance between two strings $s$ and $t$ is lower-bounded by $|len(s) - len(t)|$, the maximum achievable similarity score is $1.0 - \frac{|len(s) - len(t)|}{\max(len(s), len(t))}$. Pruning candidates whose maximum possible similarity score cannot exceed the current `bestScore` or `minThreshold` eliminates up to 90%+ of Levenshtein matrix calculations in candidate ranking loops.

**Action:** Before invoking expensive $O(N \cdot M)$ string comparison algorithms like Levenshtein distance inside candidate ranking loops, compute cheap scalar length difference bounds to filter out ineligible candidates upfront.
