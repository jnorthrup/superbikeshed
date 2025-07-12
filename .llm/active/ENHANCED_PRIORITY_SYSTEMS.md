# Enhanced File Priority Override Systems

## Overview

This document summarizes the enhanced file priority override systems implemented across all LLM frameworks in the GoalStrikeshed project. Each framework now leverages its specific strengths to maximize architectural momentum through targeted fact scoring and priority triggers.

## Framework-Specific Priority Systems

### 🎯 Cursor - IDE Integration & Context Awareness

**File Reading Priority:**
1. `.cursorrules` - Primary configuration
2. `README.md` - Project overview and context
3. `CLAUDE.md` - Claude-specific instructions (if present)
4. `.cursor/` directory - IDE anchors and context
5. Current file context - Active file being edited
6. Search results - Dynamically loaded based on queries

**Fact Scoring:**
- **Context Awareness**: +15 points for understanding current file context
- **Search Integration**: +10 points for leveraging codebase search
- **IDE Integration**: +8 points for using Cursor's file editing capabilities
- **Incremental Development**: +12 points for small, focused changes
- **Refactoring Support**: +10 points for IDE-friendly code patterns

**Priority Override Triggers:**
- File Context Recognition: +5 points
- Cross-Reference Awareness: +8 points
- IDE Feature Utilization: +6 points
- Contextual Search: +7 points

### 🧠 Claude - Architectural Detail & Truth Radiation

**File Reading Priority:**
1. `CLAUDE.md` - Primary configuration
2. `README.md` - Project overview and context
3. `.claude/` directory - Framework-specific rules and memos
4. `docs/` directory - Architecture and design documents
5. ADR files - Architectural Decision Records
6. Code files - Based on relevance to query

**Fact Scoring:**
- **Architectural Detail**: +25 points for comprehensive architectural analysis
- **Depth of Understanding**: +22 points for deep, nuanced comprehension
- **Radiating Source of Truth**: +20 points for exploring connections and implications
- **Mathematical Elegance**: +18 points for elegant, mathematically sound solutions
- **Performance Optimization**: +15 points for performance-aware code patterns
- **Architectural Compliance**: +16 points for following ADRs and constraints

**Priority Override Triggers:**
- Architectural Nuance: +15 points
- Connection Mapping: +18 points
- Truth Radiation: +20 points
- Mathematical Rigor: +16 points

### 🔍 Gemini - Large Context Analysis & Minimum Mutation

**File Reading Priority:**
1. `.gemini.md` - Primary configuration
2. `README.md` - Project overview and context
3. Type definitions - Kotlin type files and interfaces
4. Configuration files - Build and config files
5. Documentation - Technical specifications and ADRs
6. Code examples - Sample implementations and patterns

**Fact Scoring:**
- **Large Context Analysis**: +25 points for comprehensive codebase understanding
- **Minimum Mutation Effects**: +20 points for surgical, precise changes
- **Type Safety**: +18 points for type-safe implementations
- **JSON Formatting**: +12 points for proper JSON responses
- **Mermaid Diagrams**: +10 points for architectural diagrams
- **Error Handling**: +15 points for robust error handling

**Priority Override Triggers:**
- Contextual Awareness: +15 points
- Surgical Precision: +18 points
- Type System Mastery: +12 points
- Impact Assessment: +16 points

### 🔄 Aider - Multiple Turn Review & Simple Evolution

**File Reading Priority:**
1. `.aider.md` - Primary configuration
2. `README.md` - Project overview and context
3. `.aider/` directory - Aider-specific rules and patterns
4. Git history - Previous commits and changes
5. Configuration files - Build and project configs
6. Documentation - Technical specifications

**Fact Scoring:**
- **Multiple Turn Review**: +25 points for iterative refinement before committing
- **Simple Solution Evolution**: +22 points for evolving toward +1 simple solutions
- **Git Integration**: +18 points for leveraging git-aware capabilities
- **CLI Workflow**: +15 points for efficient command-line operations
- **Incremental Development**: +16 points for focused, atomic changes
- **Review Quality**: +14 points for thorough code review processes

