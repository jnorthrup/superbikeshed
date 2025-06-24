# Audit Tester Modules

This directory contains individual test modules for the Audit Tester framework. Each module is typically a Python class that groups test methods for a specific protocol, service, command-line tool, or other target being audited.

Modules are dynamically loaded by the `TestRunner` based on test definitions in `config.yaml`.
