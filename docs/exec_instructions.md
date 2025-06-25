# Executive Instructions for Later Execution

## Active State Snapshot
- **Date**: 2025-06-22
- **Context**: Hot development session - need to preserve state for continuation

## High Priority Items (Execute First)
1. **Realtime Dense Bitgraph** - IN PROGRESS
   - Target: nexus/ directory
   - Goal: kotlin symbol metadata in register-packed Join<A,B> structures
   - Status: Started analysis phase
   
2. **Release Engineering Path** - PENDING HIGH
   - Map: bitpacking → kotlin-elements → ksp → nexus → k2script
   - Deliverable: Complete pipeline documentation and automation
   
## Medium Priority Items
3. **boingDemo Native Audio** - PENDING
   - Scope: boingDemo/ directory only
   - Task: Complete cross-platform audio implementation
   
4. **flatton Scanner Migration** - PENDING  
   - Scope: flatton/ directory only
   - Replace: SimdJsonScanner → kotlinx-serialization-scanner

## Completed
- ✅ GitHub app installation for release engineering

## Critical Notes
- Repository has uncommitted changes - commit before major operations
- Agents are directory-isolated to prevent conflicts
- Use `--console=plain --no-daemon` for gradle operations
- Follow Series→Indexed migration pattern when touching files

## Execution Commands Ready
```bash
# Resume hot session
bash .claude/executor.sh
# Check agent statuses in .claude/execution.json
```