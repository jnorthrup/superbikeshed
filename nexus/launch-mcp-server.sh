#!/bin/bash

# Launch script for Stratified MCP Hosting Server
# This script sets up the environment and launches both native and JVM components

set -e  # Exit on error

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

echo "🚀 MCP Server Launch Script"
echo "=========================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java not found. Please install Java 17 or later."
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        log_error "Java 17 or later required. Found Java $JAVA_VERSION"
        exit 1
    fi
    log_info "✅ Java $JAVA_VERSION found"
    
    # Check Kotlin native binary
    NATIVE_BINARY="$PROJECT_ROOT/platform-launcher/build/bin/native/releaseExecutable/platform-launcher.kexe"
    if [ ! -f "$NATIVE_BINARY" ]; then
        log_warn "Native binary not found. Building..."
        cd "$PROJECT_ROOT"
        ./gradlew :platform-launcher:linkReleaseExecutableNative || {
            log_error "Failed to build native binary"
            exit 1
        }
    fi
    log_info "✅ Native binary found"
    
    # Check shared memory
    if [ ! -d "/dev/shm" ]; then
        log_error "Shared memory (/dev/shm) not available"
        exit 1
    fi
    log_info "✅ Shared memory available"
    
    # Check ports
    for port in 8000 8001 8002 8003 9876; do
        if lsof -i :$port &> /dev/null; then
            log_warn "Port $port is already in use"
        fi
    done
}

# Clean up previous runs
cleanup() {
    log_info "Cleaning up previous runs..."
    
    # Remove shared memory files
    rm -f /dev/shm/uring_mcp_ccek 2>/dev/null || true
    
    # Kill any existing processes
    pkill -f "platform-launcher" 2>/dev/null || true
    pkill -f "StratifiedMcpHostingServer" 2>/dev/null || true
    
    # Wait a bit for processes to exit
    sleep 1
}

# Set up environment
setup_environment() {
    log_info "Setting up environment..."
    
    # IntelliJ integration
    if [ -z "$INTELLIJ_HOME" ]; then
        # Try to find IntelliJ
        if [ -d "/Applications/IntelliJ IDEA.app" ]; then
            export INTELLIJ_HOME="/Applications/IntelliJ IDEA.app/Contents"
        elif [ -d "$HOME/.local/share/JetBrains/Toolbox/apps/IDEA-U" ]; then
            export INTELLIJ_HOME="$HOME/.local/share/JetBrains/Toolbox/apps/IDEA-U"
        else
            log_warn "INTELLIJ_HOME not set. IntelliJ integration may not work."
        fi
    fi
    
    # Java options
    export JAVA_OPTS="-Xms512m -Xmx4g -XX:+UseG1GC"
    
    # Native options
    export URING_MCP_DEBUG=1
    export RUST_LOG=debug
    
    # Create log directory
    mkdir -p "$PROJECT_ROOT/logs"
}

# Launch native host
launch_native_host() {
    log_info "Launching native io_uring host..."
    
    NATIVE_LOG="$PROJECT_ROOT/logs/native-host.log"
    
    "$NATIVE_BINARY" > "$NATIVE_LOG" 2>&1 &
    NATIVE_PID=$!
    
    log_info "Native host started with PID: $NATIVE_PID"
    
    # Wait for native host to be ready
    local attempts=0
    while [ $attempts -lt 30 ]; do
        if [ -e "/dev/shm/uring_mcp_ccek" ]; then
            log_info "✅ Native host is ready"
            return 0
        fi
        
        # Check if process is still running
        if ! kill -0 $NATIVE_PID 2>/dev/null; then
            log_error "Native host died. Check $NATIVE_LOG"
            tail -20 "$NATIVE_LOG"
            exit 1
        fi
        
        sleep 1
        attempts=$((attempts + 1))
        
        if [ $((attempts % 5)) -eq 0 ]; then
            log_info "Waiting for native host... ($attempts/30)"
        fi
    done
    
    log_error "Native host failed to start in time"
    exit 1
}

# Launch JVM server
launch_jvm_server() {
    log_info "Launching JVM stratified server..."
    
    cd "$PROJECT_ROOT"
    
    # Build if needed
    if [ ! -f "nexus/build/libs/nexus.jar" ]; then
        log_info "Building Nexus..."
        ./gradlew :nexus:build || {
            log_error "Build failed"
            exit 1
        }
    fi
    
    JVM_LOG="$PROJECT_ROOT/logs/jvm-server.log"
    
    java $JAVA_OPTS \
        -cp "nexus/build/libs/*:platform-launcher/build/libs/*" \
        nexus.mcp.DebuggedMcpLauncher \
        > "$JVM_LOG" 2>&1 &
    
    JVM_PID=$!
    log_info "JVM server started with PID: $JVM_PID"
    
    # Monitor both processes
    monitor_processes
}

# Monitor processes
monitor_processes() {
    log_info ""
    log_info "🎯 MCP Server Running"
    log_info "===================="
    log_info "Native host PID: $NATIVE_PID"
    log_info "JVM server PID:  $JVM_PID"
    log_info ""
    log_info "Logs:"
    log_info "  Native: $PROJECT_ROOT/logs/native-host.log"
    log_info "  JVM:    $PROJECT_ROOT/logs/jvm-server.log"
    log_info ""
    log_info "Press Ctrl+C to stop"
    log_info ""
    
    # Trap shutdown
    trap shutdown INT TERM
    
    # Monitor loop
    while true; do
        # Check native host
        if ! kill -0 $NATIVE_PID 2>/dev/null; then
            log_error "Native host died!"
            shutdown
            exit 1
        fi
        
        # Check JVM server
        if ! kill -0 $JVM_PID 2>/dev/null; then
            log_error "JVM server died!"
            shutdown
            exit 1
        fi
        
        sleep 5
    done
}

# Shutdown handler
shutdown() {
    log_info ""
    log_info "Shutting down..."
    
    # Kill JVM first (graceful)
    if [ ! -z "$JVM_PID" ]; then
        kill $JVM_PID 2>/dev/null || true
        log_info "Sent TERM to JVM server"
    fi
    
    # Give it time to cleanup
    sleep 2
    
    # Kill native host
    if [ ! -z "$NATIVE_PID" ]; then
        kill $NATIVE_PID 2>/dev/null || true
        log_info "Sent TERM to native host"
    fi
    
    # Force kill if needed
    sleep 2
    kill -9 $JVM_PID 2>/dev/null || true
    kill -9 $NATIVE_PID 2>/dev/null || true
    
    # Cleanup
    rm -f /dev/shm/uring_mcp_ccek 2>/dev/null || true
    
    log_info "✅ Shutdown complete"
}

# Main execution
main() {
    check_prerequisites
    cleanup
    setup_environment
    launch_native_host
    launch_jvm_server
}

# Run main
main