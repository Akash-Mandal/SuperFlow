# Bolt's Journal - Critical Learnings

## 2026-09-04 - Fuzzy Candidate Matching Pruning Bounds
**Learning:** In fuzzy string search across lists of candidates using Levenshtein distance, computing dynamic programming matrices for candidates with large string length differences is wasted computation. Since the Levenshtein edit distance between two strings $s$ and $t$ is lower-bounded by $|len(s) - len(t)|$, the maximum achievable similarity score is $1.0 - \frac{|len(s) - len(t)|}{\max(len(s), len(t))}$. Pruning candidates whose maximum possible similarity score cannot exceed the current `bestScore` or `minThreshold` eliminates up to 90%+ of Levenshtein matrix calculations in candidate ranking loops.

**Action:** Before invoking expensive $O(N \cdot M)$ string comparison algorithms like Levenshtein distance inside candidate ranking loops, compute cheap scalar length difference bounds to filter out ineligible candidates upfront.
