#!/usr/bin/env python3
"""
Extract stacktrace errors with layered context
- 100 total lines
- 20 lines of context each
- 5 layers deep
"""

import re
import sys
from pathlib import Path
from typing import List, Tuple, Dict

class StacktraceContextExtractor:
    def __init__(self):
        self.errors = []
        self.file_contents = {}
        
    def extract_errors_from_log(self, log_file: str) -> List[Dict]:
        """Extract all error locations from gradle log"""
        errors = []
        # Try both patterns - with and without file:// prefix
        error_patterns = [
            r'e: file://(/[^:]+):(\d+):(\d+)\s+(.+)',
            r'e: (/[^:]+):(\d+):(\d+)\s+(.+)'
        ]
        
        with open(log_file, 'r') as f:
            content = f.read()
            
        # Also try to extract from the raw output we saw
        lines = content.split('\n')
        for line in lines:
            for pattern in error_patterns:
                match = re.match(pattern, line)
                if match:
                    errors.append({
                        'file': match.group(1),
                        'line': int(match.group(2)),
                        'column': int(match.group(3)),
                        'message': match.group(4),
                        'original': line.strip()
                    })
                    break
        return errors
    
    def load_file_content(self, filepath: str) -> List[str]:
        """Load and cache file content"""
        if filepath not in self.file_contents:
            try:
                with open(filepath, 'r') as f:
                    self.file_contents[filepath] = f.readlines()
            except:
                self.file_contents[filepath] = []
        return self.file_contents[filepath]
    
    def extract_context(self, filepath: str, line_num: int, context_lines: int = 10) -> List[str]:
        """Extract context around a specific line"""
        lines = self.load_file_content(filepath)
        if not lines:
            return []
            
        start = max(0, line_num - context_lines - 1)
        end = min(len(lines), line_num + context_lines)
        
        context = []
        for i in range(start, end):
            prefix = ">>>" if i == line_num - 1 else "   "
            context.append(f"{i+1:4d}{prefix} {lines[i].rstrip()}")
        return context
    
    def extract_layered_context(self, log_file: str, num_layers: int = 5, lines_per_layer: int = 20):
        """Extract errors in layers with context"""
        errors = self.extract_errors_from_log(log_file)
        
        # Group errors by file
        errors_by_file = {}
        for error in errors:
            file_path = error['file']
            if file_path not in errors_by_file:
                errors_by_file[file_path] = []
            errors_by_file[file_path].append(error)
        
        # Sort files by error count
        sorted_files = sorted(errors_by_file.items(), key=lambda x: len(x[1]), reverse=True)
        
        output = []
        output.append("=" * 80)
        output.append("STACKTRACE CONTEXT EXTRACTION - 5 LAYERS x 20 LINES")
        output.append("=" * 80)
        
        layer = 0
        total_lines = 0
        max_lines = 100
        
        for file_path, file_errors in sorted_files:
            if layer >= num_layers or total_lines >= max_lines:
                break
                
            layer += 1
            output.append(f"\n{'#' * 40}")
            output.append(f"# LAYER {layer}: {file_path}")
            output.append(f"# Errors: {len(file_errors)}")
            output.append(f"{'#' * 40}")
            
            # Take first few errors from this file
            for i, error in enumerate(file_errors[:3]):  # Max 3 errors per file
                if total_lines >= max_lines:
                    break
                    
                output.append(f"\n## Error {layer}.{i+1}: Line {error['line']}")
                output.append(f"## {error['message']}")
                output.append("-" * 60)
                
                # Get context
                context = self.extract_context(file_path, error['line'], context_lines=lines_per_layer//2)
                
                # Add context lines
                for line in context:
                    output.append(line)
                    total_lines += 1
                    if total_lines >= max_lines:
                        break
                        
                output.append("-" * 60)
        
        output.append(f"\nTotal lines extracted: {total_lines}")
        output.append(f"Layers processed: {layer}")
        
        return "\n".join(output)
    
    def save_context_report(self, log_file: str, output_file: str):
        """Save the layered context report"""
        report = self.extract_layered_context(log_file)
        with open(output_file, 'w') as f:
            f.write(report)
        print(f"Context report saved to: {output_file}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python extract-stacktrace-context.py <gradle-log-file> [output-file]")
        sys.exit(1)
    
    log_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else log_file.replace('.log', '_context.txt')
    
    extractor = StacktraceContextExtractor()
    extractor.save_context_report(log_file, output_file)