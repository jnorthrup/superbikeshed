#!/bin/bash

# Configuration
LLM_API_KEY="${LLM_API_KEY:-}"
LLM_API_ENDPOINT="${LLM_API_ENDPOINT:-https://api.llm-service.com/v1/chat/completions}"
LLM_MODEL="${LLM_MODEL:-default-model}"

# Validate environment
if [ -z "$LLM_API_KEY" ]; then
  echo "Error: LLM_API_KEY not set in environment."
  exit 1
fi

invoke_llm() {
  local task="$1"
  local input_file="$2"
  
  # Read input file content
  local input_content
  if [ -f "$input_file" ]; then
    input_content=$(cat "$input_file")
  else
    input_content="No input file provided"
  fi
  
  # Make API request
  curl "$LLM_API_ENDPOINT" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $LLM_API_KEY" \
    -d "{
      \"model\": \"$LLM_MODEL\",
      \"messages\": [
        {\"role\": \"system\", \"content\": \"$task\"},
        {\"role\": \"user\", \"content\": \"$input_content\"}
      ],
      \"temperature\": 0.6,
      \"top_p\": 0.95,
      \"max_tokens\": 4096,
      \"frequency_penalty\": 0,
      \"presence_penalty\": 0,
      \"stream\": true
    }" > "output_$task.txt"
  
  echo "LLM Task '$task' Completed. Output: output_$task.txt"
}

# Main execution
if [ $# -lt 2 ]; then
  echo "Usage: $0 <task> <input_file>"
  exit 1
fi

invoke_llm "$1" "$2"