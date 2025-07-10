#!/bin/bash
set -euo pipefail

# K2Script KMP Launcher with Sum Validation
# Launches sandboxed NVIDIA Tasker KMP agents with role FSM and duration control

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

# Configuration
K2SCRIPT_JAR="${PROJECT_ROOT}/k2script/build/libs/k2script-jvm.jar"
NVIDIA_TASKER_KTS="${PROJECT_ROOT}/nexus/nvidia-tasker-stacktrace.kts"
LAUNCHER_SUM_FILE="${PROJECT_ROOT}/.k2script-launcher.sum"
AGENT_SANDBOX_DIR="${PROJECT_ROOT}/.k2script-sandbox"
ROLE_STATE_FILE="${AGENT_SANDBOX_DIR}/role-state.fsm"

# Role FSM States
declare -A ROLE_STATES=(
    ["INIT"]="VALIDATING"
    ["VALIDATING"]="SANDBOXING"
    ["SANDBOXING"]="TASKING"
    ["TASKING"]="MONITORING"
    ["MONITORING"]="COMPLETING"
    ["COMPLETING"]="CLEANUP"
    ["CLEANUP"]="DONE"
)

# Default task duration (seconds)
DEFAULT_DURATION=300  # 5 minutes

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_state() {
    echo -e "${PURPLE}[FSM:$1]${NC} $2"
}

