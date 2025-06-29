#!/bin/bash
set -euo pipefail

# Configuration
LLM_SCRIPT="./llm_invoke.sh"
OUTPUT_DIR="salvage_triage_output"

# Validate LLM script exists
if [ ! -f "$LLM_SCRIPT" ]; then
  echo "Error: LLM script not found at $LLM_SCRIPT"
  exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Commit Logging
echo "Logging commits for branches golden_age, retrtace, and others..."
git log --format="%H,%an,%ad" golden_age retrtace $(git branch --list | cut -d' ' -f2-) > "$OUTPUT_DIR/commit_log.txt"

# CommitDiff Metrics
echo "Calculating commit diff metrics..."
git log --numstat -z --all | 
  awk -F'\t' '$1 != "" {add+=$1; rem+=$2; cnt++} 
   END {print add","rem","cnt,"(add+rem)/cnt >1?"avg"}' > "$OUTPUT_DIR/commit_diff_metrics.csv"

# Octopus Merge Setup & Conflict Logging
echo "Temporarily merging all branches to identify conflicts..."
MERGE_BRANCH="temp_octopus_merge"
git checkout -b "$MERGE_BRANCH"
BRANCHES=$(git branch --list | cut -d' ' -f2-)
git merge -m "Temp octopus merge" $BRANCHES || true  # Continue even if merge fails
git diff --name-only -u > "$OUTPUT_DIR/merge_conflicts.log"
git checkout -
git branch -D "$MERGE_BRANCH"

# Code Analysis & Triage
echo "Analyzing conflicts and categorizing code..."
if [ -s "$OUTPUT_DIR/merge_conflicts.log" ]; then
  "$LLM_SCRIPT" "salvage-triage" "$OUTPUT_DIR/merge_conflicts.log"
  
  # Create directories for categorized code
  mkdir -p "$OUTPUT_DIR/needs_re-integration" "$OUTPUT_DIR/needs-recovery-document"
  
  echo "Analysis complete. Results available in $OUTPUT_DIR/"
else
  echo "No conflicts found to analyze."
fi

# Cleanup
echo "Cleaning up temporary files..."
rm -f "$OUTPUT_DIR/merge_conflicts.log"