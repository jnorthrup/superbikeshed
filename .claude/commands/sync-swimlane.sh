#!/bin/bash
# sync-swimlane.sh - Sync changes between main repository and swimlane

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check for arguments
if [ $# -lt 1 ]; then
    echo -e "${RED}Error: No branch name provided${NC}"
    echo "Usage: $0 <branch-name> [--from-main|--to-main] [module1 module2 ...]"
    echo "Examples:"
    echo "  $0 jk-kj-double-dispatch --from-main              # Sync all changes from main"
    echo "  $0 jk-kj-double-dispatch --to-main Trikeshed      # Sync Trikeshed module to main"
    echo "  $0 register-packing --from-main Trikeshed ksp-processors  # Sync specific modules from main"
    exit 1
fi

BRANCH_NAME="$1"
DIRECTION="${2:---from-main}"
shift 2 || shift 1

# Get the repository root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SWIMLANE_DIR="$REPO_ROOT/build/$BRANCH_NAME"

# Check if swimlane exists
if [ ! -d "$SWIMLANE_DIR" ]; then
    echo -e "${RED}Error: Swimlane '$BRANCH_NAME' not found at $SWIMLANE_DIR${NC}"
    echo "Run setup-swimlane.sh first to create the swimlane"
    exit 1
fi

# Check if it's a swimlane
if [ ! -f "$SWIMLANE_DIR/.swimlane" ]; then
    echo -e "${RED}Error: Directory exists but is not a swimlane${NC}"
    exit 1
fi

# Get swimlane type
SWIMLANE_TYPE=$(grep "Type:" "$SWIMLANE_DIR/.swimlane" | cut -d' ' -f2)

echo -e "${BLUE}Syncing swimlane: $BRANCH_NAME (type: $SWIMLANE_TYPE)${NC}"

# Function to sync specific modules
sync_modules() {
    local source_dir="$1"
    local target_dir="$2"
    local modules=("${@:3}")
    
    if [ ${#modules[@]} -eq 0 ]; then
        # No specific modules specified, detect changes
        echo "Detecting changed files..."
        
        cd "$source_dir"
        # Get list of changed files
        if [ "$source_dir" = "$REPO_ROOT" ]; then
            # From main repo
            changed_files=$(git diff --name-only HEAD)
        else
            # From swimlane
            changed_files=$(git diff --name-only origin/main...HEAD 2>/dev/null || git diff --name-only main...HEAD)
        fi
        
        if [ -z "$changed_files" ]; then
            echo -e "${YELLOW}No changes detected${NC}"
            return
        fi
        
        # Group by top-level directory
        modules=($(echo "$changed_files" | cut -d'/' -f1 | sort -u))
    fi
    
    echo "Modules to sync: ${modules[*]}"
    
    for module in "${modules[@]}"; do
        if [ -d "$source_dir/$module" ]; then
            echo -e "${GREEN}Syncing $module...${NC}"
            
            # Special handling for certain directories
            case "$module" in
                .gradle|build|node_modules|kotlin-js-store)
                    echo -e "${YELLOW}Skipping $module (build artifact)${NC}"
                    continue
                    ;;
                .git)
                    echo -e "${YELLOW}Skipping $module (git directory)${NC}"
                    continue
                    ;;
            esac
            
            # Create target directory if it doesn't exist
            mkdir -p "$target_dir/$module"
            
            # Sync the module
            rsync -av --delete \
                --exclude='.gradle/' \
                --exclude='build/' \
                --exclude='node_modules/' \
                --exclude='.git/' \
                --exclude='*.class' \
                --exclude='*.jar' \
                --exclude='*.klib' \
                "$source_dir/$module/" "$target_dir/$module/"
        elif [ -f "$source_dir/$module" ]; then
            echo -e "${GREEN}Copying file $module...${NC}"
            cp -v "$source_dir/$module" "$target_dir/$module"
        else
            echo -e "${YELLOW}Warning: $module not found in source${NC}"
        fi
    done
}

# Perform sync based on direction
case "$DIRECTION" in
    --from-main)
        echo -e "${BLUE}Syncing from main repository to swimlane${NC}"
        
        # If worktree, we might need to fetch latest
        if [ "$SWIMLANE_TYPE" = "worktree" ]; then
            echo "Fetching latest changes in main..."
            cd "$REPO_ROOT"
            git fetch origin
        fi
        
        sync_modules "$REPO_ROOT" "$SWIMLANE_DIR" "$@"
        
        echo
        echo -e "${GREEN}✓ Sync complete!${NC}"
        echo "Review changes in: $SWIMLANE_DIR"
        ;;
        
    --to-main)
        echo -e "${BLUE}Syncing from swimlane to main repository${NC}"
        echo -e "${YELLOW}Warning: This will modify your main repository${NC}"
        read -p "Continue? (y/N) " -n 1 -r
        echo
        
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Aborted."
            exit 1
        fi
        
        sync_modules "$SWIMLANE_DIR" "$REPO_ROOT" "$@"
        
        echo
        echo -e "${GREEN}✓ Sync complete!${NC}"
        echo "Review changes in main repository before committing"
        ;;
        
    *)
        echo -e "${RED}Error: Unknown direction '$DIRECTION'${NC}"
        echo "Use --from-main or --to-main"
        exit 1
        ;;
esac

# Show git status in target
echo
echo -e "${BLUE}Git status:${NC}"
if [ "$DIRECTION" = "--from-main" ]; then
    cd "$SWIMLANE_DIR"
else
    cd "$REPO_ROOT"
fi

git status --short