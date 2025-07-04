#!/bin/sh
# Launch a privileged Alpine Linux Docker container for kernel/eBPF/io_uring dev
# Mounts the current workspace and installs all required tools

set -e

WORKSPACE_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

# Pull latest Alpine image
if ! docker image inspect alpine:latest >/dev/null 2>&1; then
  docker pull alpine:latest
fi

docker run --rm -it \
  --privileged \
  --cap-add=SYS_ADMIN \
  --cap-add=SYS_PTRACE \
  --security-opt seccomp=unconfined \
  -v "$WORKSPACE_DIR:/workspace" \
  -w /workspace \
  alpine:latest \
  sh -c '
    apk add --no-cache build-base linux-headers clang llvm bpftool iproute2 git && \
    echo "[INFO] Alpine kernel dev environment ready. Your code is in /workspace." && \
    sh' 