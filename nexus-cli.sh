#!/bin/bash

echo "🎯 Nexus Interactive CLI"
echo "========================================"
echo
echo "Commands:"
echo "  1/demo     - Run standalone demo (attention distribution)"
echo "  2/agent    - Run agent configuration examples"
echo "  3/help     - Show detailed help"
echo "  4/exit     - Exit"
echo

while true; do
    echo -n "nexus> "
    read cmd
    
    case $cmd in
        1|demo)
            echo "Running attention distribution demo..."
            ./gradlew :nexus:runStandaloneNexus
            ;;
            
        2|agent)
            echo "Running agent examples (requires DSL generation)..."
            echo "To generate DSL: ./gradlew :nexus:build"
            echo "Then check build/generated/ksp/ for generated builders"
            ;;
            
        3|help)
            echo
            echo "=== Nexus Agent Framework ==="
            echo
            echo "Nexus demonstrates advanced architectural patterns:"
            echo "- Autonomous agent coordination"
            echo "- Reactor pattern for event-driven systems"
            echo "- Attention distribution mechanisms"
            echo "- Compositional programming via Trikeshed"
            echo
            echo "The 'demo' shows these patterns in action through"
            echo "a simulated multi-agent system with different layers"
            echo "exchanging messages and distributing attention."
            echo
            ;;
            
        4|exit|quit)
            echo "👋 Goodbye!"
            exit 0
            ;;
            
        *)
            echo "Unknown command: $cmd"
            echo "Type 'help' for more information"
            ;;
    esac
    echo
done