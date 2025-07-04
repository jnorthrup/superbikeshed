#!/usr/bin/env bash

# NVIDIA Tasker Script - Clone repo and run tasks with NVIDIA models
set -eo pipefail

# Check bash version and suggest brew bash for macOS
if [ "${BASH_VERSION%%.*}" -lt 4 ]; then
    echo "Error: This script requires bash 4.0 or higher"
    echo "Current version: $BASH_VERSION"
    echo ""
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "On macOS, install newer bash with:"
        echo "  brew install bash"
        echo "Then run with: /usr/local/bin/bash $0"
        echo "Or add to PATH: export PATH=\"/usr/local/bin:\$PATH\""
    fi
    exit 1
fi

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_color() { echo -e "${1}${2}${NC}"; }

# NVIDIA API configuration
NVIDIA_API_KEY="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"
NVIDIA_MODEL="nvidia/llama-3.1-nemotron-ultra-253b-v1"
NVIDIA_ENDPOINT="https://integrate.api.nvidia.com/v1/chat/completions"

# Function to call NVIDIA API with streaming option
call_nvidia_api() {
    local prompt="$1"
    local thinking_mode="${2:-off}"
    local temperature="${3:-0}"
    local show_stream="${4:-false}"
    
    local system_content="detailed thinking $thinking_mode"
    
    if [ "$show_stream" = "true" ]; then
        # Stream response for real-time feedback
        echo "🤖 Streaming response..."
        local full_response=""
        local temp_file=$(mktemp)
        
        curl -N "$NVIDIA_ENDPOINT" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $NVIDIA_API_KEY" \
            -d "{
                \"model\": \"$NVIDIA_MODEL\",
                \"messages\": [
                    {\"role\":\"system\",\"content\":\"$system_content\"},
                    {\"role\":\"user\",\"content\":\"$prompt\"}
                ],
                \"temperature\": $temperature,
                \"top_p\": 0.95,
                \"max_tokens\": 4096,
                \"frequency_penalty\": 0,
                \"presence_penalty\": 0,
                \"stream\": true
            }" | while IFS= read -r line; do
                if [[ $line == data:* ]]; then
                    content=$(echo "$line" | sed 's/data: //' | jq -r '.choices[0].delta.content // empty' 2>/dev/null)
                    if [[ -n $content && $content != "null" ]]; then
                        printf "%s" "$content"
                        echo -n "$content" >> "$temp_file"
                    fi
                fi
            done
        echo ""
        cat "$temp_file"
        rm "$temp_file"
    else
        # Regular non-streaming response with better error handling
        # Create JSON payload with proper escaping
        local json_payload=$(jq -n \
            --arg model "$NVIDIA_MODEL" \
            --arg system_content "$system_content" \
            --arg user_content "$prompt" \
            --argjson temperature "$temperature" \
            '{
                "model": $model,
                "messages": [
                    {"role": "system", "content": $system_content},
                    {"role": "user", "content": $user_content}
                ],
                "temperature": $temperature,
                "top_p": 0.95,
                "max_tokens": 4096,
                "frequency_penalty": 0,
                "presence_penalty": 0,
                "stream": false
            }')
        
        local response=$(curl -s "$NVIDIA_ENDPOINT" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $NVIDIA_API_KEY" \
            -d "$json_payload")
        
        # Check for valid response and extract content
        if [[ -n "$response" ]]; then
            local content=$(echo "$response" | jq -r '.choices[0].message.content // empty')
            if [[ -n "$content" && "$content" != "null" ]]; then
                echo "$content"
            else
                echo "ERROR: No content in response"
                echo "DEBUG: Full response: $response" >&2
            fi
        else
            echo "ERROR: Empty response from API"
        fi
    fi
}

# FSM States for task execution - using regular arrays instead of associative
FSM_STATES=()
FSM_STATES[0]="INIT:Initialize environment and parse task"
FSM_STATES[1]="ANALYZE:Analyze codebase and understand requirements"
FSM_STATES[2]="PLAN:Create execution plan with tools"
FSM_STATES[3]="EXECUTE:Execute planned actions with shell access"
FSM_STATES[4]="VALIDATE:Validate results and check for completion"
FSM_STATES[5]="COMPLETE:Finalize and report results"

# Available tools for the AI agent
TOOLS=()
TOOLS[0]="shell:Execute shell commands"
TOOLS[1]="read_file:Read file contents"
TOOLS[2]="write_file:Write or modify files"
TOOLS[3]="search_code:Search through codebase"
TOOLS[4]="run_tests:Execute test suites"
TOOLS[5]="git_ops:Git operations"
TOOLS[6]="build:Build and compile"

# Helper function to get FSM state description
get_fsm_state_desc() {
    local state="$1"
    for entry in "${FSM_STATES[@]}"; do
        if [[ $entry == "$state:"* ]]; then
            echo "${entry#*:}"
            return
        fi
    done
    echo "Unknown state"
}

# Function to execute shell commands with logging
execute_shell_command() {
    local command="$1"
    local description="${2:-Shell command}"
    local show_terminal="${3:-false}"
    
    print_color $BLUE "Executing: $description"
    echo "Command: $command" >> NVIDIA_EXECUTION.log
    
    if [ "$show_terminal" = "true" ]; then
        # Show everything in terminal
        echo ">>> $command"
        if eval "$command" 2>&1 | tee -a NVIDIA_EXECUTION.log; then
            print_color $GREEN "✓ Command successful"
            echo "SUCCESS: $command" >> NVIDIA_EXECUTION.log
            return 0
        else
            print_color $RED "✗ Command failed"
            echo "FAILED: $command" >> NVIDIA_EXECUTION.log
            return 1
        fi
    else
        # Log only mode
        if eval "$command" 2>&1 | tee -a NVIDIA_EXECUTION.log; then
            print_color $GREEN "✓ Command successful"
            echo "SUCCESS: $command" >> NVIDIA_EXECUTION.log
            return 0
        else
            print_color $RED "✗ Command failed"
            echo "FAILED: $command" >> NVIDIA_EXECUTION.log
            return 1
        fi
    fi
}

# Function to provide tool descriptions for AI
get_tool_descriptions() {
    cat << 'EOF'
Available tools and their capabilities:

SHELL TOOL:
{"tool": "shell", "command": "ls -la", "description": "List files"}
{"tool": "shell", "command": "cat filename.txt", "description": "Read file"}
{"tool": "shell", "command": "grep -r pattern .", "description": "Search codebase"}
{"tool": "shell", "command": "./gradlew build", "description": "Build project"}
{"tool": "shell", "command": "git status", "description": "Check git status"}
{"tool": "shell", "command": "find . -name '*.kt'", "description": "Find Kotlin files"}

EXAMPLES:
- List directory: {"tool": "shell", "command": "ls -la", "description": "List current directory"}
- Search code: {"tool": "shell", "command": "rg 'pattern' --type kotlin", "description": "Search Kotlin files"}
- Run tests: {"tool": "shell", "command": "./gradlew test", "description": "Run all tests"}
- Check build: {"tool": "shell", "command": "./gradlew build --console=plain", "description": "Build with output"}
- Git operations: {"tool": "shell", "command": "git diff", "description": "Show changes"}

The shell tool can execute ANY command available on the system.
EOF
}

# Enhanced AI interaction with FSM and tools
run_nvidia_fsm() {
    local initial_prompt="$1"
    local thinking_mode="${2:-off}"
    local show_terminal="${3:-false}"
    local current_state="INIT"
    local max_iterations=10
    local iteration=0
    
    print_color $BLUE "=== NVIDIA FSM Agent Starting ==="
    print_color $BLUE "Initial task: $initial_prompt"
    
    # Initialize execution log
    echo "=== NVIDIA FSM Agent Execution Log ===" > NVIDIA_EXECUTION.log
    echo "Start time: $(date)" >> NVIDIA_EXECUTION.log
    echo "Task: $initial_prompt" >> NVIDIA_EXECUTION.log
    echo "" >> NVIDIA_EXECUTION.log
    
    while [[ $iteration -lt $max_iterations && $current_state != "COMPLETE" ]]; do
        iteration=$((iteration + 1))
        
        # Persistent status titlebar - always visible
        echo "╔══════════════════════════════════════════════════════════════════════════════╗"
        echo "║ NVIDIA FSM AGENT │ Task: $initial_prompt"
        echo "║ State: $current_state │ Iteration: $iteration/$max_iterations │ Dir: $(basename "$(pwd)")"
        echo "║ Branch: $(git branch --show-current 2>/dev/null || echo 'unknown') │ Model: nvidia/llama-3.1-nemotron-ultra-253b-v1"
        echo "╚══════════════════════════════════════════════════════════════════════════════╝"
        
        print_color $YELLOW "--- Iteration $iteration: State $current_state ---"
        
        # Build context for AI with clear instructions
        local state_desc=$(get_fsm_state_desc "$current_state")
        local context_prompt="You are an AI software development agent with shell access. You are currently in a Kotlin/Gradle project.

CURRENT STATE: $current_state ($state_desc)
ITERATION: $iteration/$max_iterations
TASK: $initial_prompt

ENVIRONMENT:
- Working Directory: $(pwd)
- Git Branch: $(git branch --show-current 2>/dev/null || echo 'unknown')
- Project Type: Kotlin Multiplatform with Gradle

AVAILABLE TOOL:
You can execute shell commands using JSON format:
{\"tool\": \"shell\", \"command\": \"your_command_here\", \"description\": \"what this does\"}

EXAMPLES:
- {\"tool\": \"shell\", \"command\": \"ls -la\", \"description\": \"List directory contents\"}
- {\"tool\": \"shell\", \"command\": \"find . -name '*.kt' | head -10\", \"description\": \"Find Kotlin files\"}
- {\"tool\": \"shell\", \"command\": \"./gradlew build --console=plain\", \"description\": \"Build the project\"}
- {\"tool\": \"shell\", \"command\": \"rg 'TODO' --type kotlin\", \"description\": \"Search for TODOs in Kotlin files\"}

CURRENT STATE GOALS:
- INIT: Get familiar with the project structure and understand the codebase
- ANALYZE: Examine code for optimization opportunities  
- PLAN: Create specific optimization strategy
- EXECUTE: Implement optimizations
- VALIDATE: Test changes and verify improvements
- COMPLETE: Summarize what was accomplished

EXECUTION LOG (recent):
$(tail -10 NVIDIA_EXECUTION.log 2>/dev/null || echo 'Starting fresh')

DIRECTORY CONTENTS:
$(ls -la | head -10)

INSTRUCTIONS:
1. Based on your current state, take specific actions using shell commands
2. Provide 1-3 shell commands in JSON format to progress toward the task goal
3. Always end your response with: NEXT_STATE: <state_name>
4. Valid next states: ANALYZE, PLAN, EXECUTE, VALIDATE, COMPLETE

Start by exploring the project structure to understand what needs to be analyzed and optimized."

        # Get AI response with live feedback - DIRECT API ONLY
        local ai_response
        print_color $BLUE "Getting AI response via direct NVIDIA API..."
        if [ "$show_terminal" = "true" ]; then
            print_color $YELLOW "🤖 Calling NVIDIA API directly..."
            echo "Model: $NVIDIA_MODEL"
            echo "Thinking mode: $thinking_mode"
            echo "Context length: $(echo "$context_prompt" | wc -c) chars"
            echo "⏳ Waiting for response..."
        fi
        ai_response=$(call_nvidia_api "$context_prompt" "$thinking_mode" "0.6" "false")
        
        # Log AI response
        echo "=== AI Response (Iteration $iteration, State $current_state) ===" >> NVIDIA_EXECUTION.log
        echo "$ai_response" >> NVIDIA_EXECUTION.log
        echo "" >> NVIDIA_EXECUTION.log
        
        # Parse and execute AI commands
        if echo "$ai_response" | grep -q '"tool"'; then
            print_color $GREEN "AI provided tool commands, executing..."
            
            # Show AI response if terminal mode
            if [ "$show_terminal" = "true" ]; then
                echo "╔══════════════════════════════════════════════════════════════════════════════╗"
                echo "║ AI RESPONSE WITH TOOLS"
                echo "╚══════════════════════════════════════════════════════════════════════════════╝"
                echo "$ai_response"
                echo "────────────────────────────────────────────────────────────────────────────────"
            fi
            
            # Extract JSON commands (simplified parsing)
            echo "$ai_response" | grep -o '{[^}]*"tool"[^}]*}' | while read -r json_cmd; do
                if [[ $json_cmd =~ \"tool\":\"shell\" ]]; then
                    local cmd=$(echo "$json_cmd" | sed -n 's/.*"command":"\([^"]*\)".*/\1/p')
                    local desc=$(echo "$json_cmd" | sed -n 's/.*"description":"\([^"]*\)".*/\1/p')
                    
                    if [[ -n $cmd ]]; then
                        execute_shell_command "$cmd" "$desc" "$show_terminal"
                    fi
                fi
            done
        else
            # Show AI response even if no tools
            if [ "$show_terminal" = "true" ]; then
                echo "╔══════════════════════════════════════════════════════════════════════════════╗"
                echo "║ AI RESPONSE (No tools requested)"
                echo "╚══════════════════════════════════════════════════════════════════════════════╝"
                echo "$ai_response"
                echo "────────────────────────────────────────────────────────────────────────────────"
            fi
            
            # If no tools and empty response, provide fallback commands
            if [[ -z "$ai_response" || "$ai_response" == *"ERROR"* ]]; then
                print_color $YELLOW "Empty/error response, providing fallback commands..."
                if [ "$current_state" = "INIT" ]; then
                    execute_shell_command "ls -la" "List directory contents" "$show_terminal"
                    execute_shell_command "find . -name '*.kt' | head -5" "Find Kotlin files" "$show_terminal"
                    current_state="ANALYZE"
                    print_color $GREEN "Auto-transitioning to state: $current_state"
                fi
            fi
        fi
        
        # Check for state transition
        if echo "$ai_response" | grep -q "NEXT_STATE:"; then
            local next_state=$(echo "$ai_response" | grep "NEXT_STATE:" | sed 's/.*NEXT_STATE: *//' | head -1 | tr -d ' ')
            print_color $GREEN "Transitioning to state: $next_state"
            current_state="$next_state"
        else
            print_color $YELLOW "No state transition specified, staying in $current_state"
        fi
        
        echo "--- End Iteration $iteration ---" >> NVIDIA_EXECUTION.log
        sleep 1
    done
    
    print_color $GREEN "=== FSM Agent Complete ==="
    print_color $GREEN "Final state: $current_state"
    print_color $GREEN "Execution log: NVIDIA_EXECUTION.log"
    
    # Show summary
    if [[ -f NVIDIA_EXECUTION.log ]]; then
        print_color $BLUE "=== Execution Summary ==="
        grep -E "(SUCCESS|FAILED|===)" NVIDIA_EXECUTION.log | tail -10
    fi
}

# Function for direct NVIDIA API calls only - NO AIDER
run_nvidia_direct() {
    local prompt="$1"
    local thinking_mode="${2:-off}"
    local show_terminal="${3:-false}"
    
    print_color $BLUE "Using direct NVIDIA API (no file editing)..."
    
    if [ "$show_terminal" = "true" ]; then
        print_color $YELLOW "🤖 Calling NVIDIA API..."
        echo "Model: $NVIDIA_MODEL"
        echo "Thinking mode: $thinking_mode"
        echo "⏳ Getting response..."
    fi
    
    local response=$(call_nvidia_api "$prompt" "$thinking_mode" "0.6" "$show_terminal")
    
    if [ "$show_terminal" = "true" ]; then
        echo ""
        echo "=== NVIDIA RESPONSE ==="
        echo "$response"
        echo "======================"
    fi
    
    echo "$response" > NVIDIA_RESPONSE.md
    print_color $GREEN "Response saved to: NVIDIA_RESPONSE.md"
}

# Function to create feature branch (same as gemini-tasker)
find_zero_error_tag() {
    print_color $BLUE "Finding latest zero-error tag..." >&2
    local zero_error_tag=$(git tag -l "*zero-error*" | sort -V | tail -n 1)
    
    if [ -z "$zero_error_tag" ]; then
        print_color $YELLOW "Warning: No zero-error tag found. Using current HEAD as parent." >&2
        echo "HEAD"
    else
        print_color $GREEN "Found zero-error tag: $zero_error_tag" >&2
        echo "$zero_error_tag"
    fi
}

create_feature_branch() {
    local base_ref="$1"
    local branch_name="$2"
    
    print_color $BLUE "Creating feature branch: $branch_name from $base_ref"
    
    if git show-ref --verify --quiet refs/heads/"$branch_name"; then
        print_color $RED "Error: Branch $branch_name already exists"
        exit 1
    fi
    
    git checkout -b "$branch_name" "$base_ref"
    print_color $GREEN "Successfully created and checked out branch: $branch_name"
}

# Main execution
main() {
    print_color $BLUE "=== NVIDIA Tasker - AI-Powered Development ===" 
    
    # Parse arguments
    local use_smart_mode=false
    local task_prompt=""
    local no_clone=false
    local use_aider=false
    local use_fsm=false
    local use_terminal=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --smart)
                use_smart_mode=true
                shift
                ;;
            --aider)
                use_aider=true
                shift
                ;;
            --fsm)
                use_fsm=true
                shift
                ;;
            --terminal)
                use_terminal=true
                shift
                ;;
            --no-clone)
                no_clone=true
                shift
                ;;
            --task)
                shift
                if [[ $# -gt 0 ]]; then
                    if [[ "$1" == "-" ]]; then
                        task_prompt=$(cat)
                    else
                        task_prompt="$1"
                    fi
                    shift
                else
                    print_color $RED "Error: --task requires a prompt"
                    exit 1
                fi
                ;;
            --help)
                echo "Usage: $0 [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --smart          Use smart mode (detailed thinking on, higher temperature)"
                echo "  --aider          Use aider instead of direct API calls"
                echo "  --fsm            Use FSM agent with shell access and tools"
                echo "  --terminal       Show all stdio output in terminal (transparent mode)"
                echo "  --no-clone       Work in current directory"
                echo "  --task PROMPT    Task prompt (use '-' for stdin)"
                echo "  --help           Show this help"
                echo ""
                echo "Examples:"
                echo "  $0 --task 'Fix compilation errors'"
                echo "  echo 'Refactor code' | $0 --task - --smart --terminal"
                echo "  $0 --aider --task 'Add new feature' --terminal"
                echo "  $0 --fsm --task 'Analyze and fix build issues' --terminal"
                exit 0
                ;;
            *)
                print_color $RED "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Check if in git repo
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        print_color $RED "Error: Not in a git repository"
        exit 1
    fi
    
    # Setup working directory
    local current_dir=$(basename "$(pwd)")
    local original_pwd="$(pwd)"
    local branch_name="feature/nvidia-task-$(date +%Y%m%d-%H%M%S)"
    
    if [ "$no_clone" = false ]; then
        local clone_target="/tmp/${current_dir}-nvidia-clone-$(date +%Y%m%d-%H%M%S)"
        print_color $BLUE "Cloning to: $clone_target"
        git clone "$original_pwd" "$clone_target"
        cd "$clone_target"
        git fetch --tags
    fi
    
    # Create feature branch
    local zero_error_parent=$(find_zero_error_tag)
    create_feature_branch "$zero_error_parent" "$branch_name"
    
    # Get task if not provided
    if [ -z "$task_prompt" ]; then
        print_color $BLUE "Enter task prompt (press Ctrl+D when done):"
        task_prompt=$(cat)
    fi
    
    # Save task
    echo "$task_prompt" > NVIDIA_TASK.md
    
    # Execute task
    print_color $GREEN "=== EXECUTING NVIDIA TASK ==="
    print_color $GREEN "Repository: $PWD"
    print_color $GREEN "Branch: $branch_name"
    print_color $GREEN "Task: $task_prompt"
    
    if [ "$use_fsm" = true ]; then
        print_color $BLUE "Using FSM Agent with shell access and tools..."
        run_nvidia_fsm "$task_prompt" "$([ "$use_smart_mode" = true ] && echo 'on' || echo 'off')" "$use_terminal"
    elif [ "$use_aider" = true ]; then
        run_nvidia_direct "$task_prompt" "$([ "$use_smart_mode" = true ] && echo 'on' || echo 'off')" "$use_terminal"
    else
        local thinking_mode="off"
        local temperature="0"
        
        if [ "$use_smart_mode" = true ]; then
            thinking_mode="on"
            temperature="0.6"
        fi
        
        print_color $BLUE "Calling NVIDIA API (thinking: $thinking_mode)..."
        local response=$(call_nvidia_api "$task_prompt" "$thinking_mode" "$temperature")
        
        echo "$response" | tee NVIDIA_RESPONSE.md
        
        print_color $GREEN "=== NVIDIA RESPONSE SAVED ==="
        print_color $GREEN "Response saved to: NVIDIA_RESPONSE.md"
    fi
    
    print_color $GREEN "=== TASK COMPLETED ==="
    print_color $GREEN "Working directory: $PWD"
}

# Check dependencies
if ! command -v curl &> /dev/null; then
    print_color $RED "Error: curl is required"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    print_color $RED "Error: jq is required for JSON parsing"
    exit 1
fi

# Run main function
main "$@"