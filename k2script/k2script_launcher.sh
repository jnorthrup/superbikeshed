#!/usr/bin/env bash
set -euo pipefail

# k2script robust launcher

# Defaults
DEPLOY_DIR=""
WORKSPACE=""
RAW_MODE=false
DOCKER_MODE=false
BRANCH_NAME=""
SCRIPT_ARGS=()

# Parse args
while [[ $# -gt 0 ]]; do
    case "$1" in
        --deploy)
            DEPLOY_DIR="$2"; shift 2;;
        --cwd)
            if [[ $# -gt 1 && ! "$2" =~ ^-- ]]; then
                WORKSPACE="$2"; shift 2
            else
                WORKSPACE=""; shift
            fi;;
        --rawdog)
            RAW_MODE=true; shift;;
        --docker)
            DOCKER_MODE=true; shift;;
        --branch)
            BRANCH_NAME="$2"; shift 2;;
        --)
            shift; SCRIPT_ARGS=("$@"); break;;
        *)
            SCRIPT_ARGS+=("$1"); shift;;
    esac
done

# Deploy logic
if [[ -n "$DEPLOY_DIR" ]]; then
    echo "[k2script] Deploying payload to $DEPLOY_DIR"
    mkdir -p "$DEPLOY_DIR"
    rsync -a --exclude='.git' . "$DEPLOY_DIR"/
    echo "[k2script] Deploy complete."
    exit 0
fi

# Workspace logic
if [[ "$RAW_MODE" == true ]]; then
    echo "[k2script] Running in raw mode (no sandbox, no clone)."
    WORKDIR="$PWD"
else
    # Determine source for clone
    SRC_DIR="${DEPLOY_DIR:-$PWD}"
    # Hourly dense/hardlink logic
    HOUR=$(date +%Y%m%d-%H)
    DENSE_DIR="/tmp/k2script-dense-$HOUR"
    if [[ ! -d "$DENSE_DIR" ]]; then
        echo "[k2script] Creating dense copy for hour: $DENSE_DIR"
        rsync -a --exclude='.git' "$SRC_DIR"/ "$DENSE_DIR"/
    else
        echo "[k2script] Dense copy exists: $DENSE_DIR"
    fi
    # Create hardlink clone
    MS=$(date +%s%3N)
    BRANCH_SUFFIX="${BRANCH_NAME:+-$BRANCH_NAME}"
    WORKDIR="/tmp/k2script-workspace-$HOUR-$MS$BRANCH_SUFFIX"
    echo "[k2script] Creating hardlink clone: $WORKDIR"
    cp -al "$DENSE_DIR" "$WORKDIR"
fi

# Optionally run in Docker
if [[ "$DOCKER_MODE" == true ]]; then
    echo "[k2script] Running in Docker sandbox."
    docker run --rm -v "$WORKDIR:/workspace" -w /workspace kotlin:latest "${SCRIPT_ARGS[@]}"
    exit $?
fi

# Run script in workspace
cd "$WORKDIR"
echo "[k2script] Running in workspace: $WORKDIR"
"${SCRIPT_ARGS[@]}" 