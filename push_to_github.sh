#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# push_to_github.sh
# Commit any pending changes and push to GitHub using a Personal Access Token.
#
# Usage:
#   bash push_to_github.sh
#
# Requires:
#   GITHUB_PERSONAL_ACCESS_TOKEN  — set as a Replit Secret
# ──────────────────────────────────────────────────────────────────────────────
set -e

if [ -z "$GITHUB_PERSONAL_ACCESS_TOKEN" ]; then
  echo "ERROR: GITHUB_PERSONAL_ACCESS_TOKEN is not set."
  echo "Add it as a Replit Secret and re-run this script."
  exit 1
fi

REPO="https://${GITHUB_PERSONAL_ACCESS_TOKEN}@github.com/TITANICBHAI/FocusFlow-jvm.git"

# Stage and commit any pending changes
git config user.email "focusflow-bot@tbtechs.app" 2>/dev/null || true
git config user.name "FocusFlow Bot" 2>/dev/null || true

# Remove any stale lock file left by a previous interrupted process
rm -f .git/index.lock .git/MERGE_HEAD .git/CHERRY_PICK_HEAD 2>/dev/null || true

if ! git diff --quiet || ! git diff --staged --quiet || [ -n "$(git ls-files --others --exclude-standard)" ]; then
  echo "Staging and committing pending changes..."
  git add -A
  git commit -m "Add missing screens: Active, KeywordBlocker, BlockDefense, HowToUse, Changelog; redesign SideNav with grouped sections; add keyword blocker DB helpers"
  echo "Committed."
else
  echo "Nothing to commit — working tree clean."
fi

echo "Pushing HEAD → github.com/TITANICBHAI/FocusFlow-jvm (main)..."
git push "$REPO" HEAD:main
echo "Done. Visit https://github.com/TITANICBHAI/FocusFlow-jvm/actions to watch CI."
