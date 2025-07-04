#!/bin/bash
# Extract all code blocks (fence pattern) from ../v2superbikeshed_broken/tmp.txt, remove whitespace, and check for unique duplicates (uduff minus ws)

# Extract code blocks between triple backticks
awk '/^```/{f=!f; next} f' ../v2superbikeshed_broken/tmp.txt > code_blocks.txt

# Remove all whitespace from code blocks
tr -d ' \t\n\r' < code_blocks.txt > code_blocks_nowhitespace.txt

# Check for unique and duplicate entries
sort code_blocks_nowhitespace.txt | uniq -c | awk '$1 > 1 {print "DUPLICATE:", $0} $1 == 1 {print "UNIQUE:", $0}' 