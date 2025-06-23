#!/bin/bash

# Git Taxonomical Analysis Script
# Integrates with TrikeShed git taxonomical feature knowledge system

set -euo pipefail

# Configuration
OUTPUT_DIR="${OUTPUT_DIR:-output}"
ANALYSIS_FILE="${OUTPUT_DIR}/git_taxonomical_analysis.json"

# Create directories
mkdir -p "${OUTPUT_DIR}"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Extract commit taxonomical features
extract_commit_features() {
    local commit_hash="$1"
    
    # Get commit details
    local commit_info=$(git show --format="%H|%an|%cn|%s|%at" --numstat "$commit_hash" --no-patch 2>/dev/null)
    
    if [ -z "$commit_info" ]; then
        return 1
    fi
    
    # Parse commit information
    local commit_line=$(echo "$commit_info" | head -n 1)
    local parts=($(echo "$commit_line" | tr '|' ' '))
    
    local hash="${parts[0]}"
    local author="${parts[1]}"
    local message="${parts[3]}"
    local timestamp="${parts[4]}"
    
    # Classify commit type
    local commit_type="unknown"
    local message_lower=$(echo "$message" | tr '[:upper:]' '[:lower:]')
    
    if [[ "$message_lower" =~ (feat|feature) ]]; then
        commit_type="feature"
    elif [[ "$message_lower" =~ (fix|bug) ]]; then
        commit_type="bugfix"
    elif [[ "$message_lower" =~ refactor ]]; then
        commit_type="refactor"
    elif [[ "$message_lower" =~ test ]]; then
        commit_type="test"
    elif [[ "$message_lower" =~ doc ]]; then
        commit_type="documentation"
    fi
    
    # Count file changes
    local file_changes=$(echo "$commit_info" | tail -n +2 | grep -v '^$')
    local total_files=$(echo "$file_changes" | wc -l)
    
    # Assess impact level
    local impact_level="minimal"
    if [ $total_files -gt 20 ]; then
        impact_level="high"
    elif [ $total_files -gt 5 ]; then
        impact_level="medium"
    elif [ $total_files -gt 1 ]; then
        impact_level="low"
    fi
    
    # Create feature object
    cat <<EOF
{
    "hash": "$hash",
    "author": "$author",
    "message": "$message",
    "timestamp": $timestamp,
    "commit_type": "$commit_type",
    "impact_level": "$impact_level",
    "file_count": $total_files,
    "message_length": ${#message}
}
EOF
}

# Main analysis
main() {
    log "Starting git taxonomical analysis"
    
    # Check if we're in a git repository
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        log "Error: Not in a git repository"
        exit 1
    fi
    
    # Get all commits
    local commits=$(git log --format="%H" --all 2>/dev/null)
    local commit_count=$(echo "$commits" | wc -l | tr -d ' ')
    
    log "Found $commit_count commits to analyze"
    
    # Analyze commits
    local analyzed_commits="[]"
    local processed=0
    
    while IFS= read -r commit_hash; do
        if [ -n "$commit_hash" ]; then
            local commit_features=$(extract_commit_features "$commit_hash" 2>/dev/null || echo "{}")
            
            if [ "$commit_features" != "{}" ]; then
                analyzed_commits=$(echo "$analyzed_commits" | jq --argjson features "$commit_features" '. += [$features]' 2>/dev/null || echo "$analyzed_commits")
                processed=$((processed + 1))
                
                if [ $((processed % 100)) -eq 0 ]; then
                    log "Processed $processed commits"
                fi
            fi
        fi
    done <<< "$commits"
    
    # Create analysis result
    local analysis=$(cat <<EOF
{
    "repository": "$(basename $(git rev-parse --show-toplevel))",
    "total_commits": $commit_count,
    "analyzed_commits": $processed,
    "commits": $analyzed_commits,
    "analysis_timestamp": "$(date -Iseconds)"
}
EOF
)
    
    # Save analysis
    echo "$analysis" > "$ANALYSIS_FILE"
    log "Analysis saved to $ANALYSIS_FILE"
    
    # Display summary
    log "Analysis complete"
    log "Repository: $(basename $(git rev-parse --show-toplevel))"
    log "Total commits analyzed: $processed"
}

# Run main function
main "$@" 