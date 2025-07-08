import json
import random

def run_llm_simulation(iteration, previous_choice=None):
    print(f"--- LLM Simulation Iteration {iteration} ---")
    
    choices = ["tool_call_A", "tool_call_B", "choice_C", "choice_D"]
    
    print("LLM presents choices:")
    for i, choice in enumerate(choices):
        print(f"  {i+1}. {choice}")
        
    # Simulate LLM making a tool call, explicitly as a command-line execution in sandbox/branch
    simulated_tool_action = random.choice([c for c in choices if c.startswith("tool_call_")])
    simulated_cmdline_tool_call = f"run_shell_command(command=\"{simulated_tool_action}_cmd\", description=\"Executing {simulated_tool_action} in sandbox branch\")"
    print(f"LLM decides to make a tool call (cmdline in sandbox in branch): {simulated_cmdline_tool_call}")
    
    # Simulate tool execution and result, reflecting the cmdline nature
    tool_result = f"{{ \"tool_output\": \"Result of {simulated_tool_action} from sandbox cmdline at iteration {iteration}\" }}"
    print(f"Tool executed. Result: {tool_result}")
    
    # Simulate LLM choosing a next action based on choices and tool result
    remaining_choices = [c for c in choices if c != simulated_tool_action]
    next_action = random.choice(remaining_choices)
    
    print(f"LLM chooses next action: {next_action}")
    print(f"--- End LLM Simulation Iteration {iteration} ---\n")
    
    return {
        "iteration": iteration,
        "choices_presented": choices,
        "simulated_tool_call": simulated_cmdline_tool_call,
        "tool_result": tool_result,
        "next_action_chosen": next_action
    }

if __name__ == "__main__":
    # Run for 3 iterations to demonstrate recurrence
    for i in range(1, 4):
        run_llm_simulation(i)