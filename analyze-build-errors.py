#\!/usr/bin/env python3
"""
Pandas script to analyze gradle build errors and rank them by different axes
"""

import pandas as pd
import subprocess
import re
import json
from collections import defaultdict
from datetime import datetime

def run_gradle_build():
    """Run gradle build and capture output"""
    print("🚀 Running gradle build...")
    result = subprocess.run(
        ["./gradlew", "build", "--console=plain", "--no-daemon", "--stacktrace"],
        capture_output=True,
        text=True
    )
    
    return result.stdout + "\n--- STDERR ---\n" + result.stderr, result.returncode

def parse_errors(build_output):
    """Parse errors from build output into structured format"""
    errors = []
    
    # Pattern for gradle errors
    error_patterns = [
        (r'e: file://(/[^:]+):(\d+):(\d+): (.+)', 'compilation'),
        (r'> Configure project :([^\s]+)', 'configuration'),
        (r'\* What went wrong:\s*(.+)', 'build_failure'),
        (r'(\w+Exception):\s*(.+)', 'exception'),
        (r'Line (\d+):\s*(.+?)\s*\^\s*(.+)', 'script_error')
    ]
    
    lines = build_output.split('\n')
    
    for i, line in enumerate(lines):
        for pattern, error_type in error_patterns:
            match = re.search(pattern, line)
            if match:
                if error_type == 'compilation':
                    errors.append({
                        'type': error_type,
                        'file': match.group(1),
                        'line': int(match.group(2)),
                        'column': int(match.group(3)),
                        'message': match.group(4),
                        'module': extract_module(match.group(1)),
                        'severity': classify_severity(match.group(4))
                    })
                elif error_type == 'configuration':
                    errors.append({
                        'type': error_type,
                        'module': match.group(1),
                        'message': f"Configuration error in {match.group(1)}",
                        'line': i,
                        'severity': 'medium'
                    })
                elif error_type == 'script_error':
                    errors.append({
                        'type': error_type,
                        'line': int(match.group(1)),
                        'code': match.group(2),
                        'message': match.group(3),
                        'severity': 'high'
                    })
                else:
                    errors.append({
                        'type': error_type,
                        'message': match.group(0),
                        'line': i,
                        'severity': 'medium'
                    })
    
    return errors

def extract_module(file_path):
    """Extract module name from file path"""
    parts = file_path.split('/')
    for i, part in enumerate(parts):
        if part == 'v2superbikeshed' and i < len(parts) - 1:
            return parts[i + 1]
    return 'unknown'

def classify_severity(message):
    """Classify error severity based on message content"""
    high_severity_keywords = ['FAILURE', 'Exception', 'cannot find', 'unresolved']
    medium_severity_keywords = ['warning', 'deprecated', 'type mismatch']
    
    message_lower = message.lower()
    
    for keyword in high_severity_keywords:
        if keyword.lower() in message_lower:
            return 'high'
    
    for keyword in medium_severity_keywords:
        if keyword.lower() in message_lower:
            return 'medium'
    
    return 'low'

def analyze_errors(errors_df):
    """Analyze errors by different axes"""
    analyses = {}
    
    # 1. By Error Type
    analyses['by_type'] = errors_df['type'].value_counts().to_dict()
    
    # 2. By Module
    if 'module' in errors_df.columns:
        analyses['by_module'] = errors_df['module'].value_counts().to_dict()
    
    # 3. By Severity
    if 'severity' in errors_df.columns:
        analyses['by_severity'] = errors_df['severity'].value_counts().to_dict()
    
    # 4. Most common error messages
    message_counts = errors_df['message'].value_counts()
    analyses['top_messages'] = message_counts.head(10).to_dict()
    
    # 5. Files with most errors
    if 'file' in errors_df.columns:
        file_errors = errors_df[errors_df['file'].notna()]['file'].value_counts()
        analyses['by_file'] = file_errors.head(10).to_dict()
    
    # 6. Error patterns (extract common patterns from messages)
    patterns = defaultdict(int)
    for msg in errors_df['message']:
        # Extract type mismatch patterns
        type_match = re.search(r'Type mismatch.*inferred type is (.+) but (.+) was expected', msg)
        if type_match:
            patterns[f"Type mismatch: {type_match.group(1)} -> {type_match.group(2)}"] += 1
        
        # Extract unresolved reference patterns
        unresolved = re.search(r'Unresolved reference: (.+)', msg)
        if unresolved:
            patterns[f"Unresolved: {unresolved.group(1)}"] += 1
    
    analyses['patterns'] = dict(sorted(patterns.items(), key=lambda x: x[1], reverse=True)[:10])
    
    return analyses

