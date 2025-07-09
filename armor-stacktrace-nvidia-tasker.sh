#!/bin/bash
set -euo pipefail

# Project Armor Stacktrace Fixer with NVIDIA Tasker Integration
# Implements TrikeShed stacktrace methodology using K2Script sandbox

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
K2_SCRIPT_PATH="$PROJECT_ROOT/nexus/nvidia-tasker-stacktrace.kts"
BUILD_LOG="${PROJECT_ROOT}/build-stacktrace.log"
ARMOR_LOG="${PROJECT_ROOT}/armor-application.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$ARMOR_LOG"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" | tee -a "$ARMOR_LOG"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$ARMOR_LOG"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$ARMOR_LOG"
}

# Initialize armor log
echo "=== Project Armor Stacktrace Fixer Session ===" > "$ARMOR_LOG"
echo "Started: $(date)" >> "$ARMOR_LOG"
echo "Project: $PROJECT_ROOT" >> "$ARMOR_LOG"
echo "=============================================" >> "$ARMOR_LOG"

show_usage() {
    echo "Project Armor Stacktrace Fixer with NVIDIA Tasker Integration"
    echo "============================================================="
    echo
    echo "Usage: $0 <command> [options]"
    echo
    echo "Commands:"
    echo "  build-analyze      - Run build, capture stacktrace, analyze with NVIDIA"
    echo "  apply-armor        - Apply Project Armor to dirty stacktrace files"
    echo "  nvidia-query <q>   - Query NVIDIA with compilation context"
    echo "  transform <rank>   - Apply ranked stacktrace transform (1-4)"
    echo "  bisect <severity>  - Bisect errors by severity"
    echo "  report             - Generate zero error achievement report"
    echo "  demo               - Run complete TrikeShed demo"
    echo "  interactive        - Enter interactive mode"
    echo
    echo "Options:"
    echo "  --k2script-path PATH   - Custom path to K2Script file"
    echo "  --build-log PATH       - Custom build log path"
    echo "  --dry-run             - Show what would be done"
    echo
    echo "Examples:"
    echo "  $0 build-analyze"
    echo "  $0 apply-armor"
    echo "  $0 nvidia-query 'How to fix circular dependencies?'"
    echo "  $0 transform 4"
    echo "  $0 demo"
}

# Parse command line arguments
COMMAND=""
QUERY=""
RANK=""
SEVERITY=""
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --k2script-path)
            K2_SCRIPT_PATH="$2"
            shift 2
            ;;
        --build-log)
            BUILD_LOG="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        build-analyze|apply-armor|report|demo|interactive)
            COMMAND="$1"
            shift
            ;;
        nvidia-query)
            COMMAND="nvidia-query"
            QUERY="$2"
            shift 2
            ;;
        transform)
            COMMAND="transform"
            RANK="$2"
            shift 2
            ;;
        bisect)
            COMMAND="bisect"
            SEVERITY="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

if [[ -z "$COMMAND" ]]; then
    show_usage
    exit 1
fi

# Verify K2Script availability
check_k2script() {
    if ! command -v k2script &> /dev/null; then
        log_error "k2script not found. Please install K2Script."
        exit 1
    fi
    
    if [[ ! -f "$K2_SCRIPT_PATH" ]]; then
        log_error "K2Script file not found: $K2_SCRIPT_PATH"
        exit 1
    fi
    
    log_info "K2Script found: $(k2script --version)"
}

# Run gradle build and capture stacktrace
run_build_with_stacktrace() {
    log_info "Running Gradle build to capture stacktrace..."
    
    local build_output
    if build_output=$(./gradlew build --stacktrace 2>&1); then
        log_success "Build completed successfully - no stacktrace needed"
        echo "$build_output" > "$BUILD_LOG"
        return 0
    else
        log_warn "Build failed - capturing stacktrace for analysis"
        echo "$build_output" > "$BUILD_LOG"
        return 1
    fi
}

