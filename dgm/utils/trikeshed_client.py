import requests
import json
from dgm.trikeshed_types import Series # Assuming Series is in trikeshed_types
from dgm.utils.serialization_utils import deserialize_trikeshed_json # For the main response structure if needed

# Placeholder for YourPythonDataType matching Kotlin's YourDataType
class YourPythonDataType:
    def __init__(self, id, value, timestamp):
        self.id = id
        self.value = value
        self.timestamp = timestamp

    @classmethod
    def from_json_dict(cls, data_dict):
        if not isinstance(data_dict, dict):
            raise TypeError(f"Expected dict for YourPythonDataType.from_json_dict, got {type(data_dict)}")
        return cls(data_dict['id'], data_dict['value'], data_dict['timestamp'])

    def __repr__(self):
        return f"YourPythonDataType(id='{self.id}', value={self.value}, timestamp={self.timestamp})"

def parse_your_python_data_type_series(data_dict):
    """
    Parses a dictionary (expected from JSON for Series<YourDataType>) into a DGM Series
    object containing YourPythonDataType instances.
    The input data_dict is expected to be the content of 'newData' from the API response,
    which itself should represent a TrikeShed Series.
    Example: {"data": [{"id": "item1", "value": 10.0, "timestamp": 1}, ...]}
    """
    if not isinstance(data_dict, dict):
        raise TypeError(f"Expected dict for parse_your_python_data_type_series, got {type(data_dict)}")

    items_list = data_dict.get('data', [])
    if not isinstance(items_list, list):
        raise ValueError(f"Expected 'data' field to be a list, got {type(items_list)}")

    parsed_items = [YourPythonDataType.from_json_dict(item) for item in items_list]
    return Series(parsed_items) # Using DGM's Series class

TRIKESHED_API_BASE_URL = "http://localhost:8080" # Example, configure as needed

# Store the simulated data on the client side for now
# This mirrors the structure in TrikeShed's IncrementalDataService for simulation consistency
simulated_sample_data_source = []
simulated_current_timestamp = 0

def client_side_add_sample_data(id_str, value_float):
    """Mirrors TrikeShed's addSampleData for client-side simulation control."""
    global simulated_current_timestamp, simulated_sample_data_source
    simulated_current_timestamp += 1
    new_item = {"id": id_str, "value": value_float, "timestamp": simulated_current_timestamp}
    simulated_sample_data_source.append(new_item)
    # print(f"DGM Client: Added simulated data - {new_item}")
    return new_item

def client_side_reset_sample_data():
    """Mirrors TrikeShed's resetSampleData."""
    global simulated_sample_data_source, simulated_current_timestamp
    simulated_sample_data_source = []
    simulated_current_timestamp = 0
    # print("DGM Client: Simulated data source reset.")


def fetch_incremental_data(data_source_name: str, since_timestamp: int = None):
    endpoint = f"{TRIKESHED_API_BASE_URL}/incremental_data"
    params = {
        "dataSource": data_source_name,
    }
    if since_timestamp is not None:
        params["sinceTimestamp"] = since_timestamp

    # ---- SIMULATED RESPONSE generation logic ----
    # This logic now directly mirrors the filtering that would happen on the TrikeShed server side.
    print(f"DGM Client: Simulating API call to TrikeShed: dataSource='{data_source_name}', sinceTimestamp={since_timestamp}")

    effective_since_timestamp = since_timestamp if since_timestamp is not None else 0

    filtered_data = [
        item for item in simulated_sample_data_source
        if item['timestamp'] > effective_since_timestamp
    ]

    latest_ts_in_response = effective_since_timestamp
    if filtered_data:
        latest_ts_in_response = max(item['timestamp'] for item in filtered_data)

    simulated_json_response = {
        "newData": {"data": filtered_data}, # Matches structure Series<YourDataType> -> {"data": [...]}
        "latestTimestamp": latest_ts_in_response
    }
    # ---- END SIMULATION ----

    try:
        # In a real scenario:
        # response = requests.get(endpoint, params=params, timeout=10) # Added timeout
        # response.raise_for_status()
        # response_data = response.json()

        # Using simulated response for this subtask
        response_data = simulated_json_response

        # Deserialize 'newData' (which is a JSON representation of Series<YourDataType>)
        # into a DGM Series object containing YourPythonDataType instances.
        # The `deserialize_trikeshed_json` function expects a JSON string as its first argument.
        # The `response_data['newData']` is already a Python dictionary.
        # So, we pass it directly to the specific parser `parse_your_python_data_type_series`.

        deserialized_series = parse_your_python_data_type_series(response_data['newData'])

        return {
            "newData": deserialized_series, # This is now a DGM Series object
            "latestTimestamp": response_data['latestTimestamp']
        }

    except requests.exceptions.RequestException as e:
        print(f"Error fetching incremental data from TrikeShed: {e}")
        return None
    # Removed json.JSONDecodeError since we are not calling json.loads on response_data directly here anymore with simulation
    except KeyError as e: # For issues like missing 'newData' or 'latestTimestamp' keys
        print(f"Error processing response from TrikeShed (KeyError): {e}")
        return None
    except TypeError as e: # Catching TypeErrors from our from_json_dict methods
        print(f"Error processing response from TrikeShed (TypeError): {e}")
        return None
    except ValueError as e: # Catching ValueErrors from our parsers
        print(f"Error processing response from TrikeShed (ValueError): {e}")
        return None


