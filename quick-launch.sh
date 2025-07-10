#!/bin/bash
# QUICK LAUNCH - Just get SOMETHING running

echo "🚀 QUICK LAUNCH FIDUCIARY SERVER"
echo "================================"

# Just compile and run the minimal launcher
echo "⚡ Building launcher..."
./gradlew :platform-launcher:classes

echo ""
echo "🔥 Running server..."
./gradlew :platform-launcher:run --no-daemon --console=plain

# Alternative: Run the basic CouchDB mock server
# echo "🔥 Running basic mock server..."
# ./gradlew :trikeshed-couchdb:run --no-daemon --console=plain