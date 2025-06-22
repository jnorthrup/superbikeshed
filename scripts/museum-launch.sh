#!/bin/bash
set -euo pipefail

# Museum Launch Script
# Generates and runs concurrent museum reviews from template

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="${PROJECT_ROOT}/scripts"
TEMPLATE="${SCRIPT_DIR}/museum-review-template.sh"
MAX_CONCURRENT=3

# Museum keywords to process
KEYWORDS=(
    "gztool"
    "http2" 
    "http_1_1"
    "reactor"
    "relaxfactory"
    "threads"
    "zran"
    "ccek"
    "couchdb"
    "ipfs"
    "quic"
    "ljson"
)

cd "${PROJECT_ROOT}"

# Ensure required directories exist
mkdir -p museum tmpdir

# Function to log with timestamp
log() {
    echo "[LAUNCHER] [$(date '+%H:%M:%S')] $*"
}

log "=== Starting Museum Concurrent Review ==="
log "Timestamp: ${TIMESTAMP}"
log "Keywords: ${KEYWORDS[*]}"
log "Max concurrent: ${MAX_CONCURRENT}"

# Array to track background processes
declare -a ACTIVE_PIDS=()

# Function to wait for available slot
wait_for_slot() {
    while [[ ${#ACTIVE_PIDS[@]} -ge ${MAX_CONCURRENT} ]]; do
        sleep 1
        
        # Check for completed processes
        local new_pids=()
        for pid in "${ACTIVE_PIDS[@]}"; do
            if kill -0 "${pid}" 2>/dev/null; then
                new_pids+=("${pid}")
            else
                wait "${pid}"
                local exit_code=$?
                log "Process ${pid} completed with exit code: ${exit_code}"
            fi
        done
        ACTIVE_PIDS=("${new_pids[@]}")
    done
}

# Function to start keyword review
start_keyword_review() {
    local keyword="$1"
    
    log "Starting review for keyword: ${keyword}"
    
    # Run template in background with environment variables
    (
        export KEYWORD="${keyword}"
        export TIMESTAMP="${TIMESTAMP}"
        bash "${TEMPLATE}"
    ) &
    
    local pid=$!
    ACTIVE_PIDS+=("${pid}")
    log "Started ${keyword} review with PID: ${pid}"
    
    return 0
}

# Process keywords with concurrency control
for keyword in "${KEYWORDS[@]}"; do
    wait_for_slot
    start_keyword_review "${keyword}"
    
    # Small delay between launches
    sleep 2
done

# Wait for all remaining processes
log "Waiting for all keyword reviews to complete..."
for pid in "${ACTIVE_PIDS[@]}"; do
    wait "${pid}"
    local exit_code=$?
    log "Process ${pid} final exit code: ${exit_code}"
done

# List created branches
log "=== Review Summary ==="
log "Created branches:"
git branch | grep "museum-review-.*-${TIMESTAMP}" | while read -r branch; do
    log "  - ${branch}"
done

# List transcripts
log "Generated transcripts:"
find museum -name "transcript-*-${TIMESTAMP}.md" 2>/dev/null | while read -r transcript; do
    if [[ -n "${transcript}" ]]; then
        local lines=$(wc -l < "${transcript}" 2>/dev/null || echo "0")
        log "  - ${transcript} (${lines} lines)"
    fi
done

log "Museum concurrent review completed"
log "Use scripts/museum-cleanup.sh summary to see results"