# --- Example Usage (conceptual, to be placed in a DGM processing script) ---
if __name__ == '__main__':
    print("Running DGM client example...")

    # Initialize/reset simulated data on the client side to mirror the server
    client_side_reset_sample_data()
    client_side_add_sample_data("item1", 10.0) # ts = 1
    client_side_add_sample_data("item2", 20.0) # ts = 2

    current_max_timestamp = None

    print("\n--- First fetch (all data) ---")
    update1 = fetch_incremental_data("myDataSource", current_max_timestamp)
    if update1 and update1["newData"] and update1["newData"].data:
        print("Update 1 - New data items:", [item.__dict__ for item in update1["newData"].data])
        current_max_timestamp = update1["latestTimestamp"]
        print(f"Update 1 - Latest timestamp from response: {current_max_timestamp}")
    else:
        print("Update 1 - No new data or error.")
        if update1 and "latestTimestamp" in update1:
             current_max_timestamp = update1["latestTimestamp"] # Still update timestamp if no data
             print(f"Update 1 - Latest timestamp from response (no new data): {current_max_timestamp}")


    print(f"\nCurrent max timestamp after Update 1: {current_max_timestamp}")

    # Simulate TrikeShed having more data
    print("\nSimulating more data being added on TrikeShed/client-side-simulation...")
    client_side_add_sample_data("item3", 30.0) # ts = 3
    client_side_add_sample_data("item4", 40.0) # ts = 4

    print("\n--- Second fetch (incremental data) ---")
    update2 = fetch_incremental_data("myDataSource", current_max_timestamp)
    if update2 and update2["newData"] and update2["newData"].data:
        print("Update 2 - New data items:", [item.__dict__ for item in update2["newData"].data])
        current_max_timestamp = update2["latestTimestamp"]
        print(f"Update 2 - Latest timestamp from response: {current_max_timestamp}")

    else:
        print("Update 2 - No new data or error.")
        if update2 and "latestTimestamp" in update2:
             current_max_timestamp = update2["latestTimestamp"]
             print(f"Update 2 - Latest timestamp from response (no new data): {current_max_timestamp}")

    print(f"\nCurrent max timestamp after Update 2: {current_max_timestamp}")

    print("\n--- Third fetch (no new data expected) ---")
    update3 = fetch_incremental_data("myDataSource", current_max_timestamp)
    if update3 and update3["newData"] and update3["newData"].data:
        print("Update 3 - New data items:", [item.__dict__ for item in update3["newData"].data])
        current_max_timestamp = update3["latestTimestamp"]
        print(f"Update 3 - Latest timestamp from response: {current_max_timestamp}")
    else:
        print("Update 3 - No new data or error.")
        if update3 and "latestTimestamp" in update3:
             current_max_timestamp = update3["latestTimestamp"]
             print(f"Update 3 - Latest timestamp from response (no new data): {current_max_timestamp}")

    print(f"\nFinal max timestamp: {current_max_timestamp}")

    # Test case: initial fetch when source is empty
    client_side_reset_sample_data()
    print("\n--- Fourth fetch (source initially empty) ---")
    current_max_timestamp = None
    update4 = fetch_incremental_data("myDataSourceEmpty", current_max_timestamp)
    if update4 and update4["newData"] and update4["newData"].data:
        print("Update 4 - New data items:", [item.__dict__ for item in update4["newData"].data])
        current_max_timestamp = update4["latestTimestamp"]
    else:
        print("Update 4 - No new data or error.")
        if update4 and "latestTimestamp" in update4:
             current_max_timestamp = update4["latestTimestamp"]
    print(f"Timestamp after empty fetch: {current_max_timestamp}") # Should be 0 or None

    client_side_add_sample_data("item5", 50.0) # ts = 1
    print("\n--- Fifth fetch (after adding one item to empty source) ---")
    update5 = fetch_incremental_data("myDataSourceEmpty", current_max_timestamp)
    if update5 and update5["newData"] and update5["newData"].data:
        print("Update 5 - New data items:", [item.__dict__ for item in update5["newData"].data])
        current_max_timestamp = update5["latestTimestamp"]
    else:
        print("Update 5 - No new data or error.")
        if update5 and "latestTimestamp" in update5:
             current_max_timestamp = update5["latestTimestamp"]
    print(f"Timestamp after one item: {current_max_timestamp}") # Should be 1
