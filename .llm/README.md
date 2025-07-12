# GoalStrikeshed LLM Framework Management

This directory manages all LLM compliance frameworks for the GoalStrikeshed project.

## Directory Structure

```
.llm/
├── README.md              # This file
├── active/                # Currently active framework files
│   ├── CLAUDE.md          # Active Claude framework
│   ├── .cursorrules       # Active Cursor framework
│   ├── .aider.md          # Active Aider framework
│   └── .gemini.md         # Active Gemini framework
├── config/                # Configuration and state
│   ├── current.json       # Current framework status
│   └── settings.json      # Global settings
└── pristine/              # Pristine framework versions
    ├── claude/            # Claude framework
    ├── gemini/            # Gemini framework
    ├── cursor/            # Cursor framework
    ├── aider/             # Aider framework
    ├── setup.sh           # Deployment script
    ├── config.json        # Framework mappings
    └── README.md          # Pristine documentation
```

## Usage

### Deploy Framework
```bash
# Deploy from pristine to active
.llm/pristine/setup.sh claude
.llm/pristine/setup.sh all
```

### Check Status
```bash
# View current active frameworks
ls -la .llm/active/
```

### Customize Framework
```bash
# Edit active framework
vim .llm/active/CLAUDE.md

# Create custom version
cp .llm/active/CLAUDE.md my-custom-claude.md
```

## Framework Status

- **Claude**: Active (CLAUDE.md)
- **Cursor**: Active (.cursorrules)
- **Aider**: Active (.aider.md)
- **Gemini**: Active (.gemini.md)

## Architecture

The `.llm` directory follows a **pristine → active** workflow:

1. **Pristine**: Source of truth frameworks in `.llm/pristine/`
2. **Active**: Currently deployed frameworks in `.llm/active/`

This ensures:
- Version control of pristine frameworks
- Easy deployment and rollback
- Clear separation of concerns 