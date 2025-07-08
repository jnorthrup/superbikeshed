#!/usr/bin/env python3
"""
Parse Gradle build stacktraces and extract compilation errors by file and type
"""

import re
import sys
from collections import defaultdict
from typing import Dict, List, Tuple

class StackTraceParser:
    def __init__(self):
        self.errors_by_file = defaultdict(list)
        self.errors_by_type = defaultdict(list)
        self.stacktraces = []
        
    def parse_file(self, filename: str):
        """Parse a gradle build output file"""
        with open(filename, 'r') as f:
            content = f.read()
            
        # Extract compilation errors
        error_pattern = r'e: file://(/[^:]+):(\d+):(\d+)\s+(.+)'
        for match in re.finditer(error_pattern, content):
            file_path = match.group(1)
            line_num = match.group(2)
            col_num = match.group(3)
            error_msg = match.group(4)
            
            # Categorize error type
            error_type = self._categorize_error(error_msg)
            
            error_info = {
                'line': line_num,
                'column': col_num,
                'message': error_msg,
                'type': error_type
            }
            
            self.errors_by_file[file_path].append(error_info)
            self.errors_by_type[error_type].append({
                'file': file_path,
                'line': line_num,
                'message': error_msg
            })
            
        # Extract full stacktraces for exceptions
        stacktrace_pattern = r'\* What went wrong:(.*?)(?=\* Try:|\* Exception is:|\Z)'
        for match in re.finditer(stacktrace_pattern, content, re.DOTALL):
            self.stacktraces.append(match.group(1).strip())
            
    def _categorize_error(self, error_msg: str) -> str:
        """Categorize error message by type"""
        if 'Unresolved reference' in error_msg:
            return 'unresolved_reference'
        elif 'Type mismatch' in error_msg or 'type mismatch' in error_msg:
            return 'type_mismatch'
        elif 'overrides nothing' in error_msg:
            return 'override_missing'
        elif 'Conflicting overloads' in error_msg:
            return 'conflicting_overloads'
        elif 'Public-API inline function' in error_msg:
            return 'inline_visibility'
        elif 'expect' in error_msg and 'actual' in error_msg:
            return 'expect_actual_mismatch'
        elif 'Cannot infer type' in error_msg:
            return 'type_inference'
        elif 'deprecated' in error_msg:
            return 'deprecated_usage'
        else:
            return 'other'
            
    def print_summary(self):
        """Print error summary"""
        print("=" * 80)
        print("GRADLE BUILD ERROR SUMMARY")
        print("=" * 80)
        
        # Summary by type
        print("\n## ERRORS BY TYPE:")
        for error_type, errors in sorted(self.errors_by_type.items(), 
                                       key=lambda x: len(x[1]), reverse=True):
            print(f"\n### {error_type.upper()} ({len(errors)} errors)")
            # Show first 3 examples
            for i, error in enumerate(errors[:3]):
                print(f"  - {error['file']}:{error['line']}")
                print(f"    {error['message']}")
            if len(errors) > 3:
                print(f"  ... and {len(errors) - 3} more")
                
        # Summary by file
        print("\n\n## ERRORS BY FILE (Top 10):")
        sorted_files = sorted(self.errors_by_file.items(), 
                            key=lambda x: len(x[1]), reverse=True)[:10]
        for file_path, errors in sorted_files:
            print(f"\n### {file_path} ({len(errors)} errors)")
            # Group by error type in this file
            types = defaultdict(int)
            for error in errors:
                types[error['type']] += 1
            for error_type, count in sorted(types.items(), key=lambda x: x[1], reverse=True):
                print(f"  - {error_type}: {count}")
                
        # Stacktraces
        if self.stacktraces:
            print("\n\n## EXCEPTION STACKTRACES:")
            for i, trace in enumerate(self.stacktraces[:3]):
                print(f"\n### Stacktrace {i+1}:")
                print(trace[:500] + "..." if len(trace) > 500 else trace)
                
        # Total summary
        total_errors = sum(len(errors) for errors in self.errors_by_file.values())
        print(f"\n\n## TOTAL: {total_errors} errors in {len(self.errors_by_file)} files")
        
    def export_markdown(self, output_file: str):
        """Export detailed report as markdown"""
        with open(output_file, 'w') as f:
            f.write("# Gradle Build Error Analysis\n\n")
            
            # Write errors by type
            f.write("## Errors by Type\n\n")
            for error_type, errors in sorted(self.errors_by_type.items(), 
                                           key=lambda x: len(x[1]), reverse=True):
                f.write(f"### {error_type} ({len(errors)} errors)\n\n")
                for error in errors:
                    f.write(f"- `{error['file']}:{error['line']}` - {error['message']}\n")
                f.write("\n")
                
            # Write errors by file
            f.write("## Errors by File\n\n")
            for file_path, errors in sorted(self.errors_by_file.items()):
                f.write(f"### {file_path}\n\n")
                for error in errors:
                    f.write(f"- Line {error['line']}: {error['message']}\n")
                f.write("\n")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python parse-gradle-stacktrace.py <gradle-output-file>")
        sys.exit(1)
        
    parser = StackTraceParser()
    parser.parse_file(sys.argv[1])
    parser.print_summary()
    
    # Export detailed report
    output_file = sys.argv[1].replace('.log', '_analysis.md').replace('.txt', '_analysis.md')
    if not output_file.endswith('_analysis.md'):
        output_file += '_analysis.md'
    parser.export_markdown(output_file)
    print(f"\n\nDetailed report exported to: {output_file}")