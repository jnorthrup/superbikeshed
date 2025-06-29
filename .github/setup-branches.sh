#!/bin/bash

# Setup script for zero-error release branches
# Run this once to configure the dev->release->main branch structure

echo "Setting up branch structure for zero-error releases..."

# Ensure we're on main
git checkout main || { echo "Error: Could not checkout main branch"; exit 1; }

# Create release branch from main if it doesn't exist
if ! git show-ref --verify --quiet refs/heads/release; then
    echo "Creating release branch..."
    git checkout -b release
    git push -u origin release
    echo "✅ Release branch created"
else
    echo "✅ Release branch already exists"
fi

# Create dev branch from main if it doesn't exist
if ! git show-ref --verify --quiet refs/heads/dev; then
    echo "Creating dev branch..."
    git checkout main
    git checkout -b dev
    git push -u origin dev
    echo "✅ Dev branch created"
else
    echo "✅ Dev branch already exists"
fi

# Switch to dev as the default working branch
git checkout dev

echo ""
echo "Branch structure ready!"
echo "  dev     → Active development (push here)"
echo "  release → Auto-merged on zero errors"
echo "  main    → Stable releases only"
echo ""
echo "GitHub branch protection rules recommended:"
echo "  - main: Require PR, no direct pushes"
echo "  - release: Allow GitHub Actions only"
echo "  - dev: Allow direct pushes"