#!/bin/bash

# Documentation Consolidation FSM with Phases
# State: IDLE -> PROCESSING -> SUCCESS/FAILURE -> IDLE
# Phases: SUMMARIZE -> CONSOLIDATE -> CLEAN -> REUP -> FINALIZE

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# FSM States
IDLE="IDLE"
PROCESSING="PROCESSING"
SUCCESS="SUCCESS"
FAILURE="FAILURE"

# Phases
PHASE_SUMMARIZE="SUMMARIZE"
PHASE_CONSOLIDATE="CONSOLIDATE"
PHASE_CLEAN="CLEAN"
PHASE_REUP="REUP"
PHASE_FINALIZE="FINALIZE"

current_state="$IDLE"
current_phase="$PHASE_SUMMARIZE"

log() {
    echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $1"
}

phase_log() {
    echo -e "${PURPLE}[PHASE: $current_phase]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    current_state="$FAILURE"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
    current_state="$SUCCESS"
}

transition() {
    local new_state="$1"
    local message="$2"
    log "State transition: $current_state -> $new_state: $message"
    current_state="$new_state"
}

phase_transition() {
    local new_phase="$1"
    local message="$2"
    phase_log "Phase transition: $current_phase -> $new_phase: $message"
    current_phase="$new_phase"
}

# Find all gradle projects
find_gradle_projects() {
    find . -name "build.gradle.kts" -o -name "build.gradle" | grep -v build/ | sed 's|/[^/]*$||' | sort -u
}

# Phase 1: Summarize project documentation
summarize_project_docs() {
    local project_dir="$1"
    local summary_file="$project_dir/DOCS_SUMMARY.md"
    
    phase_log "Summarizing documentation for $project_dir"
    
    # Find all markdown files
    local md_files=$(find "$project_dir" -name "*.md" -type f 2>/dev/null | sort)
    
    if [ -z "$md_files" ]; then
        log "No markdown files found in $project_dir"
        return 0
    fi
    
    # Create comprehensive summary
    cat > "$summary_file" << EOF
# Documentation Summary: $(basename "$project_dir")

## Overview
Generated on: $(date)
Total markdown files: $(echo "$md_files" | wc -l)
Project type: Gradle
Consolidation phase: $current_phase

## Files Found
EOF
    
    echo "$md_files" | while read -r file; do
        local size=$(wc -c < "$file" 2>/dev/null || echo "0")
        local lines=$(wc -l < "$file" 2>/dev/null || echo "0")
        local relative_path=$(realpath --relative-to="$project_dir" "$file")
        echo "- \`$relative_path\` ($size bytes, $lines lines)" >> "$summary_file"
    done
    
    # Add content analysis
    echo "" >> "$summary_file"
    echo "## Content Analysis" >> "$summary_file"
    echo "### Large Files (>10KB):" >> "$summary_file"
    echo "$md_files" | while read -r file; do
        local size=$(wc -c < "$file" 2>/dev/null || echo "0")
        if [ "$size" -gt 10240 ]; then
            local relative_path=$(realpath --relative-to="$project_dir" "$file")
            echo "- \`$relative_path\` ($size bytes)" >> "$summary_file"
        fi
    done
    
    echo "" >> "$summary_file"
    echo "### Potential Duplicates:" >> "$summary_file"
    echo "$md_files" | while read -r file1; do
        echo "$md_files" | while read -r file2; do
            if [ "$file1" != "$file2" ] && [ -f "$file1" ] && [ -f "$file2" ]; then
                local hash1=$(md5sum "$file1" 2>/dev/null | cut -d' ' -f1 || echo "")
                local hash2=$(md5sum "$file2" 2>/dev/null | cut -d' ' -f1 || echo "")
                if [ "$hash1" = "$hash2" ] && [ -n "$hash1" ]; then
                    local rel1=$(realpath --relative-to="$project_dir" "$file1")
                    local rel2=$(realpath --relative-to="$project_dir" "$file2")
                    echo "- \`$rel1\` and \`$rel2\` (identical content)" >> "$summary_file"
                fi
            fi
        done
    done
    
    echo "" >> "$summary_file"
    echo "## Consolidation Status" >> "$summary_file"
    echo "- [x] Summarized" >> "$summary_file"
    echo "- [ ] Consolidated" >> "$summary_file"
    echo "- [ ] Cleaned" >> "$summary_file"
    echo "- [ ] Re-upped" >> "$summary_file"
    echo "- [ ] Finalized" >> "$summary_file"
    
    success "Created comprehensive summary for $project_dir"
}

