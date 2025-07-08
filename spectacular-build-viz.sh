#!/bin/bash

echo "🎆 SPECTACULAR BUILD ERROR VISUALIZER 🎆"
echo "======================================="
echo

# Check for Python dependencies
echo "📦 Checking dependencies..."
pip install pandas matplotlib seaborn numpy > /dev/null 2>&1

# Run the visualization
echo "🚀 Launching spectacular visualization..."
python3 analyze-build-errors-viz.py

# Open the generated images if on macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo "🖼️  Opening visualizations..."
    open build-error-analysis.png 2>/dev/null
    open error-sunburst.png 2>/dev/null
    open error-3d.png 2>/dev/null
fi

echo "✨ Done! Check your spectacular charts!"