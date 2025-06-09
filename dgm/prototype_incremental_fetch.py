import json # For potentially inspecting raw JSON if needed during debugging

# Assuming the script is run from the parent directory of 'dgm' or PYTHONPATH is set.
# If dgm is a package and this script is inside it, relative imports might be needed
# e.g., from .utils.trikeshed_client import ...
# For this subtask, sticking to the provided import style.
from utils.trikeshed_client import (
    fetch_incremental_data,
    client_side_add_sample_data,
    client_side_reset_sample_data,
    YourPythonDataType # To potentially work with type hints or isinstance checks
)
# from trikeshed_types import Series # Series is used within trikeshed_client and its return values

def run_prototype():
    print("Starting TrikeShed DGM Integration Prototype...\n")

    # 0. Reset any existing simulated data for a clean run
    client_side_reset_sample_data()
    print("Simulated TrikeShed data source reset.\n")

    current_max_timestamp = None

    # 1. Simulate initial data generation in TrikeShed
    # These calls now use the client-side simulation controls
    client_side_add_sample_data("item_A1", 100.5) # Timestamp 1
    client_side_add_sample_data("item_A2", 200.75) # Timestamp 2
    print("Simulated initial data in TrikeShed: item_A1 (ts=1), item_A2 (ts=2)\n")

    # 2. DGM: Initial fetch
    print("--- DGM: Initial Fetch ---")
    update1 = fetch_incremental_data(data_source_name="prototypeSource", since_timestamp=current_max_timestamp)

    if update1 and update1["newData"] and update1["newData"].data:
        print(f"Received {len(update1['newData'].data)} new items.")
        for item in update1["newData"].data:
            # Basic manipulation: Print the received item's details
            print(f"  - ID: {item.id}, Value: {item.value}, Timestamp: {item.timestamp}")
        current_max_timestamp = update1["latestTimestamp"]
        print(f"Updated DGM 'current_max_timestamp' to: {current_max_timestamp}\n")
    else:
        print("No new data or error during initial fetch.\n")
        if update1 and "latestTimestamp" in update1: # Still update timestamp if no data but response is valid
            current_max_timestamp = update1["latestTimestamp"]
            print(f"Updated DGM 'current_max_timestamp' from empty fetch to: {current_max_timestamp}\n")


    # 3. Simulate more data generation in TrikeShed
    client_side_add_sample_data("item_B1", 301.0)  # Timestamp 3
    client_side_add_sample_data("item_B2", 402.25) # Timestamp 4
    print("Simulated more data in TrikeShed: item_B1 (ts=3), item_B2 (ts=4)\n")

    # 4. DGM: Second fetch (incremental)
    print("--- DGM: Second Fetch (Incremental) ---")
    update2 = fetch_incremental_data(data_source_name="prototypeSource", since_timestamp=current_max_timestamp)

    if update2 and update2["newData"] and update2["newData"].data:
        print(f"Received {len(update2['newData'].data)} new items.")
        total_value_received_this_batch = 0
        for item in update2["newData"].data:
            print(f"  - ID: {item.id}, Value: {item.value}, Timestamp: {item.timestamp}")
            total_value_received_this_batch += item.value # Another basic manipulation
        current_max_timestamp = update2["latestTimestamp"]
        print(f"Total value of items in this batch: {total_value_received_this_batch}")
        print(f"Updated DGM 'current_max_timestamp' to: {current_max_timestamp}\n")
    else:
        print("No new data or error during second fetch.\n")
        if update2 and "latestTimestamp" in update2:
            current_max_timestamp = update2["latestTimestamp"]
            print(f"Updated DGM 'current_max_timestamp' from empty fetch to: {current_max_timestamp}\n")

    # 5. DGM: Third fetch (no new data expected)
    print("--- DGM: Third Fetch (No new data expected) ---")
    update3 = fetch_incremental_data(data_source_name="prototypeSource", since_timestamp=current_max_timestamp)

    if update3 and update3["newData"] and update3["newData"].data:
        print(f"Received {len(update3['newData'].data)} new items. (This should not happen if logic is correct)")
        for item in update3["newData"].data:
            print(f"  - ID: {item.id}, Value: {item.value}, Timestamp: {item.timestamp}")
        # current_max_timestamp = update3["latestTimestamp"] # Don't update if unexpected data
        print(f"DGM 'current_max_timestamp' remains: {current_max_timestamp}\n")
    else:
        print("No new data, as expected.")
        if update3 and "latestTimestamp" in update3: # latestTimestamp should still be reported
             print(f"Reported latestTimestamp from TrikeShed: {update3['latestTimestamp']}")
             # current_max_timestamp = update3["latestTimestamp"] # Usually, only update if new data or if ts higher
        print(f"DGM 'current_max_timestamp' remains: {current_max_timestamp}\n")

    print("Prototype finished.")

if __name__ == "__main__":
    run_prototype()