# Calculate sum of K2Script KMP launcher components
calculate_launcher_sum() {
    log_info "Calculating K2Script KMP launcher sum..."
    
    local sum=0
    local components=(
        "${K2SCRIPT_JAR}"
        "${PROJECT_ROOT}/k2script/build/libs/k2script-metadata.jar"
        "${PROJECT_ROOT}/k2script/build/libs/k2script-js.js"
        "${PROJECT_ROOT}/k2script/build/bin/native/releaseExecutable/k2script.kexe"
    )
    
    for component in "${components[@]}"; do
        if [[ -f "$component" ]]; then
            local size=$(stat -f%z "$component" 2>/dev/null || stat -c%s "$component" 2>/dev/null || echo "0")
            local checksum=$(shasum -a 256 "$component" 2>/dev/null | cut -d' ' -f1 || echo "0")
            # Sum = size + first 8 hex digits of checksum as decimal
            local hex_part=${checksum:0:8}
            local dec_part=$((16#${hex_part}))
            sum=$((sum + size + dec_part))
            log_info "Component: $(basename "$component") - Size: $size, Checksum prefix: $hex_part"
        else
            log_info "Component missing: $component"
        fi
    done
    
    echo "$sum"
}

# Validate launcher sum
validate_launcher_sum() {
    log_info "Validating K2Script KMP launcher installation..."
    
    local current_sum=$(calculate_launcher_sum)
    
    if [[ -f "$LAUNCHER_SUM_FILE" ]]; then
        local stored_sum=$(cat "$LAUNCHER_SUM_FILE")
        if [[ "$current_sum" == "$stored_sum" ]]; then
            log_success "Launcher sum validated: $current_sum"
            return 0
        else
            log_error "Launcher sum mismatch! Expected: $stored_sum, Got: $current_sum"
            return 1
        fi
    else
        log_info "First run - storing launcher sum: $current_sum"
        echo "$current_sum" > "$LAUNCHER_SUM_FILE"
        return 0
    fi
}

# Initialize FSM role state
init_role_fsm() {
    local role="$1"
    log_state "INIT" "Initializing role FSM for: $role"
    
    mkdir -p "$AGENT_SANDBOX_DIR"
    cat > "$ROLE_STATE_FILE" <<EOF
{
    "role": "$role",
    "state": "INIT",
    "started": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "transitions": []
}
EOF
}

# Transition FSM state
transition_fsm() {
    local current_state="$1"
    local next_state="${ROLE_STATES[$current_state]}"
    
    if [[ -z "$next_state" ]]; then
        log_error "Invalid state transition from: $current_state"
        return 1
    fi
    
    log_state "$current_state" "Transitioning to: $next_state"
    
    # Update state file
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    jq --arg state "$next_state" --arg ts "$timestamp" \
        '.state = $state | .transitions += [{"from": .state, "to": $state, "timestamp": $ts}]' \
        "$ROLE_STATE_FILE" > "${ROLE_STATE_FILE}.tmp" && \
        mv "${ROLE_STATE_FILE}.tmp" "$ROLE_STATE_FILE"
    
    echo "$next_state"
}

# Create sandbox environment
create_sandbox() {
    local task_id="$1"
    log_info "Creating sandbox for task: $task_id"
    
    local sandbox_dir="${AGENT_SANDBOX_DIR}/${task_id}"
    mkdir -p "$sandbox_dir"
    
    # Copy necessary files
    cp "$NVIDIA_TASKER_KTS" "$sandbox_dir/"
    
    # Create sandbox config
    cat > "$sandbox_dir/sandbox.conf" <<EOF
# K2Script Sandbox Configuration
TASK_ID=$task_id
SANDBOX_ROOT=$sandbox_dir
MEMORY_LIMIT=512M
CPU_SHARES=50
NETWORK_ALLOWED=true
FILESYSTEM_READONLY=false
EOF
    
    echo "$sandbox_dir"
}

# Launch NVIDIA tasker agent
launch_nvidia_agent() {
    local role="$1"
    local duration="$2"
    local task="$3"
    local sandbox_dir="$4"
    
    log_info "Launching NVIDIA tasker agent..."
    log_info "Role: $role, Duration: ${duration}s, Task: $task"
    
    # Build K2Script command
    local k2_cmd="k2script"
    if [[ -f "$K2SCRIPT_JAR" ]]; then
        k2_cmd="java -jar $K2SCRIPT_JAR"
    elif command -v k2script &> /dev/null; then
        k2_cmd="k2script"
    else
        log_error "K2Script not found!"
        return 1
    fi
    
    # Launch with timeout and sandbox constraints
    timeout "$duration" bash -c "
        cd '$sandbox_dir'
        export K2_SANDBOX_MODE=true
        export K2_ROLE='$role'
        export K2_TASK='$task'
        $k2_cmd nvidia-tasker-stacktrace.kts $task
    " &
    
    local agent_pid=$!
    echo "$agent_pid" > "$sandbox_dir/agent.pid"
    
    log_success "Agent launched with PID: $agent_pid"
    return 0
}

# Monitor agent execution
monitor_agent() {
    local sandbox_dir="$1"
    local duration="$2"
    local start_time=$(date +%s)
    
    log_info "Monitoring agent execution..."
    
    local pid_file="$sandbox_dir/agent.pid"
    if [[ ! -f "$pid_file" ]]; then
        log_error "Agent PID file not found!"
        return 1
    fi
    
    local agent_pid=$(cat "$pid_file")
    
    while true; do
        if ! kill -0 "$agent_pid" 2>/dev/null; then
            log_info "Agent completed"
            break
        fi
        
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        if [[ $elapsed -ge $duration ]]; then
            log_info "Duration limit reached, terminating agent"
            kill -TERM "$agent_pid" 2>/dev/null || true
            sleep 2
            kill -KILL "$agent_pid" 2>/dev/null || true
            break
        fi
        
        # Show progress
        local remaining=$((duration - elapsed))
        echo -ne "\r${CYAN}[MONITOR]${NC} Time remaining: ${remaining}s "
        
        sleep 1
    done
    
    echo # New line after progress
    return 0
}

# Cleanup sandbox
cleanup_sandbox() {
    local sandbox_dir="$1"
    log_info "Cleaning up sandbox: $sandbox_dir"
    
    if [[ -d "$sandbox_dir" ]]; then
        # Save logs before cleanup
        local logs_dir="${PROJECT_ROOT}/logs/k2-sandbox"
        mkdir -p "$logs_dir"
        local task_id=$(basename "$sandbox_dir")
        
        if [[ -f "$sandbox_dir/output.log" ]]; then
            cp "$sandbox_dir/output.log" "$logs_dir/${task_id}-output.log"
        fi
        
        # Remove sandbox
        rm -rf "$sandbox_dir"
    fi
}

# Main execution flow with FSM
execute_with_fsm() {
    local role="$1"
    local duration="$2"
    local task="$3"
    
    # Initialize FSM
    init_role_fsm "$role"
    local state="INIT"
    
    # FSM execution loop
    while [[ "$state" != "DONE" ]]; do
        case "$state" in
            "INIT")
                state=$(transition_fsm "$state")
                ;;
            
            "VALIDATING")
                if validate_launcher_sum; then
                    state=$(transition_fsm "$state")
                else
                    log_error "Validation failed!"
                    return 1
                fi
                ;;
            
            "SANDBOXING")
                local task_id="task-$(date +%s)-$$"
                local sandbox_dir=$(create_sandbox "$task_id")
                state=$(transition_fsm "$state")
                ;;
            
            "TASKING")
                if launch_nvidia_agent "$role" "$duration" "$task" "$sandbox_dir"; then
                    state=$(transition_fsm "$state")
                else
                    log_error "Failed to launch agent!"
                    return 1
                fi
                ;;
            
            "MONITORING")
                monitor_agent "$sandbox_dir" "$duration"
                state=$(transition_fsm "$state")
                ;;
            
            "COMPLETING")
                log_info "Task completed, gathering results..."
                # Could add result collection here
                state=$(transition_fsm "$state")
                ;;
            
            "CLEANUP")
                cleanup_sandbox "$sandbox_dir"
                state=$(transition_fsm "$state")
                ;;
            
            *)
                log_error "Unknown state: $state"
                return 1
                ;;
        esac
    done
    
    log_success "FSM execution completed successfully"
}

