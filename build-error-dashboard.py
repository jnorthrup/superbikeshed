#!/usr/bin/env python3

import subprocess
import re
import json
from collections import defaultdict, Counter
from datetime import datetime
import webbrowser
import os

def run_gradle_build():
    """Run gradle build with stacktrace and capture output"""
    print("🚀 Running gradle build with stacktrace...")
    
    cmd = ["./gradlew", "build", "--stacktrace", "--console=plain", "--no-daemon"]
    
    result = subprocess.run(
        cmd,
        capture_output=True,
        text=True
    )
    
    # Save to file
    with open("build-stacktrace.log", "w") as f:
        f.write(result.stdout)
        f.write("\n--- STDERR ---\n")
        f.write(result.stderr)
    
    print(f"Build exit code: {result.returncode}")
    return result.stdout + "\n" + result.stderr

def parse_errors(build_output):
    """Parse build output to extract error information"""
    errors = []
    
    # Patterns to match different error types
    patterns = {
        'dependency_error': r'Could not resolve ([^:]+:[^:]+:[^\.]+)',
        'variant_error': r'No matching variant of ([^:]+:[^:]+:[^\.]+\.[^\s]+)',
        'task_error': r'Could not determine the dependencies of task \'([^\']+)\'',
        'compilation_error': r'e: ([^:]+):(\d+):(\d+): (.+)',
        'type_error': r'Type mismatch: inferred type is (.+) but (.+) was expected',
        'unresolved_reference': r'Unresolved reference: (.+)',
        'module_error': r'project :(\w+)',
        'platform_error': r'platform\.type\' with value \'(\w+)\'',
        'wasm_error': r'wasm|wasmJs',
        'kotlin_error': r'kotlin-(\w+)',
        'serialization_error': r'kotlinx-serialization'
    }
    
    lines = build_output.split('\n')
    
    for i, line in enumerate(lines):
        for error_type, pattern in patterns.items():
            if re.search(pattern, line, re.IGNORECASE):
                matches = re.findall(pattern, line, re.IGNORECASE)
                if matches or error_type.endswith('_error'):
                    errors.append({
                        'error_type': error_type,
                        'line_number': i,
                        'line_content': line.strip()[:300],
                        'matched_content': str(matches[0] if matches else 'matched'),
                        'severity': 'high' if 'Could not' in line or 'failed' in line.lower() else 'medium'
                    })
    
    return errors

def analyze_errors(errors):
    """Analyze errors and create statistics"""
    stats = {
        'total_errors': len(errors),
        'error_types': Counter(e['error_type'] for e in errors),
        'severity_counts': Counter(e['severity'] for e in errors),
        'error_timeline': [],
        'top_patterns': []
    }
    
    # Create timeline data
    timeline_buckets = defaultdict(int)
    for error in errors:
        bucket = error['line_number'] // 100
        timeline_buckets[bucket] += 1
    
    stats['error_timeline'] = [{'x': k * 100, 'y': v} for k, v in sorted(timeline_buckets.items())]
    
    # Find top error patterns
    pattern_counts = Counter(e['matched_content'] for e in errors)
    stats['top_patterns'] = [{'pattern': p, 'count': c} for p, c in pattern_counts.most_common(10)]
    
    return stats

