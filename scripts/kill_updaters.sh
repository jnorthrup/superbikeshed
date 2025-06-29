#!/bin/bash

# Script to find and kill apps with pending updates
# Usage: ./kill_updaters.sh [app_name_pattern]

echo "🔍 Finding apps with pending updates..."

# Common update-related processes
UPDATE_PROCESSES=(
    "Updater"
    "Helper"
    "Agent"
    "Background"
    "Service"
    "Daemon"
)

# If app name provided, focus on that
if [ "$1" ]; then
    echo "🎯 Focusing on: $1"
    APP_PATTERN="$1"
else
    echo "🔍 Checking all processes..."
    APP_PATTERN=""
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Find and display processes
for pattern in "${UPDATE_PROCESSES[@]}"; do
    if [ "$APP_PATTERN" ]; then
        PROCS=$(ps aux | grep -i "$APP_PATTERN" | grep -i "$pattern" | grep -v grep)
    else
        PROCS=$(ps aux | grep -i "$pattern" | grep -v grep | head -20)
    fi
    
    if [ "$PROCS" ]; then
        echo "🔴 Found $pattern processes:"
        echo "$PROCS" | awk '{printf "   PID: %-8s User: %-10s Command: %s\n", $2, $1, substr($0, index($0,$11))}'
        echo
    fi
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🤔 Kill these processes? (y/N)"
read -r response

if [[ "$response" =~ ^[Yy]$ ]]; then
    echo "💀 Killing processes..."
    
    for pattern in "${UPDATE_PROCESSES[@]}"; do
        if [ "$APP_PATTERN" ]; then
            PIDS=$(ps aux | grep -i "$APP_PATTERN" | grep -i "$pattern" | grep -v grep | awk '{print $2}')
        else
            PIDS=$(ps aux | grep -i "$pattern" | grep -v grep | awk '{print $2}' | head -20)
        fi
        
        for pid in $PIDS; do
            if [ "$pid" ]; then
                echo "   Killing PID $pid..."
                kill -TERM "$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null
            fi
        done
    done
    
    echo "✅ Done! Try restarting your app now."
else
    echo "❌ Cancelled"
fi

echo
echo "🔧 Additional commands you can try:"
echo "   sudo pkill -f Updater"
echo "   sudo pkill -f Helper"
echo "   killall 'App Name'"