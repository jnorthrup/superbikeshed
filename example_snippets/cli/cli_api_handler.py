import os
import sys
import yaml # PyYAML
# To enable the actual API call, you'll need the 'requests' library:
# import requests

CONFIG_FILE_NAME = "cli_config.yaml"
_cached_config = None

def load_config():
    """
    Loads the configuration from cli_config.yaml.
    Handles errors like file not found or misconfiguration.
    Caches the loaded config to avoid redundant file I/O.
    """
    global _cached_config
    if _cached_config:
        return _cached_config

    try:
        if not os.path.exists(CONFIG_FILE_NAME):
            print(f"Error: Configuration file '{CONFIG_FILE_NAME}' not found in the current directory.", file=sys.stderr)
            return None

        with open(CONFIG_FILE_NAME, 'r') as f:
            config = yaml.safe_load(f)

            if not isinstance(config, dict) or \
               "artificial_analysis" not in config or \
               not isinstance(config["artificial_analysis"], dict) or \
               "api" not in config["artificial_analysis"] or \
               not isinstance(config["artificial_analysis"]["api"], dict) or \
               "base_url" not in config["artificial_analysis"]["api"] or \
               "api_key_env_var" not in config["artificial_analysis"]["api"]:
                print(f"Error: Configuration file '{CONFIG_FILE_NAME}' is misconfigured. "
                      "Expected structure: artificial_analysis.api.{{base_url, api_key_env_var}}", file=sys.stderr)
                return None
            _cached_config = config
            return _cached_config
    except yaml.YAMLError as e:
        print(f"Error parsing YAML from '{CONFIG_FILE_NAME}': {e}", file=sys.stderr)
    except Exception as e:
        print(f"Error loading configuration from '{CONFIG_FILE_NAME}': {e}", file=sys.stderr)
    return None

def get_api_key():
    """
    Retrieves the API key from the environment variable specified in the config.
    Handles errors if the config is not loaded or the environment variable is not set.
    """
    config = load_config()
    if not config:
        print("Cannot retrieve API key: Configuration not loaded.", file=sys.stderr)
        return None

    api_key_env_var = config["artificial_analysis"]["api"]["api_key_env_var"]
    api_key = os.getenv(api_key_env_var)

    if not api_key:
        print(f"Error: API key environment variable '{api_key_env_var}' is not set or is empty.", file=sys.stderr)
        return None
    return api_key

def get_api_headers():
    """
    Prepares the API headers using the API key.
    Returns a dictionary of headers {'x-api-key': api_key} or None if an error occurs.
    """
    api_key = get_api_key()
    if not api_key:
        print("Cannot prepare API headers: API key not available.", file=sys.stderr)
        return None

    return {"x-api-key": api_key}