def create_html_dashboard(errors, stats):
    """Create an interactive HTML dashboard"""
    html_content = f"""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🔥 Gradle Build Error Dashboard</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body {{ 
            background: #0f172a; 
            color: #e2e8f0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        }}
        .chart-container {{
            position: relative;
            height: 400px;
            margin: 20px 0;
        }}
        .error-card {{
            background: #1e293b;
            border: 1px solid #334155;
            border-radius: 8px;
            padding: 16px;
            margin: 8px 0;
            transition: all 0.3s;
        }}
        .error-card:hover {{
            background: #334155;
            transform: translateY(-2px);
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        }}
        .high-severity {{
            border-left: 4px solid #ef4444;
        }}
        .medium-severity {{
            border-left: 4px solid #f59e0b;
        }}
        .gradient-text {{
            background: linear-gradient(to right, #ec4899, #8b5cf6);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }}
        .pulse {{
            animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
        }}
        @keyframes pulse {{
            0%, 100% {{ opacity: 1; }}
            50% {{ opacity: .5; }}
        }}
    </style>
</head>
<body class="min-h-screen p-8">
    <div class="max-w-7xl mx-auto">
        <!-- Header -->
        <div class="text-center mb-12">
            <h1 class="text-6xl font-bold gradient-text mb-4">🔥 Gradle Build Error Dashboard 🔥</h1>
            <p class="text-xl text-gray-400">Generated: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}</p>
            <div class="mt-4 flex justify-center gap-8">
                <div class="text-center">
                    <div class="text-4xl font-bold text-red-500 pulse">{stats['total_errors']}</div>
                    <div class="text-sm text-gray-400">Total Errors</div>
                </div>
                <div class="text-center">
                    <div class="text-4xl font-bold text-orange-500">{stats['severity_counts']['high']}</div>
                    <div class="text-sm text-gray-400">High Severity</div>
                </div>
                <div class="text-center">
                    <div class="text-4xl font-bold text-yellow-500">{stats['severity_counts']['medium']}</div>
                    <div class="text-sm text-gray-400">Medium Severity</div>
                </div>
            </div>
        </div>

        <!-- Charts Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
            <!-- Error Type Distribution -->
            <div class="bg-slate-800 rounded-lg p-6">
                <h2 class="text-2xl font-bold mb-4">Error Type Distribution</h2>
                <div class="chart-container">
                    <canvas id="errorTypePie"></canvas>
                </div>
            </div>

            <!-- Error Timeline -->
            <div class="bg-slate-800 rounded-lg p-6">
                <h2 class="text-2xl font-bold mb-4">Error Timeline</h2>
                <div class="chart-container">
                    <canvas id="errorTimeline"></canvas>
                </div>
            </div>

            <!-- Top Error Patterns -->
            <div class="bg-slate-800 rounded-lg p-6">
                <h2 class="text-2xl font-bold mb-4">Top Error Patterns</h2>
                <div class="chart-container">
                    <canvas id="topPatterns"></canvas>
                </div>
            </div>

            <!-- Severity Gauge -->
            <div class="bg-slate-800 rounded-lg p-6">
                <h2 class="text-2xl font-bold mb-4">Severity Distribution</h2>
                <div class="chart-container">
                    <canvas id="severityDoughnut"></canvas>
                </div>
            </div>
        </div>

        <!-- Error List -->
        <div class="bg-slate-800 rounded-lg p-6">
            <h2 class="text-2xl font-bold mb-4">Error Details</h2>
            <div class="space-y-2 max-h-96 overflow-y-auto">
                {create_error_cards(errors[:50])}
            </div>
        </div>

        <!-- Fix Recommendations -->
        <div class="bg-slate-800 rounded-lg p-6 mt-8">
            <h2 class="text-2xl font-bold mb-4">🛠️ Recommended Fixes</h2>
            <div class="space-y-4">
                {create_fix_recommendations(stats)}
            </div>
        </div>
    </div>

    <script>
        // Chart configuration
        Chart.defaults.color = '#e2e8f0';
        Chart.defaults.borderColor = '#334155';

        // Error Type Pie Chart
        new Chart(document.getElementById('errorTypePie'), {{
            type: 'doughnut',
            data: {{
                labels: {json.dumps(list(stats['error_types'].keys()))},
                datasets: [{{
                    data: {json.dumps(list(stats['error_types'].values()))},
                    backgroundColor: [
                        '#ef4444', '#f59e0b', '#10b981', '#3b82f6', 
                        '#8b5cf6', '#ec4899', '#14b8a6', '#f97316'
                    ],
                    borderWidth: 2,
                    borderColor: '#1e293b'
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                plugins: {{
                    legend: {{
                        position: 'right'
                    }}
                }}
            }}
        }});

        // Error Timeline
        new Chart(document.getElementById('errorTimeline'), {{
            type: 'line',
            data: {{
                labels: {json.dumps([d['x'] for d in stats['error_timeline']])},
                datasets: [{{
                    label: 'Error Density',
                    data: {json.dumps([d['y'] for d in stats['error_timeline']])},
                    borderColor: '#ec4899',
                    backgroundColor: 'rgba(236, 72, 153, 0.1)',
                    fill: true,
                    tension: 0.4
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                scales: {{
                    x: {{
                        title: {{
                            display: true,
                            text: 'Build Progress (line number)'
                        }}
                    }},
                    y: {{
                        title: {{
                            display: true,
                            text: 'Error Count'
                        }}
                    }}
                }}
            }}
        }});

        // Top Patterns Bar Chart
        new Chart(document.getElementById('topPatterns'), {{
            type: 'bar',
            data: {{
                labels: {json.dumps([p['pattern'][:30] + '...' if len(p['pattern']) > 30 else p['pattern'] for p in stats['top_patterns'][:8]])},
                datasets: [{{
                    label: 'Occurrences',
                    data: {json.dumps([p['count'] for p in stats['top_patterns'][:8]])},
                    backgroundColor: '#8b5cf6'
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                indexAxis: 'y',
                scales: {{
                    x: {{
                        beginAtZero: true
                    }}
                }}
            }}
        }});

        // Severity Doughnut
        new Chart(document.getElementById('severityDoughnut'), {{
            type: 'doughnut',
            data: {{
                labels: ['High Severity', 'Medium Severity'],
                datasets: [{{
                    data: [{stats['severity_counts']['high']}, {stats['severity_counts']['medium']}],
                    backgroundColor: ['#ef4444', '#f59e0b'],
                    borderWidth: 2,
                    borderColor: '#1e293b'
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                cutout: '70%',
                plugins: {{
                    legend: {{
                        position: 'bottom'
                    }}
                }}
            }}
        }});
    </script>
</body>
</html>
"""
    
    with open("build-error-dashboard.html", "w") as f:
        f.write(html_content)
    
    return "build-error-dashboard.html"

