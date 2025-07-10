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
LLM_PROVIDER="claude"
LLM_MODEL="claude-3-sonnet-20240229"

# Function to print usage
print_usage() {
    cat << EOF
${CYAN}Super Build Policy Script${NC}

${YELLOW}Usage:${NC}
    $0 [command] [options]

${YELLOW}Commands:${NC}
    ${GREEN}strip-versions${NC}      Strip versions & stomp deviating targets in child gradle files
    ${GREEN}validate-build${NC}      Validate build file immutability
    ${GREEN}grant-permission${NC}    Grant permission to modify build files
    ${GREEN}apply-armor${NC}         Apply Project Armor to all Kotlin files
    ${GREEN}fix-stacktrace${NC}      Process stacktrace, apply armor to bugfix+stacktrace files
    ${GREEN}fix-lambdas${NC}         Fix infix lambda type annotations
    ${GREEN}llm-fix${NC}             Process stacktrace and feed to LLM for quick fixing
    ${GREEN}pre-build${NC}           Run all pre-build policies
    ${GREEN}post-error${NC}          Process gradle errors with Opus-optimal format

${YELLOW}Options:${NC}
    -f, --file FILE          Build file or stacktrace file
    -p, --purpose PURPOSE    Purpose for build file modification
    -d, --duration MINUTES   Permission duration in minutes
    -g, --gradle-log FILE    Gradle log file for context
    -o, --opus               Use Opus-optimal stacktrace format
    -l, --llm-provider PROVIDER  LLM provider (claude, gpt, gemini) [default: claude]
    -m, --model MODEL        LLM model name [default: claude-3-sonnet-20240229]
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

    # Fix a stacktrace with full context (armor applied to bugfix+stacktrace files only)
    $0 fix-stacktrace -f error.log

    # Process stacktrace and get LLM fix suggestions
    $0 llm-fix -f error.log

    # Use specific LLM provider and model
    $0 llm-fix -f error.log -l gpt -m gpt-4

    # Process gradle error with Opus-optimal format
    $0 post-error -f build.log -g gradle.log --opus

    # Run all pre-build checks
    $0 pre-build

${YELLOW}Environment Variables:${NC}
    ENFORCE_IMMUTABILITY     Enable build file immutability checks
    ARMOR_ALL               Apply armor to all files automatically
    OPUS_DEFAULT            Use Opus format by default
    ANTHROPIC_API_KEY       API key for Claude
    OPENAI_API_KEY          API key for GPT
    GOOGLE_API_KEY          API key for Gemini

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
                if [[ "$MODE" == "fix-stacktrace" || "$MODE" == "post-error" || "$MODE" == "llm-fix" ]]; then
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
            -l|--llm-provider)
                shift
                if [ $# -eq 0 ]; then
                    echo -e "${RED}Error: --llm-provider requires an argument${NC}"
                    exit 1
                fi
                LLM_PROVIDER="$1"
                shift
                ;;
            -m|--model)
                shift
                if [ $# -eq 0 ]; then
                    echo -e "${RED}Error: --model requires an argument${NC}"
                    exit 1
                fi
                LLM_MODEL="$1"
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
    echo -e "${CYAN}Stripping versions & enforcing target consistency...${NC}"
    echo -e "${YELLOW}This will stomp any deviating targets to match trikeshed-lib${NC}"
    cd "$PROJECT_ROOT"
    
    # Run gradle task
    ./gradlew stripVersions
    
    echo -e "${GREEN}✓ Version stripping & target enforcement complete${NC}"
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

cmd_llm_fix() {
    if [ -z "$STACKTRACE_FILE" ]; then
        echo -e "${RED}Error: --file is required for llm-fix${NC}"
        exit 1
    fi
    
    if [ ! -f "$STACKTRACE_FILE" ]; then
        echo -e "${RED}Error: Stacktrace file not found: $STACKTRACE_FILE${NC}"
        exit 1
    fi
    
    echo -e "${CYAN}Processing stacktrace: $STACKTRACE_FILE${NC}"
    cd "$PROJECT_ROOT"
    
    # First, process the stacktrace to get clean format
    TEMP_PROCESSED="/tmp/stacktrace_processed_$$.txt"
    
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
    
    # Process stacktrace first
    ./gradlew $TASK $GRADLE_ARGS
    
    # Get the processed output
    PROCESSED_FILE="${STACKTRACE_FILE%.*}_processed.txt"
    if [ ! -f "$PROCESSED_FILE" ]; then
        echo -e "${RED}Error: Processed stacktrace file not found${NC}"
        exit 1
    fi
    
    # Create LLM prompt
    echo -e "${CYAN}Creating LLM prompt for quick fixing...${NC}"
    
    # Read project context
    PROJECT_CONTEXT=""
    if [ -f "README.md" ]; then
        PROJECT_CONTEXT=$(head -n 100 README.md | grep -v "^#" | tr '\n' ' ' | sed 's/  */ /g')
    fi
    
    # Create the prompt
    PROMPT_FILE="/tmp/llm_prompt_$$.txt"
    cat > "$PROMPT_FILE" << EOF
You are an expert Kotlin/Gradle developer working on the v2superbikeshed project. 

Project Context:
$PROJECT_CONTEXT

Please analyze this stacktrace and provide:
1. Root cause analysis
2. Specific fix suggestions with code examples
3. Files that need to be modified
4. Any additional context or warnings

Stacktrace:
$(cat "$PROCESSED_FILE")

Provide your analysis in a clear, actionable format suitable for immediate implementation.
EOF
    
    # Call LLM based on provider
    echo -e "${CYAN}Calling LLM ($LLM_PROVIDER/$LLM_MODEL) for fix suggestions...${NC}"
    
    case "$LLM_PROVIDER" in
        claude)
            call_claude_api "$PROMPT_FILE"
            ;;
        gpt)
            call_gpt_api "$PROMPT_FILE"
            ;;
        gemini)
            call_gemini_api "$PROMPT_FILE"
            ;;
        *)
            echo -e "${RED}Error: Unsupported LLM provider: $LLM_PROVIDER${NC}"
            exit 1
            ;;
    esac
    
    # Cleanup
    rm -f "$PROMPT_FILE" "$TEMP_PROCESSED"
    
    echo -e "${GREEN}✓ LLM fix suggestions complete${NC}"
}

