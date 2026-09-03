## 2026-08-26 - Allocation-Free Search & Fuzzy Matching in SuperFlow
**Learning:** `Search.relevance` and `Fuzzy.bestMatch` are called frequently across workspace items. Higher-order collection chains (`filter` + `map`) and repeated `IntArray`/`Pair` allocations inside Levenshtein calculations were causing high heap churn during workspace search.
**Action:** Use single-pass loops over `vararg` fields with early returns for exact matches (`1.0f`), skip Levenshtein computation when prefix/contains matches exist, and reuse thread-local buffers for Levenshtein dynamic programming arrays.
