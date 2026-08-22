# AGENTS

## GitHub routines — always auto-run, no reminder needed

Before/during/after any GitHub-related work:
- Check `git status`, `git diff`, `git log --oneline -5`, `git remote -v`, `gh auth status`
- Commit with concise message after each fix/feature
- Push to `origin/main` immediately (or feature branch + PR if requested)
- For multi-step fixes: commit + push incrementally, don't wait for manual prompt
- Ignore `core.fileMode` false (sdcard fuse) — use `HOME=/data/data/com.termux/files/home`
