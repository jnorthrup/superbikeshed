#!/usr/bin/env python3
"""
Gradle Error Analyzer with Stacktrace Context
Runs gradle build with stacktrace, analyzes errors by impact, and shows context
"""

import subprocess
import re
import os
from collections import defaultdict
from pathlib import Path
import sys

def run_gradle_build(module=None):
    """Run gradle build with stacktrace and capture output"""
    cmd = ["./gradlew"]
    if module:
        cmd.append(f":{module}:build")
    else:
        cmd.append("build")
    
    cmd.extend(["--console=plain", "--no-daemon", "--stacktrace"])
    
    print(f"Running: {' '.join(cmd)}")
    print("=" * 80)
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    return result.stdout + result.stderr

def parse_errors(output):
    """Parse errors from gradle output and extract file locations"""
    errors = defaultdict(list)
    
    # Pattern to match Kotlin compilation errors
    error_pattern = re.compile(r'e: file://(.+?):(\d+):(\d+) (.+)')
    
    for line in output.split('\n'):
        match = error_pattern.match(line)
        if match:
            file_path = match.group(1)
            line_num = int(match.group(2))
            col_num = int(match.group(3))
            error_msg = match.group(4)
            
            errors[file_path].append({
                'line': line_num,
                'column': col_num,
                'message': error_msg,
                'full_line': line.strip()
            })
    
    return errors

def get_file_context(file_path, line_num, context_lines=20):
    """Get context around error line in file"""
    try:
        with open(file_path, 'r') as f:
            lines = f.readlines()
        
        start = max(0, line_num - context_lines - 1)
        end = min(len(lines), line_num + context_lines)
        
        context = []
        for i in range(start, end):
            line_marker = ">>>" if i == line_num - 1 else "   "
            context.append(f"{line_marker} {i+1:4d}: {lines[i].rstrip()}")
        
        return '\n'.join(context)
    except Exception as e:
        return f"Could not read file: {e}"

def analyze_errors_by_impact(errors):
    """Sort files by error count (impact) and return analysis"""
    # Sort by error count
    sorted_files = sorted(errors.items(), key=lambda x: len(x[1]), reverse=True)
    
    analysis = []
    for file_path, file_errors in sorted_files:
        file_info = {
            'path': file_path,
            'error_count': len(file_errors),
            'errors': file_errors,
            'unique_errors': len(set(e['message'] for e in file_errors))
        }
        analysis.append(file_info)
    
    return analysis

def display_analysis(analysis, max_files=10, show_context=True):
    """Display error analysis with optional file context"""
    print("\n" + "=" * 80)
    print("ERROR ANALYSIS BY IMPACT")
    print("=" * 80)
    
    total_errors = sum(f['error_count'] for f in analysis)
    print(f"\nTotal errors: {total_errors} across {len(analysis)} files")
    print("\nTop files by error count:")
    
    for i, file_info in enumerate(analysis[:max_files]):
        print(f"\n{'-' * 80}")
        print(f"#{i+1} {file_info['path']}")
        print(f"   Errors: {file_info['error_count']} ({file_info['unique_errors']} unique)")
        
        # Group errors by type
        error_types = defaultdict(list)
        for error in file_info['errors']:
            error_types[error['message']].append(error)
        
        # Show error summary
        print("\n   Error types:")
        for error_msg, instances in sorted(error_types.items(), key=lambda x: len(x[1]), reverse=True):
            print(f"   - [{len(instances)}x] {error_msg[:100]}...")
            if len(instances) <= 3:
                for inst in instances:
                    print(f"     Line {inst['line']}, Column {inst['column']}")
            else:
                print(f"     Lines: {', '.join(str(e['line']) for e in instances[:5])}...")
        
        if show_context and i < 3:  # Show context for top 3 files
            print(f"\n   First error context (line {file_info['errors'][0]['line']}):")
            print("   " + "-" * 60)
            context = get_file_context(file_info['path'], file_info['errors'][0]['line'])
            for line in context.split('\n'):
                print("   " + line)

def fix_common_errors(analysis):
    """Generate fix suggestions for common error patterns"""
    print("\n" + "=" * 80)
    print("SUGGESTED FIXES")
    print("=" * 80)
    
    # Analyze common patterns
    all_errors = []
    for file_info in analysis:
        all_errors.extend([(file_info['path'], e) for e in file_info['errors']])
    
    # Group by error message patterns
    error_patterns = defaultdict(list)
    for file_path, error in all_errors:
        if "Unresolved reference" in error['message']:
            ref = error['message'].split("'")[1] if "'" in error['message'] else error['message']
            error_patterns['unresolved_reference'].append((file_path, ref))
        elif "type mismatch" in error['message'].lower():
            error_patterns['type_mismatch'].append((file_path, error['message']))
        elif "expect" in error['message'] and "actual" in error['message']:
            error_patterns['expect_actual'].append((file_path, error['message']))
        elif "opt-in" in error['message'].lower():
            error_patterns['opt_in'].append((file_path, error['message']))
    
    # Generate fixes
    if error_patterns['unresolved_reference']:
        refs = defaultdict(int)
        for _, ref in error_patterns['unresolved_reference']:
            refs[ref] += 1
        
        print("\n1. Unresolved references (most common):")
        for ref, count in sorted(refs.items(), key=lambda x: x[1], reverse=True)[:10]:
            print(f"   - {ref} ({count} occurrences)")
            if ref == "datetime":
                print("     Fix: Add kotlinx-datetime dependency")
            elif ref == "nio":
                print("     Fix: Change imports from 'nio' to 'io'")
    
    if error_patterns['opt_in']:
        print("\n2. Opt-in required:")
        print("   Fix: Add @OptIn(ExperimentalForeignApi::class) to native files using cinterop/posix")
    
    if error_patterns['expect_actual']:
        print("\n3. Expect/Actual issues:")
        print("   Fix: Ensure expect declarations have corresponding actual implementations")

def main():
    if len(sys.argv) > 1:
        module = sys.argv[1]
        print(f"Analyzing module: {module}")
    else:
        module = None
        print("Analyzing all modules")
    
    # Run gradle build
    output = run_gradle_build(module)
    
    # Parse errors
    errors = parse_errors(output)
    
    if not errors:
        print("\nNo compilation errors found!")
        return
    
    # Analyze by impact
    analysis = analyze_errors_by_impact(errors)
    
    # Display analysis
    display_analysis(analysis)
    
    # Suggest fixes
    fix_common_errors(analysis)
    
    # Save full output
    with open("gradle-build-output.log", "w") as f:
        f.write(output)
    print(f"\nFull gradle output saved to gradle-build-output.log")

if __name__ == "__main__":
    main()