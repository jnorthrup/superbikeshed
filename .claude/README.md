# Claude Concurrent Execution Framework

## Overview
This framework enables multiple Claude instances to work concurrently on the SuperBikeShed codebase while preventing conflicts and chaos.

## Architecture

### Directory Isolation
- Each agent owns one directory scope (e.g., `k2script/**`)
- Agents cannot write to sibling directories
- Shared resources (Trikeshed/CoreTypes.kt, build files) are read-only

### Memo-to-Executor Pattern
1. **Agents** analyze their scope and write memos describing needed changes
2. **Executor** processes memos, detects conflicts, and coordinates execution
3. **Executive** (human) approves high-risk changes and resolves conflicts

## Usage

### For Agents
```bash
# Copy the template
cp .claude/memo-template.memo .claude/memos/pending/my-changes.memo

# Fill out the memo with your planned changes
# Submit by placing in memos/pending/ directory
```

### For Executor (Automated)
```bash
# Run executor manually
./.claude/executor.sh

# Or set up cron job for automatic processing
*/5 * * * * /Users/jim/work/superbikeshed/.claude/executor.sh
```

### For Executive (Human)
```bash
# Check for conflicts requiring attention
cat .claude/conflicts.json

# Review execution queue
cat .claude/execution.json

# Check if attention is needed
ls .claude/executive_attention_required
```

## Directory Agents Available

| Agent | Scope | Purpose |
|-------|-------|---------|
| k2script-agent | k2script/** | AI features and annotation parser |
| nexus-agent | nexus/** | Architectural rebuild |
| moneyfan-agent | moneyfan/** | Trading improvements |
| flatton-agent | flatton/** | JSON scanner modernization |
| boingDemo-agent | boingDemo/** | Native audio implementation |
| spacegraph-agent | spacegraph/** | Graph operations |

## Safety Features

- **2-Factor Reach Analysis**: Check direct + transitive impact before changes
- **Conflict Detection**: Executor prevents overlapping modifications
- **Risk Assessment**: Low/medium/high risk levels for different operations
- **Executive Escalation**: Human approval required for high-risk changes
- **Rollback Capability**: All changes planned with undo procedures

## File Structure
```
.claude/
├── commands                    # Agent definitions and scope rules
├── executor.sh                # Timer-based memo processor
├── memo-template.memo         # Template for agent submissions
├── memos/
│   ├── pending/              # Memos awaiting processing
│   ├── processed/            # Completed memos
│   └── failed/               # Failed memo executions
├── conflicts.json            # Conflicts requiring executive review
├── execution.json            # Queued operations awaiting approval
└── executor.log             # Execution history and debugging
```

## Running Phase Integration

This framework supports the current "Running Phase" for Series → Indexed migration:
- Agents can use `import Series as Indexed` in their scope
- Changes are coordinated to prevent dueling architects
- Migration happens organically without system shock