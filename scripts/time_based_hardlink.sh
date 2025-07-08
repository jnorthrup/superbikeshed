#!/bin/bash

# Time-Based Hard Link Strategy
# 1 full copy per hour, then hard link to that copy

set -e

REPO_SOURCE="/Users/jim/work/v2superbikeshed"
BACKUP_ROOT="/tmp/repo-backups"
CLONE_ROOT="/tmp/repo-clones"

# Protocol endpoints
HOURLY_COPY_DIR="$BACKUP_ROOT/hourly"
CLONE_DIR="$CLONE_ROOT/clones"
LOCK_FILE="$BACKUP_ROOT/lock"

# Ensure directories exist
mkdir -p "$HOURLY_COPY_DIR" "$CLONE_DIR"

# Get current hour timestamp
get_hour_timestamp() {
    date +%Y%m%d-%H
}

# Get current hour directory
get_current_hour_dir() {
    echo "$HOURLY_COPY_DIR/$(get_hour_timestamp)"
}

# Create hourly full copy (if not exists)
create_hourly_copy() {
    local hour_dir=$(get_current_hour_dir)
    
    if [[ -d "$hour_dir" ]]; then
        echo "Hourly copy exists: $hour_dir"
        return 0
    fi
    
    echo "Creating hourly copy: $hour_dir"
    cp -a "$REPO_SOURCE" "$hour_dir"
    echo "Hourly copy created: $hour_dir"
}

# Create hard link clone
create_hardlink_clone() {
    local clone_name="$1"
    local hour_dir=$(get_current_hour_dir)
    local clone_path="$CLONE_DIR/$clone_name"
    
    if [[ ! -d "$hour_dir" ]]; then
        echo "Error: No hourly copy found at $hour_dir"
        exit 1
    fi
    
    if [[ -d "$clone_path" ]]; then
        echo "Removing existing clone: $clone_path"
        rm -rf "$clone_path"
    fi
    
    echo "Creating hard link clone: $clone_path -> $hour_dir"
    cp -lf "$hour_dir" "$clone_path"
    echo "Clone created: $clone_path"
}

# List available clones
list_clones() {
    echo "Available clones:"
    ls -la "$CLONE_DIR" 2>/dev/null || echo "No clones found"
}

# Clean old hourly copies (keep last 24 hours)
cleanup_old_copies() {
    echo "Cleaning up old hourly copies..."
    find "$HOURLY_COPY_DIR" -maxdepth 1 -type d -name "*-*" -mtime +1 -exec rm -rf {} \;
}

# Main protocol endpoints
case "${1:-help}" in
    "hourly-copy")
        create_hourly_copy
        ;;
    "clone")
        if [[ -z "$2" ]]; then
            echo "Usage: $0 clone <clone-name>"
            exit 1
        fi
        create_hourly_copy
        create_hardlink_clone "$2"
        ;;
    "list")
        list_clones
        ;;
    "cleanup")
        cleanup_old_copies
        ;;
    "status")
        echo "Current hour: $(get_hour_timestamp)"
        echo "Hourly copy: $(get_current_hour_dir)"
        echo "Clone count: $(ls -1 "$CLONE_DIR" 2>/dev/null | wc -l)"
        ;;
    *)
        echo "Time-Based Hard Link Protocol"
        echo "Usage: $0 <command> [args]"
        echo ""
        echo "Commands:"
        echo "  hourly-copy              Create full copy for current hour"
        echo "  clone <name>             Create hard link clone"
        echo "  list                     List available clones"
        echo "  cleanup                  Remove old hourly copies"
        echo "  status                   Show current status"
        echo ""
        echo "Example:"
        echo "  $0 clone my-feature      # Create clone 'my-feature'"
        echo "  $0 list                  # List all clones"
        ;;
esac 