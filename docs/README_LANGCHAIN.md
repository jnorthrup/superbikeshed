# DGM Langchain Integration

This module integrates the Langchain framework with the DGM (Dynamic Generation Model) system to enhance code improvement capabilities through AI-driven analysis and modifications.

## Overview

The integration combines the strengths of both systems:
- DGM's code analysis and modification capabilities
- Langchain's orchestration and multi-agent collaboration features

## Components

### 1. Langchain Orchestrator (`langchain_orchestrator.py`)
- Manages the self-improvement loop
- Coordinates between different agents
- Handles state management and history tracking

### 2. Langchain Tools (`langchain_tools.py`)
- Code analysis tools
- Code modification tools
- Test execution tools
- Documentation tools

### 3. Integration Layer (`langchain_integration.py`)
- Bridges DGM and Langchain systems
- Manages the improvement cycle
- Handles error cases and logging

## Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Set up environment variables:
```bash
cp .env.example .env
# Edit .env with your API keys and configuration
```

## Usage

### Basic Usage

```python
from langchain_integration import DGMIntegration

# Initialize the integration
integration = DGMIntegration(
    output_dir="output_selfimprove/",
    model_name="claude-3-sonnet-20241022",
    temperature=0.7,
    max_iterations=5
)

# Run an improvement cycle
result = integration.run_improvement_cycle(
    entry="your_task_entry",
    parent_commit="initial",
    polyglot=False
)
```

### Command Line Interface

```bash
python langchain_integration.py --entry "your_task_entry" --output_dir "output_selfimprove/" --model_name "claude-3-sonnet-20241022"
```

## Testing

Run the test suite:
```bash
pytest test_langchain_integration.py -v
```

## Architecture

The integration follows a layered architecture:

1. **Integration Layer**
   - Manages the overall improvement cycle
   - Coordinates between DGM and Langchain
   - Handles logging and error cases

2. **Orchestration Layer**
   - Manages the Langchain agents
   - Handles state and memory
   - Coordinates tool execution

3. **Tool Layer**
   - Provides specific functionality for code analysis
   - Handles code modifications
   - Manages test execution
   - Generates documentation

## Error Handling

The integration includes comprehensive error handling:
- DGM operation failures
- Langchain execution errors
- Tool execution errors
- State management errors

All errors are logged with appropriate context and severity levels.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 