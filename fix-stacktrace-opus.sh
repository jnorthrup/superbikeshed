#!/bin/bash
set -euo pipefail

# Fix Stacktrace with Opus - Grab 10 'e:' stacktraces and run virtuous cycle
# Implements the most valuable immediate workflow for Project Armor

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# Files
BUILD_LOG="${PROJECT_ROOT}/build-stacktrace.log"
EXTRACTED_ERRORS="${PROJECT_ROOT}/extracted-errors.log"
OPUS_OUTPUT="${PROJECT_ROOT}/opus-fixes.log"
CYCLE_LOG="${PROJECT_ROOT}/virtuous-cycle.log"

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$CYCLE_LOG"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$CYCLE_LOG"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$CYCLE_LOG"
}

log_cycle() {
    echo -e "${PURPLE}[CYCLE]${NC} $1" | tee -a "$CYCLE_LOG"
}

# Initialize cycle log
echo "=== Fix Stacktrace Opus Virtuous Cycle ===" > "$CYCLE_LOG"
echo "Started: $(date)" >> "$CYCLE_LOG"
echo "Project: $PROJECT_ROOT" >> "$CYCLE_LOG"
echo "=========================================" >> "$CYCLE_LOG"

# Extract 10 'e:' stacktraces from gradle output
extract_stacktraces() {
    log_info "Extracting 10 'e:' stacktraces from gradle output..."
    
    # Run gradle build and capture output
    log_info "Running gradle build to capture stacktraces..."
    if ./gradlew build --stacktrace > "$BUILD_LOG" 2>&1; then
        log_success "Build passed - no stacktraces to fix"
        return 1
    else
        log_info "Build failed - extracting stacktraces"
    fi
    
    # Extract 'e:' lines (compilation errors)
    log_info "Extracting 'e:' compilation errors..."
    grep -n "^e: file://" "$BUILD_LOG" | head -10 > "$EXTRACTED_ERRORS" || {
        log_error "No 'e:' stacktraces found in build output"
        return 1
    }
    
    local error_count=$(wc -l < "$EXTRACTED_ERRORS")
    log_success "Extracted $error_count compilation errors"
    
    # Show preview
    echo -e "\n${YELLOW}Top 10 Compilation Errors:${NC}"
    cat "$EXTRACTED_ERRORS" | sed 's/^/  /'
    echo
    
    return 0
}

# Process stacktraces with Opus-optimal format
process_with_opus() {
    log_info "Processing stacktraces with Opus-optimal format..."
    
    # Use super-build-policy.sh with opus mode
    log_info "Running super-build-policy.sh post-error with --opus"
    if ./scripts/super-build-policy.sh post-error -f "$EXTRACTED_ERRORS" --opus > "$OPUS_OUTPUT" 2>&1; then
        log_success "Opus processing completed"
    else
        log_error "Opus processing failed"
        cat "$OPUS_OUTPUT"
        return 1
    fi
    
    # Show opus output preview
    echo -e "\n${PURPLE}Opus Output Preview:${NC}"
    head -20 "$OPUS_OUTPUT" | sed 's/^/  /'
    echo
}

# Apply Project Armor to bugfix+stacktrace files intersection
apply_armor_intersection() {
    log_info "Applying Project Armor to bugfix+stacktrace files intersection..."
    
    # Get dirty files from git
    local dirty_files
    dirty_files=$(git status --porcelain | grep "^[M ]" | cut -c4- | grep "\.kt$" || true)
    
    if [[ -z "$dirty_files" ]]; then
        log_info "No dirty Kotlin files found"
        return 0
    fi
    
    log_info "Found dirty Kotlin files:"
    echo "$dirty_files" | sed 's/^/  /'
    
    # Extract file paths from stacktraces
    local stacktrace_files
    stacktrace_files=$(grep -o "file://[^:]*" "$EXTRACTED_ERRORS" | sed 's|file://||' | sort -u || true)
    
    if [[ -z "$stacktrace_files" ]]; then
        log_error "No files found in stacktraces"
        return 1
    fi
    
    log_info "Found stacktrace files:"
    echo "$stacktrace_files" | sed 's/^/  /'
    
    # Find intersection
    local intersection_files=""
    while IFS= read -r dirty_file; do
        while IFS= read -r stacktrace_file; do
            if [[ "$stacktrace_file" == *"$dirty_file"* ]] || [[ "$dirty_file" == *"$(basename "$stacktrace_file")"* ]]; then
                intersection_files="$intersection_files$dirty_file"$'\n'
                break
            fi
        done <<< "$stacktrace_files"
    done <<< "$dirty_files"
    
    if [[ -z "$intersection_files" ]]; then
        log_info "No intersection between dirty and stacktrace files"
        return 0
    fi
    
    log_success "Intersection files to armor:"
    echo "$intersection_files" | sed 's/^/  /'
    
    # Apply armor to intersection files
    echo "$intersection_files" | while IFS= read -r file; do
        if [[ -n "$file" && -f "$file" ]]; then
            log_info "Applying armor to: $file"
            # Use ProjectArmorStacktraceFixer to apply armor
            ./gradlew applyProjectArmorToFile -Pfile="$file" || {
                log_error "Failed to apply armor to $file"
            }
        fi
    done
    
    log_success "Armor applied to intersection files"
}