# Phase 2: Consolidate project documentation
consolidate_project_docs() {
    local project_dir="$1"
    
    phase_log "Consolidating documentation for $project_dir"
    
    # Create docs directory if it doesn't exist
    mkdir -p "$project_dir/docs"
    
    # Move root-level markdown files to docs/
    find "$project_dir" -maxdepth 1 -name "*.md" ! -name "README.md" ! -name "TODO.md" ! -name "DOCS_SUMMARY.md" | while read -r file; do
        local basename_file=$(basename "$file")
        if [ ! -f "$project_dir/docs/$basename_file" ]; then
            mv "$file" "$project_dir/docs/"
            log "Moved $basename_file to docs/"
        else
            log "Skipped $basename_file (already exists in docs/)"
        fi
    done
    
    # Create consolidated README if multiple files exist
    local docs_files=$(find "$project_dir/docs" -name "*.md" 2>/dev/null | wc -l)
    if [ "$docs_files" -gt 1 ]; then
        cat > "$project_dir/docs/README.md" << EOF
# $(basename "$project_dir") Documentation

## Overview
This directory contains consolidated documentation for the $(basename "$project_dir") project.
Consolidated on: $(date)

## Files
EOF
        find "$project_dir/docs" -name "*.md" ! -name "README.md" | while read -r file; do
            local size=$(wc -c < "$file" 2>/dev/null || echo "0")
            local lines=$(wc -l < "$file" 2>/dev/null || echo "0")
            echo "- [$(basename "$file" .md)]($(basename "$file")) ($size bytes, $lines lines)" >> "$project_dir/docs/README.md"
        done
        
        success "Created consolidated docs README for $project_dir"
    fi
    
    # Update summary status
    sed -i 's/- \[ \] Consolidated/- [x] Consolidated/' "$project_dir/DOCS_SUMMARY.md" 2>/dev/null || true
}

# Phase 3: Clean project documentation
clean_project_docs() {
    local project_dir="$1"
    
    phase_log "Cleaning documentation for $project_dir"
    
    # Remove empty files
    find "$project_dir/docs" -name "*.md" -size 0 -delete 2>/dev/null || true
    
    # Remove duplicate content (basic check)
    local docs_dir="$project_dir/docs"
    if [ -d "$docs_dir" ]; then
        # Find files with similar content (simple hash-based check)
        find "$docs_dir" -name "*.md" | while read -r file1; do
            find "$docs_dir" -name "*.md" | while read -r file2; do
                if [ "$file1" != "$file2" ] && [ -f "$file1" ] && [ -f "$file2" ]; then
                    local hash1=$(md5sum "$file1" 2>/dev/null | cut -d' ' -f1 || echo "")
                    local hash2=$(md5sum "$file2" 2>/dev/null | cut -d' ' -f1 || echo "")
                    if [ "$hash1" = "$hash2" ] && [ -n "$hash1" ]; then
                        log "Removing duplicate: $file2 (same as $file1)"
                        rm "$file2"
                    fi
                fi
            done
        done
    fi
    
    # Update summary status
    sed -i 's/- \[ \] Cleaned/- [x] Cleaned/' "$project_dir/DOCS_SUMMARY.md" 2>/dev/null || true
    
    success "Cleaned documentation for $project_dir"
}

# Phase 4: Re-up the material
reup_project_docs() {
    local project_dir="$1"
    
    phase_log "Re-upping material for $project_dir"
    
    # Create re-up summary
    local reup_file="$project_dir/DOCS_REUP.md"
    cat > "$reup_file" << EOF
# Documentation Re-up: $(basename "$project_dir")

## Re-up Summary
Generated on: $(date)
Phase: $current_phase

## Current State
EOF
    
    # Count files in different locations
    local root_files=$(find "$project_dir" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l)
    local docs_files=$(find "$project_dir/docs" -name "*.md" 2>/dev/null | wc -l)
    local total_files=$((root_files + docs_files))
    
    echo "- Root level files: $root_files" >> "$reup_file"
    echo "- Docs directory files: $docs_files" >> "$reup_file"
    echo "- Total documentation files: $total_files" >> "$reup_file"
    
    echo "" >> "$reup_file"
    echo "## File Inventory" >> "$reup_file"
    
    # List all files with sizes
    echo "### Root Level:" >> "$reup_file"
    find "$project_dir" -maxdepth 1 -name "*.md" 2>/dev/null | while read -r file; do
        local size=$(wc -c < "$file" 2>/dev/null || echo "0")
        local lines=$(wc -l < "$file" 2>/dev/null || echo "0")
        echo "- \`$(basename "$file")\` ($size bytes, $lines lines)" >> "$reup_file"
    done
    
    if [ -d "$project_dir/docs" ]; then
        echo "" >> "$reup_file"
        echo "### Docs Directory:" >> "$reup_file"
        find "$project_dir/docs" -name "*.md" 2>/dev/null | while read -r file; do
            local size=$(wc -c < "$file" 2>/dev/null || echo "0")
            local lines=$(wc -l < "$file" 2>/dev/null || echo "0")
            local relative_path=$(realpath --relative-to="$project_dir" "$file")
            echo "- \`$relative_path\` ($size bytes, $lines lines)" >> "$reup_file"
        done
    fi
    
    echo "" >> "$reup_file"
    echo "## Next Steps" >> "$reup_file"
    echo "1. Review consolidated documentation" >> "$reup_file"
    echo "2. Update cross-references" >> "$reup_file"
    echo "3. Remove redundant content" >> "$reup_file"
    echo "4. Finalize documentation structure" >> "$reup_file"
    
    # Update summary status
    sed -i 's/- \[ \] Re-upped/- [x] Re-upped/' "$project_dir/DOCS_SUMMARY.md" 2>/dev/null || true
    
    success "Re-upped material for $project_dir"
}

