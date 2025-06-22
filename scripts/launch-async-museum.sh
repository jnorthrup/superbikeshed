#!/bin/bash
set -euo pipefail

# Launch Async Museum Reviews - Real beneficial code integration
# Spawns concurrent workers for actual museum artifact processing

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAX_WORKERS=3

# Museum keywords mapped to their likely artifact locations
declare -A MUSEUM_KEYWORDS=(
    ["reactor"]="museum/Review/trikeshed-core museum/reactor"
    ["ljson"]="museum/parse museum/unstable_features"
    ["couchdb"]="museum/unstable_features/services museum/couchdb-admin"
    ["threads"]="museum/Review/trikeshed-core museum/threads"
    ["gztool"]="museum/gztool"
    ["http2"]="museum/http2"
    ["http_1_1"]="museum/http_11"
    ["zran"]="museum/zran"
    ["relaxfactory"]="museum/relaxfactory"
    ["ccek"]="museum/unstable_features"
    ["ipfs"]="museum/unstable_features"
    ["quic"]="museum/unstable_features"
)

cd "${PROJECT_ROOT}"
mkdir -p tmpdir museum

# Launch log
LAUNCH_LOG="museum/async-launch-${TIMESTAMP}.md"
cat > "${LAUNCH_LOG}" << EOF
# Async Museum Launch ${TIMESTAMP}

Real beneficial code integration from museum artifacts.

## Workers Launched

EOF

echo "[LAUNCHER] Starting async museum workers at ${TIMESTAMP}"
echo "[LAUNCHER] Max concurrent workers: ${MAX_WORKERS}"

# Track active workers
declare -a WORKER_PIDS=()

# Launch workers concurrently
worker_id=1
for keyword in "${!MUSEUM_KEYWORDS[@]}"; do
    # Wait for available slot
    while [[ ${#WORKER_PIDS[@]} -ge ${MAX_WORKERS} ]]; do
        sleep 1
        
        # Check for completed workers
        local new_pids=()
        for pid in "${WORKER_PIDS[@]}"; do
            if kill -0 "${pid}" 2>/dev/null; then
                new_pids+=("${pid}")
            else
                wait "${pid}"
                echo "[LAUNCHER] Worker ${pid} completed"
            fi
        done
        WORKER_PIDS=("${new_pids[@]}")
    done
    
    # Launch worker
    echo "[LAUNCHER] Launching worker ${worker_id} for ${keyword}"
    echo "- Worker ${worker_id}: ${keyword} (PID pending)" >> "${LAUNCH_LOG}"
    
    (
        export KEYWORD="${keyword}"
        export WORKER_ID="${worker_id}"
        export TIMESTAMP="${TIMESTAMP}"
        
        cd "${PROJECT_ROOT}"
        ./scripts/async-museum-worker.sh
    ) &
    
    local worker_pid=$!
    WORKER_PIDS+=("${worker_pid}")
    
    echo "[LAUNCHER] Worker ${worker_id} started with PID ${worker_pid} for ${keyword}"
    
    ((worker_id++))
    sleep 1  # Stagger launches
done

# Wait for all workers to complete
echo "[LAUNCHER] Waiting for all workers to complete..."
for pid in "${WORKER_PIDS[@]}"; do
    wait "${pid}"
    echo "[LAUNCHER] Worker ${pid} finished"
done

# Summary
echo "[LAUNCHER] All async museum workers completed"

echo "" >> "${LAUNCH_LOG}"
echo "## Results Summary" >> "${LAUNCH_LOG}"
echo "- Launch time: ${TIMESTAMP}" >> "${LAUNCH_LOG}"
echo "- Keywords processed: ${#MUSEUM_KEYWORDS[@]}" >> "${LAUNCH_LOG}"
echo "- Max concurrent workers: ${MAX_WORKERS}" >> "${LAUNCH_LOG}"

# List created branches
echo "- Branches created:" >> "${LAUNCH_LOG}"
git branch | grep "museum-review-.*-${TIMESTAMP}" | while read -r branch; do
    echo "  * ${branch}" >> "${LAUNCH_LOG}"
done

# List worker transcripts
echo "- Worker transcripts:" >> "${LAUNCH_LOG}"
find museum -name "worker-*-${TIMESTAMP}.md" 2>/dev/null | while read -r transcript; do
    if [[ -n "${transcript}" ]]; then
        echo "  * ${transcript}" >> "${LAUNCH_LOG}"
    fi
done

echo "[LAUNCHER] Launch summary saved to: ${LAUNCH_LOG}"
echo "[LAUNCHER] Check individual worker transcripts for detailed results"
echo "[LAUNCHER] All museum review branches preserved and ready for evaluation"