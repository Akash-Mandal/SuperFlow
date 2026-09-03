# AGENTS

## GitHub routines — always auto-run, no reminder needed

Before/during/after any GitHub-related work:
- Check `git status`, `git diff`, `git log --oneline -5`, `git remote -v`, `gh auth status`
- Commit with concise message after each fix/feature
- Do NOT push or trigger CI workflows automatically unless explicitly instructed by the user (to avoid unnecessary GitHub Actions CI usage and costs)
- Ignore `core.fileMode` false (sdcard fuse) — use `HOME=/data/data/com.termux/files/home`
