#!/usr/bin/env python3

import subprocess
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import re
from collections import defaultdict
import numpy as np
from matplotlib.patches import Rectangle
import matplotlib.patches as mpatches
from datetime import datetime

# Set up the visual style
plt.style.use('dark_background')
sns.set_palette("husl")

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
                    error_dict = {
                        'error_type': error_type,
                        'line_number': i,
                        'line_content': line.strip()[:200],  # Truncate long lines
                        'matched_content': str(matches[0] if matches else 'matched'),
                        'file': None,
                        'module': None,
                        'dependency': None,
                        'platform': None,
                        'severity': 'high' if 'Could not' in line else 'medium'
                    }
                    
                    # Extract specific fields based on error type
                    if error_type == 'dependency_error' and matches:
                        error_dict['dependency'] = matches[0]
                    elif error_type == 'module_error' and matches:
                        error_dict['module'] = matches[0]
                    elif error_type == 'platform_error' and matches:
                        error_dict['platform'] = matches[0]
                    elif error_type == 'compilation_error' and matches:
                        error_dict['file'] = matches[0][0]
                        error_dict['line_number'] = matches[0][1]
                        
                    errors.append(error_dict)
    
    return pd.DataFrame(errors)

def create_spectacular_viz(df, summary_df):
    """Create a spectacular dashboard of visualizations"""
    
    # Create figure with subplots
    fig = plt.figure(figsize=(20, 12))
    fig.suptitle('🔥 GRADLE BUILD ERROR ANALYSIS DASHBOARD 🔥', fontsize=24, fontweight='bold')
    
    # Define grid
    gs = fig.add_gridspec(3, 4, hspace=0.3, wspace=0.3)
    
    # 1. Error Type Distribution - Pie Chart with explosion
    ax1 = fig.add_subplot(gs[0, :2])
    error_counts = df['error_type'].value_counts()
    colors = plt.cm.Set3(np.linspace(0, 1, len(error_counts)))
    explode = [0.1 if i == 0 else 0.05 for i in range(len(error_counts))]
    
    wedges, texts, autotexts = ax1.pie(error_counts.values, 
                                         labels=error_counts.index,
                                         explode=explode,
                                         autopct='%1.1f%%',
                                         colors=colors,
                                         shadow=True,
                                         startangle=90)
    
    for autotext in autotexts:
        autotext.set_color('white')
        autotext.set_weight('bold')
    
    ax1.set_title('Error Type Distribution', fontsize=16, fontweight='bold', pad=20)
    
    # 2. Platform Distribution - Donut Chart
    ax2 = fig.add_subplot(gs[0, 2:])
    platform_data = df[df['platform'].notna()]['platform'].value_counts()
    if not platform_data.empty:
        colors = plt.cm.Spectral(np.linspace(0, 1, len(platform_data)))
        wedges, texts = ax2.pie(platform_data.values, labels=platform_data.index, 
                                 colors=colors, wedgeprops=dict(width=0.5),
                                 startangle=90)
        centre_circle = plt.Circle((0, 0), 0.70, fc='black')
        ax2.add_artist(centre_circle)
        ax2.text(0, 0, f'{len(df)}\nTotal\nErrors', ha='center', va='center', 
                 fontsize=16, fontweight='bold', color='white')
    ax2.set_title('Platform-Specific Errors', fontsize=16, fontweight='bold', pad=20)
    
    # 3. Module Error Heatmap
    ax3 = fig.add_subplot(gs[1, :2])
    module_errors = df[df['module'].notna()].groupby(['module', 'error_type']).size().unstack(fill_value=0)
    if not module_errors.empty:
        sns.heatmap(module_errors, annot=True, fmt='d', cmap='YlOrRd', 
                    cbar_kws={'label': 'Error Count'}, ax=ax3)
        ax3.set_title('Module vs Error Type Heatmap', fontsize=16, fontweight='bold')
        ax3.set_xlabel('Error Type')
        ax3.set_ylabel('Module')
    
    # 4. Error Timeline (simulated based on line numbers)
    ax4 = fig.add_subplot(gs[1, 2:])
    timeline_data = df.groupby(df['line_number'] // 100)['error_type'].count()
    ax4.fill_between(timeline_data.index, timeline_data.values, alpha=0.6, color='cyan')
    ax4.plot(timeline_data.index, timeline_data.values, linewidth=2, color='yellow')
    ax4.set_title('Error Density Through Build Log', fontsize=16, fontweight='bold')
    ax4.set_xlabel('Build Progress (line blocks)')
    ax4.set_ylabel('Error Count')
    ax4.grid(True, alpha=0.3)
    
    # 5. Top Dependencies with Issues - Horizontal Bar
    ax5 = fig.add_subplot(gs[2, :2])
    dep_errors = df[df['dependency'].notna()]['dependency'].value_counts().head(10)
    if not dep_errors.empty:
        bars = ax5.barh(dep_errors.index, dep_errors.values, color=plt.cm.plasma(np.linspace(0, 1, len(dep_errors))))
        ax5.set_title('Top 10 Problematic Dependencies', fontsize=16, fontweight='bold')
        ax5.set_xlabel('Error Count')
        
        # Add value labels on bars
        for i, (bar, value) in enumerate(zip(bars, dep_errors.values)):
            ax5.text(bar.get_width() + 0.1, bar.get_y() + bar.get_height()/2, 
                    str(value), va='center', fontweight='bold')
    
    # 6. Error Severity Distribution - Stacked Bar
    ax6 = fig.add_subplot(gs[2, 2:])
    severity_by_type = df.groupby(['error_type', 'severity']).size().unstack(fill_value=0)
    if not severity_by_type.empty:
        severity_by_type.plot(kind='bar', stacked=True, ax=ax6, 
                              color=['#FF6B6B', '#FFA500'], alpha=0.8)
        ax6.set_title('Error Severity by Type', fontsize=16, fontweight='bold')
        ax6.set_xlabel('Error Type')
        ax6.set_ylabel('Count')
        ax6.legend(title='Severity', loc='upper right')
        plt.setp(ax6.xaxis.get_majorticklabels(), rotation=45, ha='right')
    
    # Add timestamp
    fig.text(0.99, 0.01, f'Generated: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}', 
             ha='right', va='bottom', fontsize=10, alpha=0.7)
    
    plt.tight_layout()
    plt.savefig('build-error-analysis.png', dpi=300, bbox_inches='tight', facecolor='black')
    plt.show()

def create_sunburst_viz(df):
    """Create a sunburst chart showing error hierarchy"""
    fig, ax = plt.subplots(figsize=(12, 12), subplot_kw=dict(projection='polar'))
    
    # Group errors hierarchically
    hierarchy = df.groupby(['error_type', 'module']).size().reset_index(name='count')
    
    # Create sunburst data
    error_types = df['error_type'].unique()
    n_types = len(error_types)
    
    # Inner ring - error types
    theta = np.linspace(0.0, 2 * np.pi, n_types, endpoint=False)
    width = 2 * np.pi / n_types
    
    colors_inner = plt.cm.Set3(np.linspace(0, 1, n_types))
    
    bars = ax.bar(theta, 0.5, width=width, bottom=0.5, color=colors_inner, alpha=0.8)
    
    # Add labels
    for i, (bar, error_type) in enumerate(zip(bars, error_types)):
        angle = theta[i] + width/2
        ax.text(angle, 0.75, error_type, rotation=np.degrees(angle)-90 if angle > np.pi else np.degrees(angle)+90,
                ha='center', va='center', fontweight='bold')
    
    ax.set_title('Error Hierarchy Sunburst', fontsize=20, fontweight='bold', pad=30)
    ax.set_ylim(0, 1.5)
    ax.set_yticklabels([])
    ax.set_xticklabels([])
    ax.spines['polar'].set_visible(False)
    ax.grid(False)
    
    plt.savefig('error-sunburst.png', dpi=300, bbox_inches='tight', facecolor='black')
    plt.show()

def create_3d_viz(summary_df):
    """Create a 3D visualization of error distribution"""
    from mpl_toolkits.mplot3d import Axes3D
    
    fig = plt.figure(figsize=(12, 10))
    ax = fig.add_subplot(111, projection='3d')
    
    # Prepare data
    axes = summary_df['axis'].unique()
    for i, axis in enumerate(axes):
        axis_data = summary_df[summary_df['axis'] == axis].head(5)
        
        xpos = np.arange(len(axis_data))
        ypos = [i] * len(axis_data)
        zpos = np.zeros(len(axis_data))
        
        dx = np.ones(len(axis_data)) * 0.8
        dy = np.ones(len(axis_data)) * 0.8
        dz = axis_data['count'].values
        
        colors = plt.cm.viridis(dz / dz.max())
        
        ax.bar3d(xpos, ypos, zpos, dx, dy, dz, color=colors, alpha=0.8)
    
    ax.set_xlabel('Rank')
    ax.set_ylabel('Error Axis')
    ax.set_zlabel('Count')
    ax.set_title('3D Error Distribution by Axis', fontsize=16, fontweight='bold')
    
    plt.savefig('error-3d.png', dpi=300, bbox_inches='tight', facecolor='black')
    plt.show()

def main():
    print("🎨 Starting Spectacular Build Error Analysis with Visualizations! 🎨\n")
    
    # Run gradle build
    build_output = run_gradle_build()
    
    # Parse errors
    print("\n🔍 Parsing errors...")
    error_df = parse_errors(build_output)
    
    if error_df.empty:
        print("✨ No errors found in build output! Build successful! ✨")
        return
    
    print(f"🎯 Found {len(error_df)} error instances")
    
    # Save raw error data
    error_df.to_csv("build-errors.csv", index=False)
    
    # Create summary
    summary_data = []
    for error_type in error_df['error_type'].unique():
        type_df = error_df[error_df['error_type'] == error_type]
        summary_data.append({
            'axis': 'error_type',
            'value': error_type,
            'count': len(type_df),
            'percentage': len(type_df) / len(error_df) * 100
        })
    
    summary_df = pd.DataFrame(summary_data)
    
    # Create visualizations
    print("\n🎨 Creating spectacular visualizations...")
    create_spectacular_viz(error_df, summary_df)
    create_sunburst_viz(error_df)
    create_3d_viz(summary_df)
    
    print("\n✅ Analysis complete! Check out:")
    print("  📊 build-error-analysis.png - Main dashboard")
    print("  🌅 error-sunburst.png - Hierarchical view")
    print("  🎲 error-3d.png - 3D distribution")
    print("  📄 build-errors.csv - Raw data")
    print("\n🚀 Happy debugging!")

if __name__ == "__main__":
    main()