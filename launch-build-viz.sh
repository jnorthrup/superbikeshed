#!/bin/bash

echo "🚀 GRADLE BUILD ERROR VISUALIZER 🚀"
echo "==================================="
echo

# Run the build and create dashboard
python3 build-error-dashboard.py

echo
echo "📊 Dashboard launched in your browser!"
echo "📁 Files created:"
echo "   - build-error-dashboard.html (interactive dashboard)"
echo "   - build-stacktrace.log (full build output)"
echo
echo "✨ Refresh the page to see live updates after fixing errors!"