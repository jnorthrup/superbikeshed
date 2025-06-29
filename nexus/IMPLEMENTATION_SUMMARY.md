## TODO: Automated Structural Search and Replace (SSR) for Kotlin

- Implement automation for Structural Search and Replace (SSR) in Kotlin code within the Nexus project.
- Preferred approach: Develop an IntelliJ IDEA plugin or use the IDE Scripting Console to programmatically apply SSR patterns and replacements across the codebase.
- Goals:
    - Enable batch or CI-driven SSR for refactoring and codebase maintenance.
    - Support TrikeShed-compliant patterns and transformations.
    - Document SSR templates and automation scripts for reproducibility.
- References:
    - [IntelliJ Platform SDK: Structural Search and Replace](https://plugins.jetbrains.com/docs/intellij/structural-search-and-replace.html)
    - [SSR for Kotlin Tutorial](https://www.jetbrains.com/help/idea/tutorial-structural-search-and-replace-in-kotlin.html)

## SSR Automation Tool Integration

- The SSR automation tool is developed in isolation at `tools/ssr-automation/`.
- Nexus does not depend on its internals; all SSR logic is isolated.
- To run automated SSR on the Nexus codebase:
    1. Configure SSR patterns and replacements in the automation tool.
    2. Invoke the tool manually or via CI to apply SSR to Nexus sources.
    3. Review and commit changes as needed.
- See `tools/ssr-automation/README.md` for details and future implementation status. 