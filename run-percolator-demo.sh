#!/bin/bash

# Run Percolator Demo
# This script starts both the coordinator server and a volunteer node

echo "╔════════════════════════════════════════════╗"
echo "║        PERCOLATOR DEMO LAUNCHER            ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "This will start:"
echo "1. Coordinator server on port 8888"
echo "2. Sample volunteer node"
echo ""

# Build the project first
echo "📦 Building percolator components..."
./gradlew :fiduciary:build -q

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "🛑 Shutting down percolator demo..."
    kill $COORDINATOR_PID 2>/dev/null
    kill $NODE_PID 2>/dev/null
    exit 0
}

# Register cleanup function
trap cleanup EXIT INT TERM

# Start coordinator server
echo "🌊 Starting coordinator server..."
java -cp fiduciary/build/libs/fiduciary-jvm.jar fiduciary.percolator.PercolatorCoordinatorServerKt &
COORDINATOR_PID=$!

# Wait for coordinator to start
echo "⏳ Waiting for coordinator to initialize..."
sleep 5

# Check if coordinator is running
if ! kill -0 $COORDINATOR_PID 2>/dev/null; then
    echo "❌ Failed to start coordinator server"
    exit 1
fi

echo "✅ Coordinator running at http://localhost:8888"
echo ""

# Start volunteer node
echo "🤖 Starting volunteer node..."
java -cp fiduciary/build/libs/fiduciary-jvm.jar fiduciary.percolator.RunPercolatorNodeKt --coordinator=http://localhost:8888 --max-concurrent=3 &
NODE_PID=$!

# Wait for node to connect
sleep 3

# Check if node is running
if ! kill -0 $NODE_PID 2>/dev/null; then
    echo "❌ Failed to start volunteer node"
    exit 1
fi

echo "✅ Volunteer node connected"
echo ""
echo "════════════════════════════════════════════"
echo "🎯 PERCOLATOR NETWORK RUNNING"
echo "════════════════════════════════════════════"
echo ""
echo "Dashboard: http://localhost:8888"
echo "API: http://localhost:8888/api/v1"
echo ""
echo "The volunteer node will:"
echo "- Claim work units from coordinator"
echo "- Extract content via range requests"
echo "- Process through NLP pipeline"
echo "- Submit results back to network"
echo ""
echo "Press Ctrl+C to stop the demo"
echo ""

# Keep script running
wait