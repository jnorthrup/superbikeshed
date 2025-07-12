#!/bin/bash

# Deploy Enhanced LLM Frameworks to Subprojects
# This script deploys provider-specific frameworks with deliverable rewards to key subprojects

set -e

echo "🚀 Deploying Enhanced LLM Frameworks to Subprojects..."

# Key subprojects that need deliverable-focused frameworks
SUBPROJECTS=(
    "k2script"
    "nexus"
    "platform-launcher"
    "rtsgame"
    "moneyfan"
    "fiduciary"
    "trikeshed-lib"
    "trikeshed-json"
    "trikeshed-http"
    "trikeshed-net"
    "trikeshed-couchdb"
    "trikeshed-ipfs"
    "trikeshed-quic"
    "trikeshed-torrent"
    "trikeshed-wave"
    "trikeshed-rest"
    "trikeshed-services"
    "trikeshed-oauth"
    "trikeshed-dht"
    "trikeshed-lsmr"
    "trikeshed-uring"
    "trikeshed-sumo"
    "trikeshed-channel-api"
    "trikeshed-channel-impl"
    "trikeshed-channel-test"
    "trikeshed-cursor"
    "trikeshed-context"
    "trikeshed-ccek"
    "trikeshed-async-core"
    "trikeshed-common"
    "trikeshed-io"
    "trikeshed-ipc"
    "trikeshed-isam"
    "trikeshed-ljson"
    "trikeshed-strace"
    "trikeshed-socks"
    "SSH"
    "boingDemo"
)

# Create enhanced frameworks for each provider
create_enhanced_frameworks() {
    echo "📝 Creating enhanced frameworks with deliverable rewards..."
    
    # Copy base frameworks and enhance them
    cp .llm/pristine/claude/CLAUDE.md .llm/pristine/claude/CLAUDE_DELIVERABLE.md
    cp .llm/pristine/cursor/CURSOR.md .llm/pristine/cursor/CURSOR_DELIVERABLE.md
    cp .llm/pristine/gemini/GEMINI.md .llm/pristine/gemini/GEMINI_DELIVERABLE.md
    cp .llm/pristine/aider/AIDER.md .llm/pristine/aider/AIDER_DELIVERABLE.md
    
    echo "✅ Enhanced frameworks created"
}

# Deploy to a single subproject
deploy_to_subproject() {
    local subproject=$1
    
    if [ ! -d "$subproject" ]; then
        echo "⚠️  Subproject $subproject not found, skipping..."
        return
    fi
    
    echo "📦 Deploying to $subproject..."
    
    # Create provider directories
    mkdir -p "$subproject/.claude"
    mkdir -p "$subproject/.cursor"
    mkdir -p "$subproject/.aider"
    
    # Deploy frameworks
    cp .llm/pristine/claude/CLAUDE_DELIVERABLE.md "$subproject/.claude/CLAUDE.md"
    cp .llm/pristine/cursor/CURSOR_DELIVERABLE.md "$subproject/.cursor/CURSOR.md"
    cp .llm/pristine/gemini/GEMINI_DELIVERABLE.md "$subproject/.gemini.md"
    cp .llm/pristine/aider/AIDER_DELIVERABLE.md "$subproject/.aider/AIDER.md"
    
    # Create subproject-specific README
    cat > "$subproject/.llm-README.md" << EOF
# LLM Frameworks for $subproject

This subproject uses enhanced GoalStrikeshed Architectural Momentum System frameworks with deliverable-focused rewards.

## Available Frameworks
- **Claude**: \`.claude/CLAUDE.md\` - Architectural depth and mathematical rigor
- **Cursor**: \`.cursor/CURSOR.md\` - IDE integration and context awareness  
- **Gemini**: \`.gemini.md\` - Large context analysis and minimum mutation effects
- **Aider**: \`.aider/AIDER.md\` - Iterative refinement and git integration

## Deliverable Rewards
- Production Readiness: +30 points
- Integration Success: +25 points
- Performance Delivery: +28 points
- Quality Assurance: +22 points
- User Experience: +24 points
- System Reliability: +26 points

## Usage
Each framework is optimized for its provider's strengths while maintaining the core architectural momentum principles.
EOF
    
    echo "✅ Deployed to $subproject"
}

# Main deployment
main() {
    echo "🎯 Starting subproject framework deployment..."
    
    # Create enhanced frameworks
    create_enhanced_frameworks
    
    # Deploy to each subproject
    for subproject in "${SUBPROJECTS[@]}"; do
        deploy_to_subproject "$subproject"
    done
    
    echo "🎉 Deployment complete!"
    echo "📊 Deployed to ${#SUBPROJECTS[@]} subprojects"
    echo "🔧 Each subproject now has deliverable-focused LLM frameworks"
}

# Run main function
main "$@" 