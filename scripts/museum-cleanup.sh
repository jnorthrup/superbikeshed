#!/bin/bash
set -euo pipefail

# Museum Review Cleanup Script
# Manages cleanup of museum review branches and artifacts

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_ROOT}"

# Function to log with timestamp
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

# Function to list museum branches
list_museum_branches() {
    git branch | grep "museum-review-" | sed 's/^[* ] //' || true
}

# Function to cleanup old museum branches
cleanup_old_branches() {
    local days_old="${1:-7}"  # Default 7 days
    
    log "Cleaning up museum branches older than ${days_old} days..."
    
    local cutoff_date=$(date -d "${days_old} days ago" +%Y%m%d 2>/dev/null || date -v-${days_old}d +%Y%m%d)
    
    list_museum_branches | while read -r branch; do
        if [[ -n "${branch}" ]]; then
            # Extract timestamp from branch name (format: museum-review-keyword-YYYYMMDD_HHMMSS)
            if [[ "${branch}" =~ museum-review-.*-([0-9]{8})_[0-9]{6} ]]; then
                local branch_date="${BASH_REMATCH[1]}"
                
                if [[ "${branch_date}" < "${cutoff_date}" ]]; then
                    log "Deleting old branch: ${branch} (${branch_date})"
                    git branch -D "${branch}" || log "Failed to delete ${branch}"
                else
                    log "Keeping recent branch: ${branch} (${branch_date})"
                fi
            else
                log "Skipping branch with unexpected format: ${branch}"
            fi
        fi
    done
}

# Function to cleanup tmpdir artifacts
cleanup_tmpdir() {
    local days_old="${1:-1}"  # Default 1 day for tmpdir
    
    log "Cleaning up tmpdir artifacts older than ${days_old} days..."
    
    if [[ -d "tmpdir" ]]; then
        find tmpdir -name "museum-review-*" -type d -mtime +${days_old} -exec rm -rf {} + 2>/dev/null || true
    fi
}

# Function to show museum branch summary
show_summary() {
    log "=== Museum Branch Summary ==="
    
    local branches=($(list_museum_branches))
    
    if [[ ${#branches[@]} -eq 0 ]]; then
        log "No museum review branches found"
        return
    fi
    
    log "Found ${#branches[@]} museum review branches:"
    
    for branch in "${branches[@]}"; do
        if [[ -n "${branch}" ]]; then
            # Get branch info
            local last_commit=$(git log -1 --format="%h %s" "${branch}" 2>/dev/null || echo "N/A")
            log "  - ${branch}"
            log "    Last commit: ${last_commit}"
        fi
    done
    
    # Show recent transcripts
    log "\n=== Recent Transcripts ==="
    if [[ -d "museum" ]]; then
        find museum -name "transcript-*.md" -mtime -7 | sort | while read -r transcript; do
            if [[ -n "${transcript}" ]]; then
                local size=$(wc -l < "${transcript}" 2>/dev/null || echo "0")
                log "  - ${transcript} (${size} lines)"
            fi
        done
    fi
}

# Function to merge successful museum branches
merge_successful_branches() {
    local target_branch="${1:-main}"
    
    log "Reviewing museum branches for potential merge to ${target_branch}..."
    
    list_museum_branches | while read -r branch; do
        if [[ -n "${branch}" ]]; then
            log "Checking branch: ${branch}"
            
            # Check if branch has commits
            local commit_count=$(git rev-list --count "${target_branch}..${branch}" 2>/dev/null || echo "0")
            
            if [[ "${commit_count}" -gt 0 ]]; then
                log "  - Has ${commit_count} new commits"
                
                # Check if it compiles
                git checkout "${branch}" >/dev/null 2>&1
                
                if ./gradlew compileCommonMainKotlinMetadata --console=plain --no-daemon >/dev/null 2>&1; then
                    log "  - Compiles successfully ✓"
                    log "  - Branch ${branch} is ready for manual review/merge"
                else
                    log "  - Compilation failed ✗"
                    log "  - Branch ${branch} needs fixes before merge"
                fi
            else
                log "  - No new commits"
            fi
        fi
    done
    
    # Return to main
    git checkout "${target_branch}" >/dev/null 2>&1
}

# Main command processing
case "${1:-summary}" in
    "cleanup")
        cleanup_old_branches "${2:-7}"
        cleanup_tmpdir "${3:-1}"
        ;;
    "summary")
        show_summary
        ;;
    "merge-check")
        merge_successful_branches "${2:-main}"
        ;;
    "help")
        echo "Museum cleanup script"
        echo "Usage: $0 [command] [options]"
        echo ""
        echo "Commands:"
        echo "  summary              Show museum branch summary (default)"
        echo "  cleanup [days]       Cleanup branches older than [days] (default: 7)"
        echo "  merge-check [target] Check branches ready for merge to [target] (default: main)"
        echo "  help                 Show this help"
        ;;
    *)
        echo "Unknown command: $1"
        echo "Use '$0 help' for usage information"
        exit 1
        ;;
esac

log "Museum cleanup completed"