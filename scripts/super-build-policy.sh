#!/bin/bash
# Super Build Policy Script - Unified build tooling for v2superbikeshed
# Combines all build policies, stacktrace fixing, and armor application

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." &> /dev/null && pwd )"

# Default values
MODE="help"
STACKTRACE_FILE=""
BUILD_FILE=""
PURPOSE=""
DURATION=""
GRADLE_LOG=""
OPUS_MODE=false

# Function to print usage
print_usage() {
    cat << EOF
${CYAN}Super Build Policy Script${NC}

${YELLOW}Usage:${NC}
    $0 [command] [options]

${YELLOW}Commands:${NC}
    ${GREEN}strip-versions${NC}      Strip version declarations from child gradle files
    ${GREEN}validate-build${NC}      Validate build file immutability
    ${GREEN}grant-permission${NC}    Grant permission to modify build files
    ${GREEN}apply-armor${NC}         Apply Project Armor to all Kotlin files
    ${GREEN}fix-stacktrace${NC}      Process stacktrace with armor and context
    ${GREEN}fix-lambdas${NC}         Fix infix lambda type annotations
    ${GREEN}pre-build${NC}           Run all pre-build policies
    ${GREEN}post-error${NC}          Process gradle errors with Opus-optimal format

${YELLOW}Options:${NC}
    -f, --file FILE          Build file or stacktrace file
    -p, --purpose PURPOSE    Purpose for build file modification
    -d, --duration MINUTES   Permission duration in minutes
    -g, --gradle-log FILE    Gradle log file for context
    -o, --opus               Use Opus-optimal stacktrace format
    -h, --help               Show this help message

${YELLOW}Examples:${NC}
    # Strip versions from all child gradle files
    $0 strip-versions

    # Validate build file immutability
    $0 validate-build

    # Grant permission to modify a build file
    $0 grant-permission -f platform-launcher/build.gradle.kts -p "Add GPU support"

    # Apply armor to all Kotlin files
    $0 apply-armor

    # Fix a stacktrace with full context
    $0 fix-stacktrace -f error.log

    # Process gradle error with Opus-optimal format
    $0 post-error -f build.log -g gradle.log --opus

    # Run all pre-build checks
    $0 pre-build

${YELLOW}Environment Variables:${NC}
    ENFORCE_IMMUTABILITY     Enable build file immutability checks
    ARMOR_ALL               Apply armor to all files automatically
    OPUS_DEFAULT            Use Opus format by default

EOF
}

# Parse command line arguments
parse_args() {
    if [ $# -eq 0 ]; then
        print_usage
        exit 0
    fi

    MODE="$1"
    shift

    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--file)
                shift
                if [ $# -eq 0 ]; then
                    echo -e "${RED}Error: --file requires an argument${NC}"
                    exit 1
                fi
                if [[ "$MODE" == "fix-stacktrace" || "$MODE" == "post-error" ]]; then
                    STACKTRACE_FILE="$1"
                else
                    BUILD_FILE="$1"
                fi
                shift
                ;;
            -p|--purpose)
                shift
                if [ $# -eq 0 ]; then
                    echo -e "${RED}Error: --purpose requires an argument${NC}"
                    exit 1
                fi
                PURPOSE="$1"
                shift
                ;;
            -d|--duration)
                shift
                if [ $# -eq 0 ]; then
                    echo -e "${RED}Error: --duration requires an argument${NC}"
                    exit 1
                fi
                DURATION="$1"
                shift
                ;;
            -g|--gradle-log)
                shift
                if [ $# -eq 0 ]; then
                    echo -e "${RED}Error: --gradle-log requires an argument${NC}"
                    exit 1
                fi
                GRADLE_LOG="$1"
                shift
                ;;
            -o|--opus)
                OPUS_MODE=true
                shift
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            *)
                echo -e "${RED}Error: Unknown option $1${NC}"
                print_usage
                exit 1
                ;;
        esac
    done
}

# Command implementations
cmd_strip_versions() {
    echo -e "${CYAN}Stripping versions from child gradle files...${NC}"
    cd "$PROJECT_ROOT"
    
    # Run gradle task
    ./gradlew stripVersions
    
    echo -e "${GREEN}✓ Version stripping complete${NC}"
}

cmd_validate_build() {
    echo -e "${CYAN}Validating build file immutability...${NC}"
    cd "$PROJECT_ROOT"
    
    # Run gradle task
    if ./gradlew validateBuildFiles; then
        echo -e "${GREEN}✓ All build files validated successfully${NC}"
    else
        echo -e "${RED}✗ Build file immutability violations detected${NC}"
        exit 1
    fi
}

cmd_grant_permission() {
    if [ -z "$BUILD_FILE" ]; then
        echo -e "${RED}Error: --file is required for grant-permission${NC}"
        exit 1
    fi
    
    if [ -z "$PURPOSE" ]; then
        echo -e "${RED}Error: --purpose is required for grant-permission${NC}"
        exit 1
    fi
    
    echo -e "${CYAN}Granting permission to modify $BUILD_FILE${NC}"
    echo -e "${YELLOW}Purpose: $PURPOSE${NC}"
    
    cd "$PROJECT_ROOT"
    
    # Build gradle arguments
    GRADLE_ARGS="-Pfile=$BUILD_FILE -Ppurpose=$PURPOSE"
    if [ -n "$DURATION" ]; then
        GRADLE_ARGS="$GRADLE_ARGS -Pduration=$DURATION"
        echo -e "${YELLOW}Duration: $DURATION minutes${NC}"
    fi
    
    ./gradlew grantBuildFilePermission $GRADLE_ARGS
    
    echo -e "${GREEN}✓ Permission granted${NC}"
}

