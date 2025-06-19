#!/bin/bash

# Configuration
LOGS_DIR="${LOGS_DIR:-logs}"
OUTPUT_DIR="${OUTPUT_DIR:-output}"
CRITERIA_FILE="CLAUDE.md"
METRICS_FILE="${OUTPUT_DIR}/criteria/git_delta_metrics.json"

# Create necessary directories
mkdir -p "${LOGS_DIR}/criteria"
mkdir -p "${OUTPUT_DIR}/criteria"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Function to check file against criteria
check_file() {
    local file="$1"
    local status="$2"
    local output_file="${OUTPUT_DIR}/criteria/$(basename "$file").criteria.json"
    
    log "Checking $file ($status) against CLAUDE.md criteria"
    
    # Initialize criteria object
    local criteria=$(cat <<EOF
{
    "file": "$file",
    "status": "$status",
    "timestamp": "$(date '+%Y-%m-%d %H:%M:%S')",
    "violations": [],
    "compliant_patterns": []
}
EOF
)
    
    # Only check Kotlin files
    if [[ "$file" == *.kt ]]; then
        # Check for banned types
        if grep -q "List<" "$file" || grep -q "MutableList<" "$file" || grep -q "Pair<" "$file"; then
            criteria=$(echo "$criteria" | jq '.violations += ["Contains banned types (List, MutableList, Pair)"]')
        fi
        
        # Check for mandatory patterns
        if grep -q "series.α" "$file"; then
            criteria=$(echo "$criteria" | jq '.compliant_patterns += ["Uses α transform operator"]')
        fi
        
        if grep -q "series.\`▶\`" "$file"; then
            criteria=$(echo "$criteria" | jq '.compliant_patterns += ["Uses play button operator"]')
        fi
        
        if grep -q "@JvmInline value class" "$file"; then
            criteria=$(echo "$criteria" | jq '.compliant_patterns += ["Uses value class wrapper"]')
        fi
        
        # Check for banned practices
        if grep -q "println" "$file"; then
            criteria=$(echo "$criteria" | jq '.violations += ["Contains println statements"]')
        fi
        
        if grep -q "demo" "$file" || grep -q "Demo" "$file"; then
            criteria=$(echo "$criteria" | jq '.violations += ["Contains demo-related code"]')
        fi
        
        # Check for type system compliance
        if grep -q "typealias" "$file"; then
            criteria=$(echo "$criteria" | jq '.compliant_patterns += ["Uses typealias definitions"]')
        fi
    fi
    
    # Save results
    echo "$criteria" > "$output_file"
    log "Results saved to $output_file"
}

# Main execution
log "Starting criteria enforcement check"

# Check if CLAUDE.md exists
if [ ! -f "$CRITERIA_FILE" ]; then
    log "Error: $CRITERIA_FILE not found"
    exit 1
fi

# Check if metrics file exists
if [ ! -f "$METRICS_FILE" ]; then
    log "Error: $METRICS_FILE not found"
    exit 1
fi

# Process each file in the metrics
while IFS=$'\t' read -r status file; do
    # Skip empty lines
    [ -z "$file" ] && continue
    
    # Skip non-Kotlin files
    [[ "$file" != *.kt ]] && continue
    
    # Check if file exists
    if [ -f "$file" ]; then
        check_file "$file" "$status"
    fi
done < "$METRICS_FILE"

# Display summary
echo -e "\nCriteria Enforcement Summary:"
find "${OUTPUT_DIR}/criteria" -name "*.criteria.json" -type f -exec jq -r '"\(.file) (\(.status)): \(.violations | length) violations, \(.compliant_patterns | length) compliant patterns"' {} \; 