# Show usage
show_usage() {
    cat <<EOF
${CYAN}K2Script KMP Launcher with Sum Validation${NC}

${YELLOW}Usage:${NC}
    $0 <role> <task> [options]

${YELLOW}Roles:${NC}
    analyzer     - Code analysis and stacktrace processing
    generator    - Code generation and fixes
    validator    - Result validation and testing
    orchestrator - Multi-agent coordination

${YELLOW}Tasks:${NC}
    fix-stacktrace    - Process and fix compilation errors
    analyze-code      - Analyze codebase with NVIDIA AI
    generate-tests    - Generate test cases
    validate-build    - Validate build integrity

${YELLOW}Options:${NC}
    -d, --duration SECONDS   Task duration limit (default: 300s)
    -s, --sandbox DIR        Custom sandbox directory
    -v, --validate-only      Only validate launcher sum
    -h, --help               Show this help

${YELLOW}Examples:${NC}
    # Fix stacktraces for 10 minutes
    $0 analyzer fix-stacktrace --duration 600

    # Generate tests with 5 minute limit
    $0 generator generate-tests -d 300

    # Validate launcher installation
    $0 --validate-only

${YELLOW}Environment Variables:${NC}
    K2_SANDBOX_MODE     Enable sandbox restrictions
    K2_MEMORY_LIMIT     Memory limit for agents
    K2_CPU_SHARES       CPU share percentage
EOF
}

# Parse arguments
main() {
    if [[ $# -eq 0 ]]; then
        show_usage
        exit 0
    fi
    
    local role=""
    local task=""
    local duration=$DEFAULT_DURATION
    local validate_only=false
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -d|--duration)
                duration="$2"
                shift 2
                ;;
            -s|--sandbox)
                AGENT_SANDBOX_DIR="$2"
                shift 2
                ;;
            -v|--validate-only)
                validate_only=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            analyzer|generator|validator|orchestrator)
                role="$1"
                shift
                ;;
            fix-stacktrace|analyze-code|generate-tests|validate-build)
                task="$1"
                shift
                ;;
            *)
                log_error "Unknown argument: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Validate only mode
    if [[ "$validate_only" == "true" ]]; then
        validate_launcher_sum
        exit $?
    fi
    
    # Check required args
    if [[ -z "$role" || -z "$task" ]]; then
        log_error "Role and task are required!"
        show_usage
        exit 1
    fi
    
    # Execute with FSM
    log_info "K2Script KMP Launcher starting..."
    log_info "Role: $role, Task: $task, Duration: ${duration}s"
    
    execute_with_fsm "$role" "$duration" "$task"
}

# Run main
main "$@"