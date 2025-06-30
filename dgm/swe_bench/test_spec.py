import os
from dataclasses import dataclass
from typing import List, Optional

@dataclass
class TestSpec:
    instance_id: str
    repo: str
    repo_script_list: List[str]
    eval_script_list: List[str]
    env_script_list: List[str]
    arch: str = "x86_64"

    def get_instance_container_name(self, run_id: str) -> str:
        return f"dgm_{self.instance_id}_{run_id}"

    def get_env_script(self) -> str:
        return "\n".join(self.env_script_list)

    def get_eval_script(self) -> str:
        return "\n".join(self.eval_script_list)

    def get_repo_script(self) -> str:
        return "\n".join(self.repo_script_list)

def make_test_spec(entry: dict) -> TestSpec:
    """
    Create a TestSpec object from a dataset entry.
    
    Args:
        entry: Dictionary containing test specification data
        
    Returns:
        TestSpec object with the test specification
    """
    instance_id = entry["instance_id"]
    repo = entry["repo"]
    
    # Get the language-specific test commands
    language = entry.get("language", "python").lower()
    if language == "python":
        test_cmd = "python -m pytest"
    elif language == "rust":
        test_cmd = "cargo test"
    elif language == "go":
        test_cmd = "go test ./..."
    elif language == "javascript":
        test_cmd = "npm test"
    elif language == "c++":
        test_cmd = "make test"
    elif language == "java":
        test_cmd = "mvn test"
    else:
        test_cmd = "python -m pytest"  # Default to Python
    
    # Create the evaluation script
    eval_script = f"""#!/bin/bash
set -e
cd /testbed
{test_cmd}
"""
    
    # Create the environment setup script
    env_script = f"""#!/bin/bash
set -e
cd /testbed
"""
    
    # Create the repository setup script
    repo_script = f"""#!/bin/bash
set -e
cd /testbed
git clone {repo} .
"""
    
    return TestSpec(
        instance_id=instance_id,
        repo=repo,
        repo_script_list=[repo_script],
        eval_script_list=[eval_script],
        env_script_list=[env_script],
    ) 