# LLM API calling functions
call_claude_api() {
    local prompt_file="$1"
    local output_file="${STACKTRACE_FILE%.*}_llm_fix.txt"
    
    if [ -z "$ANTHROPIC_API_KEY" ]; then
        echo -e "${RED}Error: ANTHROPIC_API_KEY environment variable not set${NC}"
        echo -e "${YELLOW}Please set your Anthropic API key: export ANTHROPIC_API_KEY=your_key_here${NC}"
        exit 1
    fi
    
    # Use curl to call Claude API
    curl -s -X POST "https://api.anthropic.com/v1/messages" \
        -H "Content-Type: application/json" \
        -H "x-api-key: $ANTHROPIC_API_KEY" \
        -H "anthropic-version: 2023-06-01" \
        -d "{
            \"model\": \"$LLM_MODEL\",
            \"max_tokens\": 4000,
            \"messages\": [
                {
                    \"role\": \"user\",
                    \"content\": \"$(cat "$prompt_file" | sed 's/"/\\"/g' | tr '\n' ' ')\"
                }
            ]
        }" | jq -r '.content[0].text' > "$output_file"
    
    echo -e "${GREEN}✓ Claude response written to: $output_file${NC}"
    
    # Show preview
    echo -e "\n${YELLOW}Preview:${NC}"
    head -n 20 "$output_file"
}

call_gpt_api() {
    local prompt_file="$1"
    local output_file="${STACKTRACE_FILE%.*}_llm_fix.txt"
    
    if [ -z "$OPENAI_API_KEY" ]; then
        echo -e "${RED}Error: OPENAI_API_KEY environment variable not set${NC}"
        echo -e "${YELLOW}Please set your OpenAI API key: export OPENAI_API_KEY=your_key_here${NC}"
        exit 1
    fi
    
    # Use curl to call GPT API
    curl -s -X POST "https://api.openai.com/v1/chat/completions" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $OPENAI_API_KEY" \
        -d "{
            \"model\": \"$LLM_MODEL\",
            \"max_tokens\": 4000,
            \"messages\": [
                {
                    \"role\": \"user\",
                    \"content\": \"$(cat "$prompt_file" | sed 's/"/\\"/g' | tr '\n' ' ')\"
                }
            ]
        }" | jq -r '.choices[0].message.content' > "$output_file"
    
    echo -e "${GREEN}✓ GPT response written to: $output_file${NC}"
    
    # Show preview
    echo -e "\n${YELLOW}Preview:${NC}"
    head -n 20 "$output_file"
}

call_gemini_api() {
    local prompt_file="$1"
    local output_file="${STACKTRACE_FILE%.*}_llm_fix.txt"
    
    if [ -z "$GOOGLE_API_KEY" ]; then
        echo -e "${RED}Error: GOOGLE_API_KEY environment variable not set${NC}"
        echo -e "${YELLOW}Please set your Google API key: export GOOGLE_API_KEY=your_key_here${NC}"
        exit 1
    fi
    
    # Use curl to call Gemini API
    curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/$LLM_MODEL:generateContent" \
        -H "Content-Type: application/json" \
        -d "{
            \"contents\": [
                {
                    \"parts\": [
                        {
                            \"text\": \"$(cat "$prompt_file" | sed 's/"/\\"/g' | tr '\n' ' ')\"
                        }
                    ]
                }
            ],
            \"generationConfig\": {
                \"maxOutputTokens\": 4000
            }
        }?key=$GOOGLE_API_KEY" | jq -r '.candidates[0].content.parts[0].text' > "$output_file"
    
    echo -e "${GREEN}✓ Gemini response written to: $output_file${NC}"
    
    # Show preview
    echo -e "\n${YELLOW}Preview:${NC}"
    head -n 20 "$output_file"
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
        llm-fix)
            cmd_llm_fix
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