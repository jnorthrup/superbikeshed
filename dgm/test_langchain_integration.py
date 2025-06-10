import os
import json
import pytest
from unittest.mock import Mock, patch
from pathlib import Path

from langchain_integration import DGMIntegration

@pytest.fixture
def temp_output_dir(tmp_path):
    """Create a temporary output directory for tests."""
    output_dir = tmp_path / "test_output"
    output_dir.mkdir()
    return str(output_dir)

@pytest.fixture
def mock_dgm_result():
    """Create a mock DGM result for testing."""
    return {
        "analysis": {
            "complexity": "high",
            "suggestions": ["Improve error handling", "Add documentation"]
        },
        "changes": {
            "files_modified": ["test.py"],
            "lines_changed": 10
        },
        "test_results": {
            "passed": 5,
            "failed": 0,
            "skipped": 1
        },
        "metadata": {
            "timestamp": "2024-03-20T12:00:00Z",
            "version": "1.0.0"
        }
    }

@pytest.fixture
def integration(temp_output_dir):
    """Create a DGMIntegration instance for testing."""
    return DGMIntegration(
        output_dir=temp_output_dir,
        model_name="test-model",
        temperature=0.5,
        max_iterations=2
    )

def test_integration_initialization(integration, temp_output_dir):
    """Test that the integration initializes correctly."""
    assert integration.output_dir == temp_output_dir
    assert integration.orchestrator is not None
    assert os.path.exists(os.path.join(temp_output_dir, "langchain_integration.log"))

@patch('langchain_integration.self_improve')
def test_run_improvement_cycle(mock_self_improve, integration, mock_dgm_result):
    """Test the complete improvement cycle."""
    # Mock the DGM self-improvement result
    mock_self_improve.return_value = mock_dgm_result
    
    # Mock the Langchain orchestrator
    mock_langchain_result = {
        "improvements": ["Added error handling", "Updated documentation"],
        "status": "success"
    }
    integration.orchestrator.run_improvement_loop = Mock(return_value=mock_langchain_result)
    
    # Run the improvement cycle
    result = integration.run_improvement_cycle(
        entry="test_entry",
        parent_commit="test_commit",
        polyglot=False
    )
    
    # Verify the results
    assert "dgm_result" in result
    assert "langchain_result" in result
    assert "improvement_history" in result
    assert result["dgm_result"] == mock_dgm_result
    assert result["langchain_result"] == mock_langchain_result

def test_prepare_initial_state(integration, mock_dgm_result):
    """Test the preparation of initial state for Langchain."""
    initial_state = integration._prepare_initial_state(mock_dgm_result)
    
    assert "dgm_analysis" in initial_state
    assert "code_changes" in initial_state
    assert "test_results" in initial_state
    assert "metadata" in initial_state
    assert initial_state["dgm_analysis"] == mock_dgm_result["analysis"]
    assert initial_state["code_changes"] == mock_dgm_result["changes"]

def test_error_handling(integration):
    """Test error handling in the integration."""
    # Mock a failing DGM improvement
    with patch('langchain_integration.self_improve', side_effect=Exception("Test error")):
        with pytest.raises(Exception) as exc_info:
            integration.run_improvement_cycle(entry="test_entry")
        assert "Test error" in str(exc_info.value)

def test_reset_functionality(integration):
    """Test the reset functionality."""
    # Add some state to the orchestrator
    integration.orchestrator.memory.chat_memory.add_user_message("Test message")
    
    # Reset the integration
    integration.reset()
    
    # Verify the memory is cleared
    assert len(integration.orchestrator.memory.chat_memory.messages) == 0

def test_improvement_history(integration):
    """Test retrieving improvement history."""
    # Mock some improvement history
    mock_history = [
        {"step": 1, "improvement": "First improvement"},
        {"step": 2, "improvement": "Second improvement"}
    ]
    integration.orchestrator.get_improvement_history = Mock(return_value=mock_history)
    
    # Get the history
    history = integration.get_improvement_history()
    
    # Verify the history
    assert history == mock_history
    assert len(history) == 2
    assert history[0]["step"] == 1
    assert history[1]["step"] == 2

if __name__ == "__main__":
    pytest.main([__file__]) 