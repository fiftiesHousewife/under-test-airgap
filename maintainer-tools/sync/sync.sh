#!/usr/bin/env bash
# For maintainers: the only way to push. Checks the build, commits, brings this repo's GitHub issues up to date
# with plan/airgap-backlog.yaml, refreshes docs/BACKLOG.md (a readable, ordered table of the open issues), commits that,
# and only then pushes, once.
#
#   maintainer-tools/sync/sync.sh "Commit message"
#
# Needs: JDK 25, git, Python 3, and a token that can write issues in UNDERTEST_GITHUB_TOKEN. If that isn't set and the
# GitHub CLI happens to be logged in, its token is borrowed. Safe to re-run: every step skips what is already done.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
MESSAGE="${1:?usage: maintainer-tools/sync/sync.sh \"Commit message\"}"
REPO="$(git remote get-url origin | sed -E 's#(git@[^:]+:|https://[^/]+/)##; s#\.git$##')"
COAUTHOR='Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'

echo "== Waiting for any earlier issue import for $REPO to finish"
# Only this repo's imports: the other repos' pushes run the same script on their own issues.
while pgrep -f "import_to_github.py $REPO " >/dev/null; do sleep 10; done

echo "== Build"
# Its own Gradle process, so other builds' daemons on this machine keep running.
./gradlew --no-daemon check

echo "== Commit"
git add -A -- . ':!.idea'
git diff --cached --quiet || git commit -q -m "$MESSAGE" -m "$COAUTHOR"

echo "== Issues and backlog page (before pushing)"
if [ -z "${UNDERTEST_GITHUB_TOKEN:-}" ] && command -v gh >/dev/null && env -u GITHUB_TOKEN gh auth status >/dev/null 2>&1; then
  UNDERTEST_GITHUB_TOKEN="$(env -u GITHUB_TOKEN gh auth token)"
fi
export UNDERTEST_GITHUB_TOKEN="${UNDERTEST_GITHUB_TOKEN:?set UNDERTEST_GITHUB_TOKEN to a token that can write issues}"
python3 -u tools/backlog-import/import_to_github.py "$REPO" --apply --file plan/airgap-backlog.yaml --page docs/BACKLOG.md
git add docs/BACKLOG.md
git diff --cached --quiet || git commit -q -m "Refresh backlog page" -m "$COAUTHOR"

echo "== Push"
# A branch's first push links it to the same branch on origin; after that, a plain push.
if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
  UNDERTEST_SYNC=1 git push
else
  UNDERTEST_SYNC=1 git push --set-upstream origin HEAD
fi
echo "== Done"
