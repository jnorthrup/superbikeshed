import os
import yaml
from typing import Optional

def get_requirements(instance: dict) -> str:
    """
    Get the requirements.txt content for an instance.
    
    Args:
        instance: Dictionary containing instance data
        
    Returns:
        String containing requirements.txt content
    """
    # Default requirements
    requirements = """pytest>=7.4.0
pytest-asyncio
async_timeout
pytest-mock>=3.11.1
"""
    
    # Add instance-specific requirements if available
    if "requirements" in instance:
        requirements += instance["requirements"]
    
    return requirements

def get_environment_yml(instance: dict, env_name: str) -> str:
    """
    Get the environment.yml content for an instance.
    
    Args:
        instance: Dictionary containing instance data
        env_name: Name of the conda environment
        
    Returns:
        String containing environment.yml content
    """
    # Default environment
    env = {
        "name": env_name,
        "channels": ["conda-forge", "defaults"],
        "dependencies": [
            "python=3.10",
            "pip",
            {
                "pip": [
                    "pytest>=7.4.0",
                    "pytest-asyncio",
                    "async_timeout",
                    "pytest-mock>=3.11.1",
                ]
            }
        ]
    }
    
    # Add instance-specific dependencies if available
    if "environment" in instance:
        env.update(instance["environment"])
    
    return yaml.dump(env) 