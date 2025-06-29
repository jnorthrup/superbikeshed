#!/bin/bash

# Load environment variables if .env exists
if [ -f .env ]; then
    source .env
fi

# Configuration
MODELS_DIR="${MODELS_DIR:-config/models}"
LOGS_DIR="${LOGS_DIR:-logs}"
OUTPUT_DIR="${OUTPUT_DIR:-output}"
METRICS_FILE="${OUTPUT_DIR}/metrics/model_metrics.json"

# Create necessary directories
mkdir -p "${LOGS_DIR}/metrics"
mkdir -p "${OUTPUT_DIR}/metrics"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# Function to measure model response time and metrics
measure_model_response() {
    local model_id="$1"
    local prompt="$2"
    local start_time=$(date +%s.%N)
    
    # Make API request and capture response
    local response=$(curl -s -w "\n%{time_total}\n%{time_connect}\n%{time_starttransfer}\n%{http_code}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${GOOGLE_API_KEY}" \
        -d "{
            \"contents\": [{
                \"parts\": [{
                    \"text\": \"$prompt\"
                }]
            }]
        }" \
        "https://generativelanguage.googleapis.com/v1beta/models/${model_id}:generateContent" 2>/dev/null)
    
    local end_time=$(date +%s.%N)
    local total_time=$(echo "$end_time - $start_time" | bc)
    
    # Extract timing information
    local curl_time=$(echo "$response" | tail -n 4 | head -n 1)
    local http_code=$(echo "$response" | tail -n 1)
    
    # Extract JSON response (excluding timing info)
    local json_response=$(echo "$response" | sed '$d' | sed '$d' | sed '$d' | sed '$d')
    
    # Validate JSON response
    if ! echo "$json_response" | jq . >/dev/null 2>&1; then
        json_response="{}"
    fi
    
    # Create metrics object
    local metrics=$(cat <<EOF
{
    "timestamp": "$(date '+%Y-%m-%d %H:%M:%S')",
    "status_code": $http_code,
    "total_time": $total_time,
    "curl_time": $curl_time,
    "response": $json_response
}
EOF
)
    
    echo "$metrics"
}

# Function to update metrics file
update_metrics() {
    local model_id="$1"
    local metrics="$2"
    
    # Create or update metrics file
    if [ ! -f "$METRICS_FILE" ]; then
        echo "{\"models\":{}}" > "$METRICS_FILE"
    fi
    
    # Update metrics using jq
    local temp_file=$(mktemp)
    if ! jq --arg model "$model_id" --argjson metrics "$metrics" \
        '.models[$model] += [$metrics]' "$METRICS_FILE" > "$temp_file" 2>/dev/null; then
        log "Error updating metrics for $model_id"
        rm "$temp_file"
        return 1
    fi
    mv "$temp_file" "$METRICS_FILE"
}

# Function to calculate and display stats
calculate_stats() {
    local model_id="$1"
    
    if [ ! -f "$METRICS_FILE" ]; then
        echo "No metrics available for $model_id"
        return
    fi
    
    # Calculate stats using jq
    local stats=$(jq --arg model "$model_id" '
        .models[$model] | 
        if length > 0 then
            {
                "total_requests": length,
                "success_rate": (map(select(.status_code == 200)) | length) / length * 100,
                "avg_response_time": (map(.total_time) | add) / length,
                "min_response_time": (map(.total_time) | min),
                "max_response_time": (map(.total_time) | max)
            }
        else
            {"error": "No data available"}
        end
    ' "$METRICS_FILE" 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        echo "$stats"
    else
        echo "{\"error\": \"Failed to calculate stats\"}"
    fi
}

# Function to run benchmark
run_benchmark() {
    local prompt="$1"
    shift
    local models=("$@")
    
    log "Starting benchmark with prompt: $prompt"
    
    for model in "${models[@]}"; do
        log "Testing model: $model"
        local metrics=$(measure_model_response "$model" "$prompt")
        if [ $? -eq 0 ]; then
            update_metrics "$model" "$metrics"
            log "Stats for $model:"
            calculate_stats "$model"
        else
            log "Error testing model: $model"
        fi
    done
    
    echo -e "\nFinal Summary:"
    for model in "${models[@]}"; do
        echo "=== $model ==="
        calculate_stats "$model"
        echo
    done
}

# Check if prompt and models are provided
if [ $# -lt 2 ]; then
    echo "Usage: $0 <prompt> <model_id1> [model_id2 ...]"
    exit 1
fi

# Run benchmark with provided prompt and models
run_benchmark "$@" 