# Run virtuous cycle
run_virtuous_cycle() {
    local cycle_count=0
    local max_cycles=5
    
    log_cycle "Starting virtuous cycle (max $max_cycles cycles)"
    
    while [[ $cycle_count -lt $max_cycles ]]; do
        cycle_count=$((cycle_count + 1))
        log_cycle "=== CYCLE $cycle_count/$max_cycles ==="
        
        # Step 1: Extract stacktraces
        if ! extract_stacktraces; then
            log_success "Build passed - virtuous cycle complete!"
            break
        fi
        
        # Step 2: Process with Opus
        if ! process_with_opus; then
            log_error "Opus processing failed in cycle $cycle_count"
            break
        fi
        
        # Step 3: Apply armor to intersection
        if ! apply_armor_intersection; then
            log_error "Armor application failed in cycle $cycle_count"
            break
        fi
        
        # Step 4: Quick build check
        log_cycle "Testing build after cycle $cycle_count..."
        if ./gradlew compileKotlinJvm > /dev/null 2>&1; then
            log_success "Build passed after cycle $cycle_count!"
            break
        else
            log_cycle "Build still failing, continuing to next cycle..."
        fi
        
        # Brief pause between cycles
        sleep 1
    done
    
    if [[ $cycle_count -eq $max_cycles ]]; then
        log_error "Maximum cycles reached without build success"
        return 1
    fi
    
    log_success "Virtuous cycle completed in $cycle_count cycles"
}

# Generate summary report
generate_summary() {
    log_info "Generating summary report..."
    
    echo -e "\n${CYAN}=== VIRTUOUS CYCLE SUMMARY ===${NC}"
    echo -e "${YELLOW}Cycle Log:${NC} $CYCLE_LOG"
    echo -e "${YELLOW}Build Log:${NC} $BUILD_LOG"
    echo -e "${YELLOW}Extracted Errors:${NC} $EXTRACTED_ERRORS"
    echo -e "${YELLOW}Opus Output:${NC} $OPUS_OUTPUT"
    echo
    
    # Check final build status
    if ./gradlew compileKotlinJvm > /dev/null 2>&1; then
        echo -e "${GREEN}✅ FINAL BUILD STATUS: PASSING${NC}"
    else
        echo -e "${RED}❌ FINAL BUILD STATUS: FAILING${NC}"
    fi
    
    # Show error reduction
    local initial_errors=$(wc -l < "$EXTRACTED_ERRORS" 2>/dev/null || echo "0")
    local final_errors
    final_errors=$(./gradlew build --stacktrace 2>&1 | grep -c "^e: file://" || echo "0")
    
    echo -e "${YELLOW}Error Reduction:${NC} $initial_errors → $final_errors errors"
    
    # Show files modified
    local modified_files=$(git status --porcelain | grep "^M" | wc -l)
    echo -e "${YELLOW}Files Modified:${NC} $modified_files files"
    
    echo -e "\n${PURPLE}Next Steps:${NC}"
    echo "  1. Review modified files: git diff"
    echo "  2. Run full test suite: ./gradlew test"
    echo "  3. Commit changes: git add -A && git commit -m 'Apply Project Armor fixes'"
    echo "  4. If still failing, run: ./fix-stacktrace-opus.sh"
}

# Main execution
main() {
    echo -e "${CYAN}Fix Stacktrace with Opus - Virtuous Cycle${NC}"
    echo -e "${CYAN}========================================${NC}"
    
    # Change to project root
    cd "$PROJECT_ROOT"
    
    # Check prerequisites
    if [[ ! -f "scripts/super-build-policy.sh" ]]; then
        log_error "scripts/super-build-policy.sh not found"
        exit 1
    fi
    
    if [[ ! -f "gradlew" ]]; then
        log_error "gradlew not found"
        exit 1
    fi
    
    # Run the virtuous cycle
    if run_virtuous_cycle; then
        generate_summary
        exit 0
    else
        log_error "Virtuous cycle failed"
        generate_summary
        exit 1
    fi
}

# Show usage if help requested
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    echo "Fix Stacktrace with Opus - Virtuous Cycle"
    echo "========================================"
    echo
    echo "Usage: $0"
    echo
    echo "This script implements the most valuable immediate workflow:"
    echo "1. Extract 10 'e:' stacktraces from gradle build output"
    echo "2. Process with Opus-optimal format using super-build-policy.sh"
    echo "3. Apply Project Armor to intersection of dirty+stacktrace files"
    echo "4. Repeat until build passes (max 5 cycles)"
    echo
    echo "Files created:"
    echo "  - build-stacktrace.log    : Gradle build output"
    echo "  - extracted-errors.log    : Top 10 'e:' compilation errors"
    echo "  - opus-fixes.log         : Opus-processed stacktrace output"
    echo "  - virtuous-cycle.log     : Cycle execution log"
    echo
    echo "Example:"
    echo "  $0"
    echo
    exit 0
fi

# Run main
main "$@"