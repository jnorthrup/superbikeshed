#!/bin/bash
# setup-swimlane.sh - Create an isolated build environment for a specific branch/feature

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check for branch name argument
if [ $# -eq 0 ]; then
    echo -e "${RED}Error: No branch name provided${NC}"
    echo "Usage: $0 <branch-name> [--worktree]"
    echo "Example: $0 jk-kj-double-dispatch"
    echo "Options:"
    echo "  --worktree    Use git worktree instead of full clone (faster)"
    exit 1
fi

BRANCH_NAME="$1"
USE_WORKTREE=false

# Check for worktree flag
if [ "${2:-}" == "--worktree" ]; then
    USE_WORKTREE=true
fi

# Get the repository root
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="$REPO_ROOT/build"
SWIMLANE_DIR="$BUILD_DIR/$BRANCH_NAME"

# Check if swimlane already exists
if [ -d "$SWIMLANE_DIR" ]; then
    echo -e "${YELLOW}Warning: Swimlane '$BRANCH_NAME' already exists at $SWIMLANE_DIR${NC}"
    read -p "Do you want to remove it and create a fresh one? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Removing existing swimlane..."
        rm -rf "$SWIMLANE_DIR"
    else
        echo "Aborting."
        exit 1
    fi
fi

# Create build directory if it doesn't exist
mkdir -p "$BUILD_DIR"

echo -e "${GREEN}Setting up swimlane: $BRANCH_NAME${NC}"

if [ "$USE_WORKTREE" = true ]; then
    # Use git worktree for faster setup
    echo "Creating git worktree..."
    cd "$REPO_ROOT"
    
    # Check if branch exists
    if git show-ref --verify --quiet refs/heads/"$BRANCH_NAME"; then
        git worktree add "$SWIMLANE_DIR" "$BRANCH_NAME"
    else
        # Create new branch
        git worktree add -b "$BRANCH_NAME" "$SWIMLANE_DIR"
    fi
else
    # Full clone for complete isolation
    echo "Cloning repository..."
    git clone "$REPO_ROOT" "$SWIMLANE_DIR"
    
    cd "$SWIMLANE_DIR"
    
    # Check if branch exists on remote
    if git ls-remote --heads origin "$BRANCH_NAME" | grep -q "$BRANCH_NAME"; then
        git checkout "$BRANCH_NAME"
    else
        # Create new branch
        git checkout -b "$BRANCH_NAME"
    fi
fi

# Create swimlane-specific gradle.properties if needed
if [ ! -f "$SWIMLANE_DIR/gradle.properties" ]; then
    echo "# Swimlane-specific gradle properties" > "$SWIMLANE_DIR/gradle.properties"
    echo "org.gradle.caching=true" >> "$SWIMLANE_DIR/gradle.properties"
    echo "org.gradle.parallel=true" >> "$SWIMLANE_DIR/gradle.properties"
    echo "org.gradle.daemon=false" >> "$SWIMLANE_DIR/gradle.properties"
fi

# Create a marker file to identify this as a swimlane
echo "$BRANCH_NAME" > "$SWIMLANE_DIR/.swimlane"
echo "Created: $(date)" >> "$SWIMLANE_DIR/.swimlane"
echo "Type: $([ "$USE_WORKTREE" = true ] && echo "worktree" || echo "clone")" >> "$SWIMLANE_DIR/.swimlane"

# Create local build script
cat > "$SWIMLANE_DIR/build-local.sh" << 'EOF'
#!/bin/bash
# Local build script for this swimlane
set -e

echo "Building in swimlane: $(basename $(pwd))"
./gradlew clean build --console=plain --no-daemon
EOF

chmod +x "$SWIMLANE_DIR/build-local.sh"

echo -e "${GREEN}✓ Swimlane setup complete!${NC}"
echo
echo "Swimlane location: $SWIMLANE_DIR"
echo
echo "Next steps:"
echo "  cd $SWIMLANE_DIR"
echo "  ./build-local.sh      # Run isolated build"
echo
echo "To sync changes:"
echo "  $REPO_ROOT/.claude/commands/sync-swimlane.sh $BRANCH_NAME"