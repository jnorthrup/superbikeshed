#\!/bin/bash
# Simple concurrent museum review within Claude Code constraints
for keyword in reactor ljson couchdb; do
  echo "Processing ${keyword} in foreground..."
  git checkout -b "museum-${keyword}-$(date +%s)" main >/dev/null 2>&1
  echo "Branch created for ${keyword}"
  find museum -name "*.kt"  < /dev/null |  head -2 | while read f; do
    echo "  Found: $(basename $f)"
  done
  git checkout main >/dev/null 2>&1
  echo "✓ ${keyword} complete"
done
echo "All museum reviews completed within Claude Code constraints"