def generate_report(errors_df, analyses):
    """Generate a comprehensive error report"""
    report = []
    report.append("=" * 80)
    report.append("GRADLE BUILD ERROR ANALYSIS REPORT")
    report.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append("=" * 80)
    report.append("")
    
    report.append(f"Total Errors: {len(errors_df)}")
    report.append("")
    
    # Error Summary by Type
    report.append("ERROR SUMMARY BY TYPE:")
    report.append("-" * 40)
    for error_type, count in analyses['by_type'].items():
        report.append(f"  {error_type:20} : {count:3d} errors")
    report.append("")
    
    # Error Summary by Module
    if 'by_module' in analyses:
        report.append("ERROR SUMMARY BY MODULE:")
        report.append("-" * 40)
        for module, count in sorted(analyses['by_module'].items(), key=lambda x: x[1], reverse=True):
            report.append(f"  {module:20} : {count:3d} errors")
        report.append("")
    
    # Error Summary by Severity
    if 'by_severity' in analyses:
        report.append("ERROR SUMMARY BY SEVERITY:")
        report.append("-" * 40)
        for severity, count in analyses['by_severity'].items():
            report.append(f"  {severity:20} : {count:3d} errors")
        report.append("")
    
    # Top Error Messages
    report.append("TOP ERROR MESSAGES:")
    report.append("-" * 40)
    for i, (msg, count) in enumerate(analyses['top_messages'].items(), 1):
        truncated_msg = msg[:60] + "..." if len(msg) > 60 else msg
        report.append(f"  {i:2d}. {truncated_msg} ({count} occurrences)")
    report.append("")
    
    # Error Patterns
    if analyses['patterns']:
        report.append("COMMON ERROR PATTERNS:")
        report.append("-" * 40)
        for pattern, count in analyses['patterns'].items():
            report.append(f"  {pattern:50} : {count:3d} occurrences")
        report.append("")
    
    # Files with Most Errors
    if 'by_file' in analyses:
        report.append("FILES WITH MOST ERRORS:")
        report.append("-" * 40)
        for file_path, count in analyses['by_file'].items():
            file_name = file_path.split('/')[-1]
            report.append(f"  {file_name:30} : {count:3d} errors")
    
    return "\n".join(report)

def main():
    # Run gradle build
    build_output, exit_code = run_gradle_build()
    
    # Save raw output
    with open('build-output-raw.log', 'w') as f:
        f.write(build_output)
    
    print(f"Build exit code: {exit_code}")
    
    # Parse errors
    errors = parse_errors(build_output)
    
    if not errors:
        print("✅ No errors found\!")
        return
    
    # Convert to DataFrame
    errors_df = pd.DataFrame(errors)
    
    # Save to CSV for further analysis
    errors_df.to_csv('build-errors.csv', index=False)
    print(f"📊 Saved {len(errors_df)} errors to build-errors.csv")
    
    # Analyze errors
    analyses = analyze_errors(errors_df)
    
    # Save analyses as JSON
    with open('build-errors-analysis.json', 'w') as f:
        json.dump(analyses, f, indent=2)
    
    # Generate and print report
    report = generate_report(errors_df, analyses)
    print("\n" + report)
    
    # Save report
    with open('build-errors-report.txt', 'w') as f:
        f.write(report)
    
    # Create a summary DataFrame for the top issues
    summary_data = []
    for module, count in analyses.get('by_module', {}).items():
        severity_counts = errors_df[errors_df['module'] == module]['severity'].value_counts().to_dict()
        summary_data.append({
            'module': module,
            'total_errors': count,
            'high_severity': severity_counts.get('high', 0),
            'medium_severity': severity_counts.get('medium', 0),
            'low_severity': severity_counts.get('low', 0)
        })
    
    if summary_data:
        summary_df = pd.DataFrame(summary_data)
        summary_df = summary_df.sort_values('total_errors', ascending=False)
        summary_df.to_csv('build-errors-summary.csv', index=False)
        
        print("\n📈 MODULE ERROR RANKINGS:")
        print("-" * 60)
        print(summary_df.to_string(index=False))

if __name__ == "__main__":
    main()
