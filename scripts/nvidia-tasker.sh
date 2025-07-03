#!/bin/bash

# NVIDIA Tasker Script - Clone repo and run tasks with NVIDIA models
set -euo pipefail

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

# Function to call NVIDIA API
call_nvidia_api() {
    local prompt="$1"
    local thinking_mode="${2:-off}"
    local temperature="${3:-0}"
    
    local system_content="detailed thinking $thinking_mode"
    
    curl -s "$NVIDIA_ENDPOINT" \
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
            \"stream\": false
        }" | jq -r '.choices[0].message.content'
}

# Function to run aider with NVIDIA model
run_aider_nvidia() {
    local prompt="$1"
    local thinking_mode="${2:-off}"
    
    if command -v aider &> /dev/null; then
        print_color $BLUE "Running aider with NVIDIA NIM model..."
        
        # Use working shell command format
        export NVIDIA_NIM_API_KEY="$NVIDIA_API_KEY"
        
        aider --model nvidia_nim/nvidia/llama-3.1-nemotron-ultra-253b-v1 --message "$prompt"
    else
        print_color $YELLOW "Aider not found. Using direct API call..."
        call_nvidia_api "$prompt" "$thinking_mode" "0.6"
    fi
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
                echo "  --no-clone       Work in current directory"
                echo "  --task PROMPT    Task prompt (use '-' for stdin)"
                echo "  --help           Show this help"
                echo ""
                echo "Examples:"
                echo "  $0 --task 'Fix compilation errors'"
                echo "  echo 'Refactor code' | $0 --task - --smart"
                echo "  $0 --aider --task 'Add new feature'"
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
    
    if [ "$use_aider" = true ]; then
        run_aider_nvidia "$task_prompt" "$([ "$use_smart_mode" = true ] && echo 'on' || echo 'off')"
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