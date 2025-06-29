#!/usr/bin/env python3

import sys

def colored_print(text, color_code):
    """Prints text in a specified ANSI color."""
    print(f"\033[{color_code}m{text}\033[0m")

def main():
    colored_print("🎯 Nexus Initiating Interactive Mode...", "1;36") # Bold Cyan
    colored_print("====================================", "1;36") # Bold Cyan
    colored_print("Hello, I am Nexus, your architectural AI. How can I assist you today?", "0") # Default color

    while True:
        try:
            user_input = input("\033[1;32mYou: \033[0m").strip() # Bold Green for user input prompt

            if user_input.lower() in ["exit", "quit", "bye"]:
                colored_print("Nexus: Goodbye! May your architectures be sound.", "1;36")
                break
            elif "hello" in user_input.lower() or "hi" in user_input.lower():
                colored_print("Nexus: Greetings, Architect! How may I serve your vision?", "0")
            elif "purpose" in user_input.lower():
                colored_print("Nexus: My purpose is to facilitate Universal Development Autonomy. I aim to bridge conceptual design with executable reality.", "0")
            elif "ui" in user_input.lower():
                colored_print("Nexus: My current interface is a conscientious CLI, designed for clarity and direct interaction. We can enhance it with ANSI colors and formatting.", "0")
            else:
                colored_print("Nexus: I am still learning, Architect. Could you rephrase your query or ask about my purpose or UI?", "0")

        except EOFError:
            colored_print("\nNexus: Session terminated unexpectedly. Goodbye!", "1;31") # Bold Red
            break
        except KeyboardInterrupt:
            colored_print("\nNexus: Interrupted by Architect. Terminating session. Goodbye!", "1;31") # Bold Red
            break

if __name__ == "__main__":
    main()
