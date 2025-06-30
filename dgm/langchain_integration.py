import os
import json
import logging
from typing import Dict, Any, Optional
from pathlib import Path

from langchain_orchestrator import DGMOrchestrator
from langchain_tools import get_dgm_tools
from self_improve_step import self_improve
from utils.docker_utils import setup_logger

class DGMIntegration:
    """Integrates Langchain orchestrator with DGM system."""
    
    def __init__(
        self,
        output_dir: str = "output_selfimprove/",
        model_name: str = "claude-3-sonnet-20241022",
        temperature: float = 0.7,
        max_iterations: int = 5
    ):
        self.output_dir = output_dir
        self.logger = setup_logger(os.path.join(output_dir, "langchain_integration.log"))
        
        # Initialize the orchestrator
        self.orchestrator = DGMOrchestrator(
            model_name=model_name,
            temperature=temperature,
            max_iterations=max_iterations
        )
        
        # Set up tools
        self.orchestrator.setup_tools(get_dgm_tools())
    
    def run_improvement_cycle(
        self,
        entry: str,
        parent_commit: str = "initial",
        test_task_list: Optional[list] = None,
        polyglot: bool = False
    ) -> Dict[str, Any]:
        """Run a complete improvement cycle using both DGM and Langchain."""
        try:
            # Step 1: Run DGM self-improvement
            self.logger.info(f"Starting DGM self-improvement for entry: {entry}")
            dgm_result = self._run_dgm_improvement(
                entry=entry,
                parent_commit=parent_commit,
                test_task_list=test_task_list,
                polyglot=polyglot
            )
            
            # Step 2: Analyze DGM results with Langchain
            self.logger.info("Analyzing DGM results with Langchain")
            initial_state = self._prepare_initial_state(dgm_result)
            improvement_goal = f"Improve the code based on DGM analysis for entry: {entry}"
            
            # Step 3: Run Langchain improvement loop
            langchain_result = self.orchestrator.run_improvement_loop(
                initial_state=initial_state,
                improvement_goal=improvement_goal
            )
            
            # Step 4: Combine and return results
            return {
                "dgm_result": dgm_result,
                "langchain_result": langchain_result,
                "improvement_history": self.orchestrator.get_improvement_history()
            }
            
        except Exception as e:
            self.logger.error(f"Error in improvement cycle: {str(e)}")
            raise
    
    def _run_dgm_improvement(
        self,
        entry: str,
        parent_commit: str,
        test_task_list: Optional[list],
        polyglot: bool
    ) -> Dict[str, Any]:
        """Run DGM self-improvement step."""
        try:
            result = self_improve(
                parent_commit=parent_commit,
                output_dir=self.output_dir,
                entry=entry,
                test_task_list=test_task_list,
                polyglot=polyglot
            )
            return result
        except Exception as e:
            self.logger.error(f"Error in DGM improvement: {str(e)}")
            raise
    
    def _prepare_initial_state(self, dgm_result: Dict[str, Any]) -> Dict[str, Any]:
        """Prepare initial state for Langchain from DGM results."""
        return {
            "dgm_analysis": dgm_result.get("analysis", {}),
            "code_changes": dgm_result.get("changes", {}),
            "test_results": dgm_result.get("test_results", {}),
            "metadata": dgm_result.get("metadata", {})
        }
    
    def get_improvement_history(self) -> list:
        """Get the complete improvement history."""
        return self.orchestrator.get_improvement_history()
    
    def reset(self) -> None:
        """Reset the integration state."""
        self.orchestrator.reset_memory()

def main():
    """Main entry point for the integration."""
    import argparse
    
    parser = argparse.ArgumentParser(description="DGM-Langchain Integration")
    parser.add_argument("--entry", required=True, help="Task entry to improve")
    parser.add_argument("--parent_commit", default="initial", help="Parent commit hash")
    parser.add_argument("--output_dir", default="output_selfimprove/", help="Output directory")
    parser.add_argument("--model_name", default="claude-3-sonnet-20241022", help="Model name")
    parser.add_argument("--temperature", type=float, default=0.7, help="Model temperature")
    parser.add_argument("--max_iterations", type=int, default=5, help="Max iterations")
    parser.add_argument("--polyglot", action="store_true", help="Run in polyglot mode")
    
    args = parser.parse_args()
    
    # Create output directory if it doesn't exist
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Initialize and run integration
    integration = DGMIntegration(
        output_dir=args.output_dir,
        model_name=args.model_name,
        temperature=args.temperature,
        max_iterations=args.max_iterations
    )
    
    result = integration.run_improvement_cycle(
        entry=args.entry,
        parent_commit=args.parent_commit,
        polyglot=args.polyglot
    )
    
    # Save results
    output_file = os.path.join(args.output_dir, "integration_result.json")
    with open(output_file, "w") as f:
        json.dump(result, f, indent=2)
    
    print(f"Integration completed. Results saved to {output_file}")

if __name__ == "__main__":
    main() 