#!/bin/bash

# GoalStrikeshed LLM Framework Setup Script
# Deploys the appropriate compliance framework to the project root

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

echo "🎯 GoalStrikeshed LLM Framework Setup"
echo "====================================="
echo "Project Root: $PROJECT_ROOT"
echo ""

# Function to deploy framework
deploy_framework() {
    local framework=$1
    local source_file=$2
    local target_file=$3
    
    echo "📦 Deploying $framework framework..."
    
    if [ -f "$source_file" ]; then
        cp "$source_file" "$target_file"
        echo "✅ $framework framework deployed to $target_file"
    else
        echo "❌ Source file not found: $source_file"
        return 1
    fi
}

# Function to backup existing file
backup_file() {
    local file=$1
    if [ -f "$file" ]; then
        local backup="${file}.backup.$(date +%Y%m%d_%H%M%S)"
        cp "$file" "$backup"
        echo "💾 Backed up existing file to $backup"
    fi
}

# Main deployment logic
case "${1:-}" in
    "claude")
        backup_file "$PROJECT_ROOT/CLAUDE.md"
        deploy_framework "Claude" "$SCRIPT_DIR/claude/CLAUDE.md" "$PROJECT_ROOT/CLAUDE.md"
        ;;
    "gemini")
        backup_file "$PROJECT_ROOT/.gemini.md"
        deploy_framework "Gemini" "$SCRIPT_DIR/gemini/GEMINI.md" "$PROJECT_ROOT/.gemini.md"
        ;;
    "cursor")
        backup_file "$PROJECT_ROOT/.cursorrules"
        deploy_framework "Cursor" "$SCRIPT_DIR/cursor/CURSOR.md" "$PROJECT_ROOT/.cursorrules"
        ;;
    "aider")
        backup_file "$PROJECT_ROOT/.aider.md"
        deploy_framework "Aider" "$SCRIPT_DIR/aider/AIDER.md" "$PROJECT_ROOT/.aider.md"
        ;;
    "all")
        echo "🚀 Deploying all frameworks..."
        backup_file "$PROJECT_ROOT/CLAUDE.md"
        deploy_framework "Claude" "$SCRIPT_DIR/claude/CLAUDE.md" "$PROJECT_ROOT/CLAUDE.md"
        backup_file "$PROJECT_ROOT/.gemini.md"
        deploy_framework "Gemini" "$SCRIPT_DIR/gemini/GEMINI.md" "$PROJECT_ROOT/.gemini.md"
        backup_file "$PROJECT_ROOT/.cursorrules"
        deploy_framework "Cursor" "$SCRIPT_DIR/cursor/CURSOR.md" "$PROJECT_ROOT/.cursorrules"
        backup_file "$PROJECT_ROOT/.aider.md"
        deploy_framework "Aider" "$SCRIPT_DIR/aider/AIDER.md" "$PROJECT_ROOT/.aider.md"
        ;;
    *)
        echo "Usage: $0 {claude|gemini|cursor|aider|all}"
        echo ""
        echo "Options:"
        echo "  claude   - Deploy Claude framework (CLAUDE.md)"
        echo "  gemini   - Deploy Gemini framework (.gemini.md)"
        echo "  cursor   - Deploy Cursor framework (.cursorrules)"
        echo "  aider    - Deploy Aider framework (.aider.md)"
        echo "  all      - Deploy all frameworks"
        echo ""
        echo "Examples:"
        echo "  $0 claude    # Deploy Claude framework"
        echo "  $0 all       # Deploy all frameworks"
        exit 1
        ;;
esac

echo ""
echo "🎉 Framework deployment complete!"
echo "📖 See .llm/pristine/README.md for usage instructions" 