# Phase 5: Finalize project documentation
finalize_project_docs() {
    local project_dir="$1"
    
    phase_log "Finalizing documentation for $project_dir"
    
    # Create final status report
    local final_file="$project_dir/DOCS_FINAL.md"
    cat > "$final_file" << EOF
# Documentation Finalization: $(basename "$project_dir")

## Final Status
Completed on: $(date)
All phases completed successfully

## Summary
- [x] Summarized: Created comprehensive documentation inventory
- [x] Consolidated: Moved files to docs/ directory
- [x] Cleaned: Removed duplicates and empty files
- [x] Re-upped: Generated current state report
- [x] Finalized: Documentation consolidation complete

## Final File Count
EOF
    
    local root_files=$(find "$project_dir" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l)
    local docs_files=$(find "$project_dir/docs" -name "*.md" 2>/dev/null | wc -l)
    local total_files=$((root_files + docs_files))
    
    echo "- Root level: $root_files files" >> "$final_file"
    echo "- Docs directory: $docs_files files" >> "$final_file"
    echo "- Total: $total_files files" >> "$final_file"
    
    echo "" >> "$final_file"
    echo "## Documentation Structure" >> "$final_file"
    echo "\`\`\`" >> "$final_file"
    tree "$project_dir" -I 'build|.gradle|.idea|*.class|*.jar' --dirsfirst 2>/dev/null || find "$project_dir" -type f -name "*.md" | head -20 >> "$final_file"
    echo "\`\`\`" >> "$final_file"
    
    # Update summary status
    sed -i 's/- \[ \] Finalized/- [x] Finalized/' "$project_dir/DOCS_SUMMARY.md" 2>/dev/null || true
    
    success "Finalized documentation for $project_dir"
}

# Main FSM loop with phases
main() {
    log "Starting Documentation Consolidation FSM with Phases"
    transition "$IDLE" "Initialized"
    
    # Find all gradle projects
    local projects=$(find_gradle_projects)
    local total_projects=$(echo "$projects" | wc -l)
    local processed=0
    
    log "Found $total_projects gradle projects"
    
    echo "$projects" | while read -r project; do
        if [ -z "$project" ]; then
            continue
        fi
        
        log "Processing project: $project"
        transition "$PROCESSING" "Processing $project"
        
        # Phase 1: Summarize
        phase_transition "$PHASE_SUMMARIZE" "Starting summarization"
        if summarize_project_docs "$project"; then
            
            # Phase 2: Consolidate
            phase_transition "$PHASE_CONSOLIDATE" "Starting consolidation"
            if consolidate_project_docs "$project"; then
                
                # Phase 3: Clean
                phase_transition "$PHASE_CLEAN" "Starting cleanup"
                if clean_project_docs "$project"; then
                    
                    # Phase 4: Re-up
                    phase_transition "$PHASE_REUP" "Starting re-up"
                    if reup_project_docs "$project"; then
                        
                        # Phase 5: Finalize
                        phase_transition "$PHASE_FINALIZE" "Starting finalization"
                        if finalize_project_docs "$project"; then
                            success "Completed all phases for $project"
                            processed=$((processed + 1))
                        else
                            error "Failed to finalize $project"
                        fi
                    else
                        error "Failed to re-up $project"
                    fi
                else
                    error "Failed to clean $project"
                fi
            else
                error "Failed to consolidate $project"
            fi
        else
            error "Failed to summarize $project"
        fi
        
        transition "$IDLE" "Ready for next project"
    done
    
    log "FSM completed. Processed $processed/$total_projects projects"
    
    # Final summary
    echo ""
    echo "=== DOCUMENTATION CONSOLIDATION SUMMARY ==="
    echo "Total projects found: $total_projects"
    echo "Successfully processed: $processed"
    echo "Failed: $((total_projects - processed))"
    echo ""
    echo "Generated files per project:"
    echo "- DOCS_SUMMARY.md: Comprehensive inventory"
    echo "- DOCS_REUP.md: Current state report"
    echo "- DOCS_FINAL.md: Final status report"
    echo "- docs/README.md: Consolidated documentation index"
    echo ""
    echo "Next steps:"
    echo "1. Review DOCS_FINAL.md files in each project"
    echo "2. Manually consolidate overlapping content across projects"
    echo "3. Update cross-references and links"
    echo "4. Remove redundant files"
    echo "5. Create master documentation index"
}

# Run main function
main "$@" 