def create_error_cards(errors):
    """Create HTML cards for individual errors"""
    cards = []
    for error in errors:
        severity_class = 'high-severity' if error['severity'] == 'high' else 'medium-severity'
        cards.append(f"""
            <div class="error-card {severity_class}">
                <div class="flex justify-between items-start">
                    <div>
                        <span class="text-sm font-semibold text-purple-400">{error['error_type']}</span>
                        <span class="text-xs text-gray-500 ml-2">Line {error['line_number']}</span>
                    </div>
                    <span class="text-xs px-2 py-1 rounded {'bg-red-900' if error['severity'] == 'high' else 'bg-orange-900'}">
                        {error['severity']}
                    </span>
                </div>
                <div class="mt-2 text-sm text-gray-300 font-mono">
                    {error['line_content']}
                </div>
            </div>
        """)
    return '\n'.join(cards)

def create_fix_recommendations(stats):
    """Create fix recommendations based on error analysis"""
    recommendations = []
    
    if 'dependency_error' in stats['error_types']:
        recommendations.append("""
            <div class="bg-slate-700 rounded p-4">
                <h3 class="font-bold text-lg mb-2">🔧 Fix Dependency Issues</h3>
                <p class="text-sm text-gray-400">Update kotlinx-serialization-json to a version that supports WASM</p>
                <code class="block mt-2 p-2 bg-slate-900 rounded text-xs">
                    val kotlinxSerializationJsonVersion = "1.7.3"
                </code>
            </div>
        """)
    
    if 'wasm_error' in stats['error_types']:
        recommendations.append("""
            <div class="bg-slate-700 rounded p-4">
                <h3 class="font-bold text-lg mb-2">🌐 WASM Compatibility</h3>
                <p class="text-sm text-gray-400">Ensure all dependencies have WASM support or exclude WASM target temporarily</p>
                <code class="block mt-2 p-2 bg-slate-900 rounded text-xs">
                    // In module build.gradle.kts
                    kotlin {
                        // Comment out wasmJs temporarily
                        // wasmJs { browser(); nodejs() }
                    }
                </code>
            </div>
        """)
    
    return '\n'.join(recommendations)

def main():
    print("🎆 SPECTACULAR BUILD ERROR ANALYZER 🎆")
    print("=====================================\n")
    
    # Run gradle build
    build_output = run_gradle_build()
    
    # Parse errors
    print("\n📊 Analyzing errors...")
    errors = parse_errors(build_output)
    
    if not errors:
        print("✨ No errors found! Build successful! ✨")
        return
    
    print(f"Found {len(errors)} errors")
    
    # Analyze errors
    stats = analyze_errors(errors)
    
    # Create HTML dashboard
    print("\n🎨 Creating interactive dashboard...")
    html_file = create_html_dashboard(errors, stats)
    
    # Open in browser
    print(f"\n🚀 Launching dashboard in browser...")
    file_url = f"file://{os.path.abspath(html_file)}"
    webbrowser.open(file_url)
    
    print(f"\n✅ Dashboard created: {html_file}")
    print("🌟 Happy debugging!")

if __name__ == "__main__":
    main()