# Apply Project Armor using buildSrc implementation
apply_project_armor() {
    log_info "Applying Project Armor to dirty stacktrace files..."
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_info "DRY RUN: Would apply armor to files intersecting dirty + stacktrace"
        return 0
    fi
    
    # Run the armor application task
    local armor_output
    if armor_output=$(./gradlew applyProjectArmor 2>&1); then
        log_success "Project Armor applied successfully"
        echo "$armor_output" >> "$ARMOR_LOG"
    else
        log_error "Failed to apply Project Armor"
        echo "$armor_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Load compilation context into K2Script
load_compilation_context() {
    log_info "Loading compilation context into NVIDIA Tasker..."
    
    if [[ ! -f "$BUILD_LOG" ]]; then
        log_error "Build log not found: $BUILD_LOG"
        return 1
    fi
    
    # Use K2Script to load the stacktrace
    local k2_output
    if k2_output=$(k2script "$K2_SCRIPT_PATH" load "$BUILD_LOG" 2>&1); then
        log_success "Compilation context loaded into K2Script sandbox"
        echo "$k2_output" >> "$ARMOR_LOG"
    else
        log_error "Failed to load compilation context"
        echo "$k2_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Query NVIDIA with compilation context
query_nvidia() {
    local query="$1"
    log_info "Querying NVIDIA with compilation context: $query"
    
    local k2_output
    if k2_output=$(k2script "$K2_SCRIPT_PATH" query "$query" 2>&1); then
        log_success "NVIDIA query completed"
        echo "=== NVIDIA RESPONSE ===" >> "$ARMOR_LOG"
        echo "$k2_output" >> "$ARMOR_LOG"
        echo "======================" >> "$ARMOR_LOG"
        echo "$k2_output"
    else
        log_error "NVIDIA query failed"
        echo "$k2_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Apply ranked transform
apply_transform() {
    local rank="$1"
    log_info "Applying ranked transform: $rank"
    
    local k2_output
    if k2_output=$(k2script "$K2_SCRIPT_PATH" transform "$rank" 2>&1); then
        log_success "Transform rank $rank applied"
        echo "$k2_output" >> "$ARMOR_LOG"
        echo "$k2_output"
    else
        log_error "Transform failed"
        echo "$k2_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Bisect by severity
bisect_by_severity() {
    local severity="$1"
    log_info "Bisecting errors by severity: $severity"
    
    local k2_output
    if k2_output=$(k2script "$K2_SCRIPT_PATH" bisect "$severity" 2>&1); then
        log_success "Bisection completed"
        echo "$k2_output" >> "$ARMOR_LOG"
        echo "$k2_output"
    else
        log_error "Bisection failed"
        echo "$k2_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Generate achievement report
generate_report() {
    log_info "Generating zero error achievement report..."
    
    local k2_output
    if k2_output=$(k2script "$K2_SCRIPT_PATH" report 2>&1); then
        log_success "Achievement report generated"
        echo "$k2_output" >> "$ARMOR_LOG"
        echo "$k2_output"
    else
        log_error "Report generation failed"
        echo "$k2_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Run complete demo
run_demo() {
    log_info "Running complete TrikeShed stacktrace demo..."
    
    local k2_output
    if k2_output=$(k2script "$K2_SCRIPT_PATH" demo 2>&1); then
        log_success "Demo completed successfully"
        echo "$k2_output" >> "$ARMOR_LOG"
        echo "$k2_output"
    else
        log_error "Demo failed"
        echo "$k2_output" >> "$ARMOR_LOG"
        return 1
    fi
}

# Interactive mode
interactive_mode() {
    log_info "Entering interactive mode..."
    
    echo "🎯 Project Armor Interactive Mode"
    echo "=================================="
    echo "Available commands:"
    echo "  build      - Run build and capture stacktrace"
    echo "  armor      - Apply Project Armor"
    echo "  query <q>  - Query NVIDIA"
    echo "  transform <rank> - Apply transform (1-4)"
    echo "  bisect <severity> - Bisect errors"
    echo "  report     - Generate report"
    echo "  demo       - Run demo"
    echo "  exit       - Exit interactive mode"
    echo
    
    while true; do
        echo -n "armor> "
        read -r input
        
        case $input in
            build)
                run_build_with_stacktrace && load_compilation_context
                ;;
            armor)
                apply_project_armor
                ;;
            query\ *)
                query_nvidia "${input#query }"
                ;;
            transform\ *)
                apply_transform "${input#transform }"
                ;;
            bisect\ *)
                bisect_by_severity "${input#bisect }"
                ;;
            report)
                generate_report
                ;;
            demo)
                run_demo
                ;;
            exit)
                log_info "Exiting interactive mode"
                break
                ;;
            *)
                echo "Unknown command: $input"
                ;;
        esac
        echo
    done
}

# Main execution
main() {
    log_info "Starting Project Armor Stacktrace Fixer"
    log_info "Command: $COMMAND"
    
    check_k2script
    
    case "$COMMAND" in
        build-analyze)
            run_build_with_stacktrace
            load_compilation_context
            query_nvidia "Analyze the compilation errors and suggest TrikeShed-compatible fixes"
            ;;
        apply-armor)
            apply_project_armor
            ;;
        nvidia-query)
            load_compilation_context
            query_nvidia "$QUERY"
            ;;
        transform)
            load_compilation_context
            apply_transform "$RANK"
            ;;
        bisect)
            load_compilation_context
            bisect_by_severity "$SEVERITY"
            ;;
        report)
            load_compilation_context
            generate_report
            ;;
        demo)
            run_demo
            ;;
        interactive)
            interactive_mode
            ;;
        *)
            log_error "Unknown command: $COMMAND"
            show_usage
            exit 1
            ;;
    esac
    
    log_success "Project Armor session completed"
    echo "Session log: $ARMOR_LOG"
}

# Run main function
main "$@"