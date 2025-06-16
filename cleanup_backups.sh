#!/bin/bash

# Script to diff files with their ~ backups and remove backups with no delta
# Usage: ./cleanup_backups.sh [directory]

set -e

TARGET_DIR="${1:-.}"

if [[ ! -d "$TARGET_DIR" ]]; then
    echo "Error: Directory '$TARGET_DIR' does not exist"
    exit 1
fi

echo "Scanning for ~ backup files in: $TARGET_DIR"

# Find all ~ backup files
find "$TARGET_DIR" -name "*~" -type f | while read -r backup_file; do
    original_file="${backup_file%~}"
    
    if [[ -f "$original_file" ]]; then
        echo "Comparing $original_file with $backup_file"
        
        # Use diff to check if files are identical (returns 0 if same, 1 if different)
        if diff -q "$original_file" "$backup_file" >/dev/null 2>&1; then
            echo "  No changes - removing backup: $backup_file"
            rm "$backup_file"
        else
            echo "  Changes detected - showing unified diff:"
            echo "  --- Original: $original_file"
            echo "  +++ Backup:   $backup_file"
            diff -u "$original_file" "$backup_file" || true
            echo "  Keeping backup: $backup_file"
        fi
    else
        echo "Original file not found for backup: $backup_file"
        echo "  Keeping orphaned backup: $backup_file"
    fi
    
    echo ""
done

echo "Backup cleanup complete"