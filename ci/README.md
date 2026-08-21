# CI workflow (staged — needs to be moved into place)

`ci.yml` in this directory is the SuperFlow CI pipeline. **It is inert here.**
GitHub only runs workflows stored in `.github/workflows/`.

It lives here because the agent that authored it pushes with a GitHub App
token that lacks the `workflows` permission, so GitHub rejects any push that
creates or updates a file under `.github/workflows/`. Staging the file at a
normal path keeps it reviewable in the pull request.

## Activating it

From the repository root, with credentials that carry the `workflow` scope:

```bash
mkdir -p .github/workflows
git mv ci/ci.yml .github/workflows/ci.yml
git rm ci/README.md
git commit -m "Activate CI workflow"
git push
```

The pipeline itself is documented in [`../docs/BUILD.md`](../docs/BUILD.md).
