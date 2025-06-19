#!/bin/bash

# Configuration
LOGS_DIR="${LOGS_DIR:-logs}"
OUTPUT_DIR="${OUTPUT_DIR:-output}"
METRICS_FILE="${OUTPUT_DIR}/metrics/git_delta_metrics.json"
GOLDEN_TAG="golden_age"

# Create necessary directories
mkdir -p "${LOGS_DIR}/git_metrics"
mkdir -p "${OUTPUT_DIR}/metrics"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Function to get all branches
get_branches() {
    git branch -a | grep -v "HEAD" | sed 's/^\s*//' | sed 's/^remotes\/origin\///'
}

# Function to get delta metrics between two points
get_delta_metrics() {
    local base_point="$1"
    local target_point="$2"
    
    log "Analyzing delta between $base_point and $target_point"
    
    # Get commit count
    local commit_count=$(git rev-list --count "${base_point}..${target_point}" 2>/dev/null)
    
    # Get file changes
    local file_changes=$(git diff --name-status "${base_point}..${target_point}" 2>/dev/null)
    
    # Count different types of changes
    local added_files=$(echo "$file_changes" | grep -c "^A" || echo "0")
    local modified_files=$(echo "$file_changes" | grep -c "^M" || echo "0")
    local deleted_files=$(echo "$file_changes" | grep -c "^D" || echo "0")
    
    # Get total lines changed
    local total_lines=$(git diff --stat "${base_point}..${target_point}" 2>/dev/null | tail -n 1 | awk '{print $4}' || echo "0")
    
    # Get commit messages
    local commit_messages=$(git log --pretty=format:"%h - %s" "${base_point}..${target_point}" 2>/dev/null)
    
    # Create metrics object
    local metrics=$(cat <<EOF
{
    "timestamp": "$(date '+%Y-%m-%d %H:%M:%S')",
    "base_point": "$base_point",
    "target_point": "$target_point",
    "commit_count": $commit_count,
    "file_changes": {
        "added": $added_files,
        "modified": $modified_files,
        "deleted": $deleted_files,
        "total": $((added_files + modified_files + deleted_files))
    },
    "lines_changed": $total_lines,
    "commit_messages": $(echo "$commit_messages" | jq -R -s 'split("\n")[:-1]')
}
EOF
)
    
    echo "$metrics"
}

# Function to update metrics file
update_metrics() {
    local metrics="$1"
    
    # Create or update metrics file
    if [ ! -f "$METRICS_FILE" ]; then
        echo "{\"deltas\":[]}" > "$METRICS_FILE"
    fi
    
    # Update metrics using jq
    local temp_file=$(mktemp)
    if ! jq --argjson metrics "$metrics" '.deltas += [$metrics]' "$METRICS_FILE" > "$temp_file" 2>/dev/null; then
        log "Error updating metrics"
        rm "$temp_file"
        return 1
    fi
    mv "$temp_file" "$METRICS_FILE"
}

# Function to calculate and display stats
calculate_stats() {
    if [ ! -f "$METRICS_FILE" ]; then
        echo "No metrics available"
        return
    fi
    
    # Calculate stats using jq
    local stats=$(jq '
        .deltas | 
        if length > 0 then
            {
                "total_branches": length,
                "avg_commits": (map(.commit_count) | add) / length,
                "avg_files_changed": (map(.file_changes.total) | add) / length,
                "avg_lines_changed": (map(.lines_changed) | add) / length,
                "total_files_changed": (map(.file_changes.total) | add),
                "total_lines_changed": (map(.lines_changed) | add)
            }
        else
            {"error": "No data available"}
        end
    ' "$METRICS_FILE" 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        echo "$stats"
    else
        echo "{\"error\": \"Failed to calculate stats\"}"
    fi
}

# Main execution
log "Starting git delta analysis from golden tag: $GOLDEN_TAG"

# Verify golden tag exists
if ! git show-ref --verify --quiet "refs/tags/${GOLDEN_TAG}"; then
    log "Error: Golden tag '${GOLDEN_TAG}' not found"
    exit 1
fi

# Get all branches
BRANCHES=($(get_branches))

# Analyze each branch
for branch in "${BRANCHES[@]}"; do
    log "Analyzing branch: $branch"
    metrics=$(get_delta_metrics "$GOLDEN_TAG" "$branch")
    if [ $? -eq 0 ]; then
        update_metrics "$metrics"
        log "Metrics for $branch:"
        echo "$metrics" | jq .
    else
        log "Error analyzing branch: $branch"
    fi
done

# Display final summary
echo -e "\nFinal Summary:"
calculate_stats 