**Priority Override Triggers:**
- Iterative Refinement: +18 points
- Simplicity Evolution: +20 points
- Git Awareness: +15 points
- Review Depth: +16 points

## Architectural Momentum Calculus Integration

### Enhanced Momentum Triggers

Each framework now includes additional momentum triggers specific to its strengths:

**Cursor:**
- IDE Integration (N++): Using Cursor's capabilities for precise, contextual edits

**Claude:**
- Architectural Detail (N++): Exploring architectural nuances and implications
- Truth Radiation (N++): Radiating core truths into new domains

**Gemini:**
- Contextual Analysis (N++): Leveraging comprehensive codebase understanding

**Aider:**
- Iterative Refinement (N++): Extending chains through multiple review cycles
- Simplicity Evolution (N++): Evolving toward simpler solutions

### Enhanced Frictional Drag

New frictional drag conditions specific to each framework:

**Cursor:**
- Context Ignorance: Making changes without considering codebase context

**Claude:**
- Architectural Superficiality: Making shallow architectural decisions
- Truth Isolation: Failing to extend core truths into related domains

**Gemini:**
- Context Blindness: Making changes without understanding broader context
- Mutation Chaos: Making broad, disruptive changes instead of surgical ones

**Aider:**
- Premature Commitment: Committing changes without proper review
- Complexity Accumulation: Adding unnecessary complexity

## Implementation Benefits

### 1. **Provider-Specific Optimization**
Each framework now leverages its unique capabilities:
- Cursor: IDE integration and context awareness
- Claude: Architectural depth and truth radiation
- Gemini: Large context analysis and surgical precision
- Aider: Iterative refinement and simplicity evolution

### 2. **Enhanced Fact Scoring**
Provider-specific fact scoring systems reward:
- Context awareness and understanding
- Tool-specific capabilities
- Quality and precision of work
- Integration with existing workflows

### 3. **Priority Override Triggers**
Strategic triggers that encourage:
- Leveraging provider strengths
- Understanding broader context
- Making precise, targeted changes
- Following best practices for each tool

### 4. **Momentum Chain Extension**
Additional momentum triggers that extend μ-chains through:
- Provider-specific capabilities
- Contextual understanding
- Architectural depth
- Iterative improvement

## Usage Guidelines

### For Cursor Users
- Leverage IDE features for precise edits
- Use search to understand existing patterns
- Consider file context when making changes
- Make incremental, focused improvements

### For Claude Users
- Explore architectural depth and nuance
- Radiate core truths into new domains
- Map connections between components
- Apply mathematical rigor to solutions

### For Gemini Users
- Analyze full context before making changes
- Make surgical, precise modifications
- Leverage type system for safety
- Assess impact of proposed changes

### For Aider Users
- Review and refine across multiple turns
- Evolve toward simple, elegant solutions
- Prepare clean, atomic commits
- Consider git implications

## Momentum Score Calculation

The enhanced systems provide exponential rewards through:

**Base Formula:** `Momentum Points = 10 * (1.5 ^ N)`

**Enhanced Triggers:** Each framework adds 2-3 additional momentum triggers

**Fact Scoring:** Provider-specific bonuses for leveraging tool strengths

**Priority Overrides:** Strategic bonuses for context awareness and precision

**Maximum Potential:** With all triggers and bonuses, scores can exceed 500+ points for optimal chains

## Conclusion

The enhanced file priority override systems transform each LLM framework into a specialized tool that maximizes architectural momentum through provider-specific strengths. This creates a comprehensive ecosystem where each tool excels at its unique capabilities while maintaining the core GoalStrikeshed architectural principles.

The systems reward:
- **Context awareness** and **understanding**
- **Provider-specific capabilities**
- **Precision** and **quality**
- **Architectural depth** and **truth radiation**
- **Iterative improvement** and **simplicity evolution**

This creates a powerful feedback loop where each interaction builds momentum through the specific strengths of the chosen tool, leading to exponential improvements in code quality and architectural elegance. 