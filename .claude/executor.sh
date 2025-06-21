#!/bin/bash
# Executor Timer Command - Processes agent memos and adapts to situations
# Usage: Run on timer to coordinate concurrent Claude agents

set -e

# Configuration
CLAUDE_DIR="/Users/jim/work/superbikeshed/.claude"
MEMO_DIR="$CLAUDE_DIR/memos"
EXECUTOR_LOG="$CLAUDE_DIR/executor.log"
CONFLICT_QUEUE="$CLAUDE_DIR/conflicts.json"
EXECUTION_QUEUE="$CLAUDE_DIR/execution.json"
REPO_ROOT="/Users/jim/work/superbikeshed"

# Ensure directories exist
mkdir -p "$MEMO_DIR/pending" "$MEMO_DIR/processed" "$MEMO_DIR/failed"

# Logging function
log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S'): $1" >> "$EXECUTOR_LOG"
    echo "$1"
}

# Initialize conflict and execution queues if they don't exist
[[ ! -f "$CONFLICT_QUEUE" ]] && echo "[]" > "$CONFLICT_QUEUE"
[[ ! -f "$EXECUTION_QUEUE" ]] && echo "[]" > "$EXECUTION_QUEUE"

log "=== Executor cycle starting ==="

# 1. Collect and parse all pending memos
MEMO_COUNT=0
CONFLICT_COUNT=0
EXECUTED_COUNT=0

for memo in "$MEMO_DIR/pending"/*.memo; do
    if [[ -f "$memo" ]]; then
        MEMO_COUNT=$((MEMO_COUNT + 1))
        MEMO_NAME=$(basename "$memo")
        log "Processing memo: $MEMO_NAME"
        
        # Parse memo structure
        AGENT=$(grep "^Agent:" "$memo" | cut -d' ' -f2- || echo "unknown")
        SCOPE=$(grep "^Scope:" "$memo" | cut -d' ' -f2- || echo "")
        ACTION=$(grep "^Action:" "$memo" | cut -d' ' -f2- || echo "")
        DEPENDENCIES=$(grep "^Dependencies:" "$memo" | cut -d' ' -f2- || echo "none")
        RISK_LEVEL=$(grep "^Risk:" "$memo" | cut -d' ' -f2- || echo "medium")
        
        log "  Agent: $AGENT, Scope: $SCOPE, Risk: $RISK_LEVEL"
        
        # 2. Conflict detection across all pending memos
        CONFLICTS_DETECTED=false
        
        # Check for file conflicts with other pending memos
        for other_memo in "$MEMO_DIR/pending"/*.memo; do
            if [[ "$other_memo" != "$memo" && -f "$other_memo" ]]; then
                OTHER_SCOPE=$(grep "^Scope:" "$other_memo" | cut -d' ' -f2- || echo "")
                
                # Simple overlap detection (can be enhanced)
                if [[ "$SCOPE" == "$OTHER_SCOPE" ]]; then
                    log "  CONFLICT: Scope overlap with $(basename "$other_memo")"
                    CONFLICTS_DETECTED=true
                fi
            fi
        done
        
        # 3. Repository state adaptation
        cd "$REPO_ROOT"
        
        # Check if repo is in clean state
        if ! git diff --quiet; then
            log "  WARNING: Repository has uncommitted changes"
            RISK_LEVEL="high"
        fi
        
        # Check if target scope exists and is writable
        if [[ -n "$SCOPE" && ! -d "$SCOPE" ]]; then
            log "  ERROR: Target scope '$SCOPE' does not exist"
            mv "$memo" "$MEMO_DIR/failed/"
            continue
        fi
        
        # 4. Decision logic: Execute, Queue for Conflicts, or Escalate
        if [[ "$CONFLICTS_DETECTED" == "true" ]]; then
            log "  QUEUING: Conflicts detected - adding to conflict queue"
            
            # Add to conflict queue for executive review
            CONFLICT_ENTRY=$(cat <<EOF
{
  "memo": "$MEMO_NAME",
  "agent": "$AGENT",
  "scope": "$SCOPE", 
  "action": "$ACTION",
  "conflicts": "scope_overlap",
  "timestamp": "$(date -Iseconds)"
}
EOF
)
            
            # Append to conflict queue (simple JSON array)
            tmp=$(mktemp)
            jq ". += [$CONFLICT_ENTRY]" "$CONFLICT_QUEUE" > "$tmp" && mv "$tmp" "$CONFLICT_QUEUE" 2>/dev/null || {
                # Fallback if jq not available
                echo "$CONFLICT_ENTRY" >> "$CONFLICT_QUEUE"
            }
            
            mv "$memo" "$MEMO_DIR/processed/"
            CONFLICT_COUNT=$((CONFLICT_COUNT + 1))
            
        elif [[ "$RISK_LEVEL" == "high" ]]; then
            log "  ESCALATING: High risk - requires executive approval"
            
            # Add to execution queue for executive approval
            EXEC_ENTRY=$(cat <<EOF
{
  "memo": "$MEMO_NAME",
  "agent": "$AGENT", 
  "scope": "$SCOPE",
  "action": "$ACTION",
  "risk": "$RISK_LEVEL",
  "status": "awaiting_approval",
  "timestamp": "$(date -Iseconds)"
}
EOF
)
            
            tmp=$(mktemp)
            jq ". += [$EXEC_ENTRY]" "$EXECUTION_QUEUE" > "$tmp" && mv "$tmp" "$EXECUTION_QUEUE" 2>/dev/null || {
                echo "$EXEC_ENTRY" >> "$EXECUTION_QUEUE"
            }
            
            mv "$memo" "$MEMO_DIR/processed/"
            
        elif [[ "$RISK_LEVEL" == "low" ]]; then
            log "  EXECUTING: Low risk - auto-executing"
            
            # Execute the memo (placeholder - would need actual execution logic)
            if execute_memo "$memo"; then
                log "  SUCCESS: Memo executed successfully"
                mv "$memo" "$MEMO_DIR/processed/"
                EXECUTED_COUNT=$((EXECUTED_COUNT + 1))
            else
                log "  FAILED: Memo execution failed"
                mv "$memo" "$MEMO_DIR/failed/"
            fi
            
        else
            log "  QUEUING: Medium risk - adding to execution queue"
            mv "$memo" "$MEMO_DIR/processed/"
        fi
    fi
done

# 5. Generate executive summary
SUMMARY="Cycle complete: $MEMO_COUNT memos processed, $EXECUTED_COUNT executed, $CONFLICT_COUNT conflicts detected"
log "$SUMMARY"

# 6. Alert executive if attention needed
if [[ $CONFLICT_COUNT -gt 0 ]] || jq -e 'length > 0' "$CONFLICT_QUEUE" >/dev/null 2>&1; then
    log "EXECUTIVE ATTENTION REQUIRED: Conflicts need resolution"
    
    # Could send notification, create flag file, etc.
    touch "$CLAUDE_DIR/executive_attention_required"
fi

log "=== Executor cycle ending ==="

# Placeholder execution function
execute_memo() {
    local memo="$1"
    local action=$(grep "^Action:" "$memo" | cut -d' ' -f2-)
    
    log "    Executing: $action"
    
    # This would contain the actual file manipulation logic
    # For now, just simulate success
    return 0
}

exit 0