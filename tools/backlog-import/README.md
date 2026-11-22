# Backlog import

Loads [this repo's backlog](../../plan/airgap-backlog.yaml) into its GitHub issues: one issue per item, with labels for
its type, owner and horizon, and links between items that wait on each other. Re-runs update the issues instead of
duplicating them, close items marked `status: done`, and close as not planned any issue whose item has left the file.
It also writes [the backlog page](../../docs/BACKLOG.md), an ordered table of the open issues.

[`import_to_github.py`](import_to_github.py) uses only Python's standard library and GitHub's REST API, so it needs no
extra tools. It runs as part of `maintainer-tools/sync/sync.sh`; on its own:

```bash
python3 tools/backlog-import/import_to_github.py OWNER/REPO            # dry run: shows what would change
python3 tools/backlog-import/import_to_github.py OWNER/REPO --apply    # needs a token in UNDERTEST_GITHUB_TOKEN
```