cmd_apply_armor() {
    echo -e "${CYAN}Applying Project Armor to all Kotlin files...${NC}"
    cd "$PROJECT_ROOT"
    
    # Run gradle task
    ./gradlew applyProjectArmor
    
    # Also fix infix lambdas
    echo -e "${CYAN}Fixing infix lambda type annotations...${NC}"
    ./gradlew fixInfixLambdas
    
    echo -e "${GREEN}✓ Project Armor applied${NC}"
}

cmd_fix_stacktrace() {
    if [ -z "$STACKTRACE_FILE" ]; then
        echo -e "${RED}Error: --file is required for fix-stacktrace${NC}"
        exit 1
    fi
    
    if [ ! -f "$STACKTRACE_FILE" ]; then
        echo -e "${RED}Error: Stacktrace file not found: $STACKTRACE_FILE${NC}"
        exit 1
    fi
    
    echo -e "${CYAN}Processing stacktrace: $STACKTRACE_FILE${NC}"
    cd "$PROJECT_ROOT"
    
    # Choose processor based on mode
    if [ "$OPUS_MODE" = true ] || [ "$OPUS_DEFAULT" = true ]; then
        echo -e "${PURPLE}Using Opus-optimal format${NC}"
        TASK="processStackTraceOpus"
    else
        TASK="processStackTrace"
    fi
    
    # Build gradle arguments
    GRADLE_ARGS="-Pstacktrace=$STACKTRACE_FILE"
    if [ -n "$GRADLE_LOG" ]; then
        GRADLE_ARGS="$GRADLE_ARGS -PgradleLog=$GRADLE_LOG"
    fi
    
    ./gradlew $TASK $GRADLE_ARGS
    
    # Output file location
    OUTPUT_FILE="${STACKTRACE_FILE%.*}_processed.txt"
    echo -e "${GREEN}✓ Processed stacktrace written to: $OUTPUT_FILE${NC}"
    
    # Show preview
    echo -e "\n${YELLOW}Preview:${NC}"
    head -n 50 "$OUTPUT_FILE"
}

cmd_fix_lambdas() {
    echo -e "${CYAN}Fixing infix lambda type annotations...${NC}"
    cd "$PROJECT_ROOT"
    
    ./gradlew fixInfixLambdas
    
    echo -e "${GREEN}✓ Lambda type annotations fixed${NC}"
}

cmd_pre_build() {
    echo -e "${CYAN}Running pre-build policy checks...${NC}"
    cd "$PROJECT_ROOT"
    
    # Strip versions
    echo -e "\n${BLUE}1. Stripping versions...${NC}"
    ./gradlew stripVersions
    
    # Validate if enabled
    if [ "$ENFORCE_IMMUTABILITY" = true ]; then
        echo -e "\n${BLUE}2. Validating build files...${NC}"
        ./gradlew validateBuildFiles
    fi
    
    # Apply armor if enabled
    if [ "$ARMOR_ALL" = true ]; then
        echo -e "\n${BLUE}3. Applying Project Armor...${NC}"
        ./gradlew applyProjectArmor
    fi
    
    echo -e "\n${GREEN}✓ Pre-build policies complete${NC}"
}

cmd_post_error() {
    if [ -z "$STACKTRACE_FILE" ]; then
        echo -e "${YELLOW}Reading from stdin...${NC}"
        STACKTRACE_FILE="/tmp/gradle_error_$$.log"
        cat > "$STACKTRACE_FILE"
    fi
    
    # Always use Opus format for post-error
    OPUS_MODE=true
    cmd_fix_stacktrace
}

# Create gradle tasks if they don't exist
ensure_gradle_tasks() {
    cd "$PROJECT_ROOT"
    
    # Check if buildSrc exists
    if [ ! -d "buildSrc" ]; then
        echo -e "${YELLOW}Creating buildSrc directory...${NC}"
        mkdir -p buildSrc/src/main/kotlin
    fi
    
    # Apply the plugin to root build.gradle.kts if not already applied
    if ! grep -q "BuildPolicyPlugin" build.gradle.kts 2>/dev/null; then
        echo -e "${YELLOW}Note: BuildPolicyPlugin not applied to root project${NC}"
        echo -e "${YELLOW}Add this to your root build.gradle.kts:${NC}"
        echo -e "${BLUE}plugins {${NC}"
        echo -e "${BLUE}    id(\"buildtools.BuildPolicyPlugin\")${NC}"
        echo -e "${BLUE}}${NC}"
    fi
}

# Main execution
main() {
    parse_args "$@"
    ensure_gradle_tasks
    
    case "$MODE" in
        strip-versions)
            cmd_strip_versions
            ;;
        validate-build)
            cmd_validate_build
            ;;
        grant-permission)
            cmd_grant_permission
            ;;
        apply-armor)
            cmd_apply_armor
            ;;
        fix-stacktrace)
            cmd_fix_stacktrace
            ;;
        fix-lambdas)
            cmd_fix_lambdas
            ;;
        pre-build)
            cmd_pre_build
            ;;
        post-error)
            cmd_post_error
            ;;
        help)
            print_usage
            ;;
        *)
            echo -e "${RED}Error: Unknown command '$MODE'${NC}"
            print_usage
            exit 1
            ;;
    esac
}

# Run main
main "$@"