def main():
    """
    Main function to demonstrate loading config, getting API key, preparing headers,
    and optionally making an API call.
    """
    print("--- CLI API Configuration Example ---", file=sys.stderr)
    print("Attempting to load configuration and API key...", file=sys.stderr)

    config = load_config()
    if not config:
        print(f"Failed to load configuration. See previous errors.", file=sys.stderr)
        print(f"Please ensure '{CONFIG_FILE_NAME}' is in the current directory and correctly formatted,", file=sys.stderr)
        print(f"and the environment variable for the API key (e.g., AIA_API_KEY) is set.", file=sys.stderr)
        return

    print(f"Configuration loaded successfully from '{CONFIG_FILE_NAME}'.", file=sys.stderr)
    api_conf = config["artificial_analysis"]["api"]
    base_url = api_conf['base_url']
    api_key_env_var_name = api_conf['api_key_env_var']

    print(f"Using API key from environment variable: '{api_key_env_var_name}'", file=sys.stderr)
    print(f"Base URL: '{base_url}'", file=sys.stderr)

    api_key = get_api_key() # This also implicitly calls load_config if not already called
    if not api_key:
        print(f"Failed to get API key.", file=sys.stderr)
        print(f"Please ensure the environment variable '{api_key_env_var_name}' is set.", file=sys.stderr)
        return

    print(f"Successfully retrieved API key (first 5 chars): '{api_key[:5]}...'", file=sys.stderr)

    headers = get_api_headers()
    if not headers:
        print("Failed to generate API headers.", file=sys.stderr)
        return

    print(f"Generated API headers: {headers}", file=sys.stderr)

    # --- Section for making an actual API call (commented out by default) ---
    # To enable this section:
    # 1. Install the 'requests' library: pip install requests
    # 2. Uncomment the 'import requests' line at the top of this file.
    # 3. Uncomment the block below.
    # 4. Ensure AIA_API_KEY environment variable is set with a valid key.
    """
    print("\n--- Attempting API Call to /data/llms/models ---", file=sys.stderr)
    try:
        models_endpoint = "/data/llms/models"
        full_url = base_url.rstrip('/') + models_endpoint

        print(f"Making GET request to: {full_url}", file=sys.stderr)

        # Ensure 'requests' is imported if this block is uncommented
        # response = requests.get(full_url, headers=headers, timeout=10)

        # print(f"Response Status Code: {response.status_code}", file=sys.stderr)

        # if response.ok:
        #     print("Response JSON (first 500 chars):", file=sys.stderr)
        #     # response_text = response.text
        #     # print(response_text[:500] + ("..." if len(response_text) > 500 else ""), file=sys.stderr)
        #     # Or, if you expect JSON and want to print it prettily:
        #     try:
        #         response_json = response.json()
        #         import json
        #         print(json.dumps(response_json, indent=2)[:500] + "...", file=sys.stderr)
        #     except ValueError: # Includes json.JSONDecodeError
        #         print("Response is not valid JSON, printing raw text (first 500 chars):", file=sys.stderr)
        #         print(response.text[:500] + ("..." if len(response.text) > 500 else ""), file=sys.stderr)
        # else:
        #     print(f"API call failed. Response Text:", file=sys.stderr)
        #     print(response.text, file=sys.stderr)

    # except requests.exceptions.RequestException as e:
    #     print(f"API call error: {e}", file=sys.stderr)
    # except NameError as e:
    #     if 'requests' in str(e):
    #        print("Error: The 'requests' library is not imported. Please uncomment 'import requests' at the top.", file=sys.stderr)
    #     else:
    #        print(f"An unexpected error occurred: {e}", file=sys.stderr)
    # except Exception as e:
    #     print(f"An unexpected error occurred during the API call attempt: {e}", file=sys.stderr)
    """
    # --- End of API call section ---

    print("\n--- End of Example ---", file=sys.stderr)

    print("\nTo run this example:", file=sys.stderr)
    print("1. Ensure you have Python 3 installed.", file=sys.stderr)
    print("2. Install PyYAML: pip install PyYAML", file=sys.stderr)
    print("3. Create/place 'cli_config.yaml' in the same directory as this script:", file=sys.stderr)
    print("   artificial_analysis:", file=sys.stderr)
    print("     api:", file=sys.stderr)
    print("       base_url: \"https://artificialanalysis.ai/api/v2\"") # Or your actual base URL
    print("       api_key_env_var: \"AIA_API_KEY\"", file=sys.stderr)
    print("4. Set the API key environment variable: export AIA_API_KEY=\"your_actual_api_key_here\"", file=sys.stderr)
    print("   (For Windows: set AIA_API_KEY=\"your_actual_api_key_here\")", file=sys.stderr)
    print("5. Run this Python script: python example_snippets/cli/cli_api_handler.py", file=sys.stderr)
    print("\nTo test the actual API call to /data/llms/models:", file=sys.stderr)
    print("1. Follow steps 1-4 above.", file=sys.stderr)
    print("2. Install the 'requests' library: pip install requests", file=sys.stderr)
    print("3. Uncomment the 'import requests' line at the top of the script.", file=sys.stderr)
    print("4. Uncomment the entire '--- Attempting API Call ... ---' block in the main() function.", file=sys.stderr)
    print("5. Ensure your AIA_API_KEY is valid and has permissions for the endpoint.", file=sys.stderr)
    print("6. Run the script: python example_snippets/cli/cli_api_handler.py", file=sys.stderr)


if __name__ == "__main__":
    main()
