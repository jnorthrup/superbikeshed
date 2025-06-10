from typing import List, Dict, Any, Optional
from langchain.agents import AgentExecutor
from langchain.agents.format_scratchpad import format_to_openai_function_messages
from langchain.agents.output_parsers import OpenAIFunctionsAgentOutputParser
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.tools import BaseTool
from langchain.schema import SystemMessage, HumanMessage
from langchain.memory import ConversationBufferMemory
from langchain.chat_models import ChatAnthropic
import os
import json
import logging

class DGMOrchestrator:
    """Orchestrates the DGM self-improvement loop using Langchain."""
    
    def __init__(
        self,
        model_name: str = "claude-3-sonnet-20241022",
        temperature: float = 0.7,
        max_iterations: int = 5,
        memory_key: str = "chat_history",
        return_intermediate_steps: bool = True
    ):
        self.model_name = model_name
        self.temperature = temperature
        self.max_iterations = max_iterations
        self.memory_key = memory_key
        self.return_intermediate_steps = return_intermediate_steps
        
        # Initialize logger
        self.logger = logging.getLogger(__name__)
        
        # Initialize the LLM
        self.llm = ChatAnthropic(
            model=model_name,
            temperature=temperature,
            anthropic_api_key=os.getenv("ANTHROPIC_API_KEY")
        )
        
        # Initialize memory
        self.memory = ConversationBufferMemory(
            memory_key=memory_key,
            return_messages=True
        )
        
        # Initialize tools (to be populated by setup_tools)
        self.tools: List[BaseTool] = []
        
        # Initialize agent executor (to be set up after tools)
        self.agent_executor: Optional[AgentExecutor] = None

    def setup_tools(self, tools: List[BaseTool]) -> None:
        """Set up the tools for the agent."""
        self.tools = tools
        
        # Create the prompt template
        prompt = ChatPromptTemplate.from_messages([
            SystemMessage(content="""You are an AI agent responsible for improving code through a self-improvement loop.
Your goal is to analyze code, identify areas for improvement, and implement those improvements.
Follow these steps:
1. Analyze the current code state
2. Identify potential improvements
3. Implement the most impactful improvements
4. Verify the changes work as expected
5. Document the improvements made

Use the available tools to accomplish these tasks."""),
            MessagesPlaceholder(variable_name=self.memory_key),
            HumanMessage(content="{input}"),
            MessagesPlaceholder(variable_name="agent_scratchpad"),
        ])
        
        # Create the agent
        agent = (
            {
                "input": lambda x: x["input"],
                "agent_scratchpad": lambda x: format_to_openai_function_messages(
                    x["intermediate_steps"]
                ),
                self.memory_key: lambda x: x[self.memory_key],
            }
            | prompt
            | self.llm
            | OpenAIFunctionsAgentOutputParser()
        )
        
        # Create the agent executor
        self.agent_executor = AgentExecutor(
            agent=agent,
            tools=self.tools,
            memory=self.memory,
            verbose=True,
            return_intermediate_steps=self.return_intermediate_steps,
            max_iterations=self.max_iterations
        )

    def run_improvement_loop(
        self,
        initial_state: Dict[str, Any],
        improvement_goal: str
    ) -> Dict[str, Any]:
        """Run the self-improvement loop."""
        if not self.agent_executor:
            raise ValueError("Tools must be set up before running the improvement loop")
        
        # Prepare the initial input
        input_data = {
            "input": f"Improvement Goal: {improvement_goal}\nInitial State: {json.dumps(initial_state, indent=2)}",
            self.memory_key: []
        }
        
        # Run the agent
        try:
            result = self.agent_executor.invoke(input_data)
            self.logger.info(f"Improvement loop completed: {result}")
            return result
        except Exception as e:
            self.logger.error(f"Error in improvement loop: {str(e)}")
            raise

    def get_improvement_history(self) -> List[Dict[str, Any]]:
        """Get the history of improvements made."""
        if not self.memory:
            return []
        
        return self.memory.chat_memory.messages

    def reset_memory(self) -> None:
        """Reset the conversation memory."""
        self.memory.clear() 