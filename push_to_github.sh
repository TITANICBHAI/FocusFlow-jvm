#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# push_to_github.sh
# Push the current HEAD to GitHub using a Personal Access Token.
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

echo "Pushing HEAD → github.com/TITANICBHAI/FocusFlow-jvm (main)..."
git push "$REPO" HEAD:main
echo "Done. Visit https://github.com/TITANICBHAI/FocusFlow-jvm/actions to watch CI."
