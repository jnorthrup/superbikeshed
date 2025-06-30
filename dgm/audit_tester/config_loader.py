"""
This module provides functionality to load test configurations for the Audit Tester.

The primary function, `load_config`, reads a YAML file specified by its path
and returns its content as a Python dictionary. It includes error handling
for common issues like file not found or YAML parsing errors.
"""
import yaml

def load_config(config_file_path="config.yaml"):
    """
    Loads test configurations from a YAML file.

    Args:
        config_file_path (str): The path to the configuration YAML file.
                                Defaults to "config.yaml" in the current working directory.

    Returns:
        dict: A dictionary containing the configuration data.
              Returns an empty dict if the file is not found, is empty,
              or if a YAML parsing error occurs.
    """
    try:
        with open(config_file_path, 'r') as f:
            config = yaml.safe_load(f)
            if config is None: # File is empty or only contains comments
                print(f"Info: Configuration file '{config_file_path}' is empty or contains only comments.")
                return {}
            return config
    except FileNotFoundError:
        print(f"Warning: Configuration file '{config_file_path}' not found. Returning empty configuration.")
        return {}
    except yaml.YAMLError as e:
        print(f"Error: Failed to parse YAML in configuration file '{config_file_path}': {e}. Returning empty configuration.")
        return {}
    except Exception as e: # Catch any other unexpected errors during file loading
        print(f"Error: An unexpected error occurred while loading config '{config_file_path}': {e}. Returning empty configuration.")
        return {}

if __name__ == '__main__':
    # Create a dummy config.yaml for testing
    dummy_config_content = """
service_endpoint: "https://api.example.com/v1"
timeout_seconds: 30
tests_to_run:
  - module: HTTPTestModule # Example test definition
    function: check_url_status
    args:
      url: "https://example.com"
      expected_status_code: 200
user_credentials:
  username: "testuser"
  # api_key: "Store sensitive data securely, not directly in config files."
"""
    dummy_config_path = "temp_config_test_main.yaml" # Use a different name to avoid conflict with actual config
    with open(dummy_config_path, 'w') as f:
        f.write(dummy_config_content)

    print(f"--- Testing load_config with '{dummy_config_path}' ---")
    config_data = load_config(dummy_config_path)

    if config_data:
        print("Config loaded successfully:")
        # Using json.dumps for pretty printing the dict
        import json
        print(json.dumps(config_data, indent=2))

        print(f"\nExample access - Service endpoint: {config_data.get('service_endpoint')}")
        print(f"Example access - Tests to run count: {len(config_data.get('tests_to_run', []))}")
    else:
        print("Failed to load dummy config or it was empty.")

    print(f"\n--- Testing load_config with non-existent file 'non_existent_config.yaml' ---")
    non_existent_config = load_config("non_existent_config.yaml")
    assert non_existent_config == {}, f"Expected empty dict for non-existent file, got {non_existent_config}"
    print(f"Result for non-existent file (should be empty dict): {non_existent_config}")

    print(f"\n--- Testing load_config with an empty file 'empty_config_test_main.yaml' ---")
    empty_config_path = "empty_config_test_main.yaml"
    with open(empty_config_path, 'w') as f:
        pass # create empty file
    empty_config = load_config(empty_config_path)
    assert empty_config == {}, f"Expected empty dict for empty file, got {empty_config}"
    print(f"Result for empty file (should be empty dict): {empty_config}")

    print(f"\n--- Testing load_config with an invalid YAML file 'invalid_config_test_main.yaml' ---")
    invalid_yaml_path = "invalid_config_test_main.yaml"
    with open(invalid_yaml_path, 'w') as f:
        f.write("service_endpoint: \"https://api.example.com/v1\"\n  bad_indent: true\nthis is: not: valid: yaml")
    invalid_config = load_config(invalid_yaml_path)
    assert invalid_config == {}, f"Expected empty dict for invalid YAML, got {invalid_config}"
    print(f"Result for invalid YAML (should be empty dict): {invalid_config}")

    # Clean up dummy files
    import os
    try:
        os.remove(dummy_config_path)
        os.remove(empty_config_path)
        os.remove(invalid_yaml_path)
        print("\nCleaned up temporary test files.")
    except OSError as e:
        print(f"Error cleaning up temporary files: {e}")

    print("\n--- End of config_loader tests ---")
