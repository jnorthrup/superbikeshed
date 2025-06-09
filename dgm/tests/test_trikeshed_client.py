import unittest
from dgm.utils.trikeshed_client import (
    fetch_incremental_data,
    client_side_add_sample_data,
    client_side_reset_sample_data,
    YourPythonDataType  # To check instance types
)
from dgm.trikeshed_types import Series  # To check instance type of newData

class TestTrikeshedClient(unittest.TestCase):

    def setUp(self):
        # Reset simulated data before each test for isolation
        client_side_reset_sample_data()

    def test_fetch_incremental_data_initial_fetch(self):
        # Add some initial data to the client-side simulation
        item1_sim = client_side_add_sample_data("id1", 10.0) # ts=1
        item2_sim = client_side_add_sample_data("id2", 20.0) # ts=2

        response = fetch_incremental_data(data_source_name="test_source", since_timestamp=None)

        self.assertIsNotNone(response, "Response should not be None")
        self.assertIn("newData", response)
        self.assertIn("latestTimestamp", response)

        new_data_series = response["newData"]
        self.assertIsInstance(new_data_series, Series, "newData should be a Series object")
        self.assertEqual(len(new_data_series.data), 2, "Should fetch all 2 items for initial fetch")
        self.assertEqual(response["latestTimestamp"], item2_sim["timestamp"], "Latest timestamp should match the last added item")

        # Check contents (order should be preserved by simulation)
        self.assertIsInstance(new_data_series.data[0], YourPythonDataType)
        self.assertEqual(new_data_series.data[0].id, item1_sim["id"])
        self.assertEqual(new_data_series.data[0].value, item1_sim["value"])
        self.assertEqual(new_data_series.data[0].timestamp, item1_sim["timestamp"])

        self.assertIsInstance(new_data_series.data[1], YourPythonDataType)
        self.assertEqual(new_data_series.data[1].id, item2_sim["id"])
        self.assertEqual(new_data_series.data[1].value, item2_sim["value"])
        self.assertEqual(new_data_series.data[1].timestamp, item2_sim["timestamp"])


    def test_fetch_incremental_data_subsequent_fetch(self):
        client_side_add_sample_data("id1", 10.0) # ts=1
        item2_sim = client_side_add_sample_data("id2", 20.0) # ts=2

        initial_response = fetch_incremental_data(data_source_name="test_source", since_timestamp=None)
        last_ts = initial_response["latestTimestamp"] # Should be 2

        # Add more data
        item3_sim = client_side_add_sample_data("id3", 30.0) # ts=3
        item4_sim = client_side_add_sample_data("id4", 40.0) # ts=4

        response = fetch_incremental_data(data_source_name="test_source", since_timestamp=last_ts)

        self.assertIsNotNone(response)
        new_data_series = response["newData"]
        self.assertIsInstance(new_data_series, Series)
        self.assertEqual(len(new_data_series.data), 2, "Should fetch only the 2 new items")
        self.assertEqual(response["latestTimestamp"], item4_sim["timestamp"])

        self.assertEqual(new_data_series.data[0].id, item3_sim["id"])
        self.assertEqual(new_data_series.data[1].id, item4_sim["id"])
        self.assertIsInstance(new_data_series.data[0], YourPythonDataType)

    def test_fetch_incremental_data_no_new_data(self):
        item1_sim = client_side_add_sample_data("id1", 10.0) # ts=1
        item2_sim = client_side_add_sample_data("id2", 20.0) # ts=2

        initial_response = fetch_incremental_data(data_source_name="test_source", since_timestamp=None)
        last_ts = initial_response["latestTimestamp"] # Should be 2

        # Fetch again with the same timestamp
        response = fetch_incremental_data(data_source_name="test_source", since_timestamp=last_ts)

        self.assertIsNotNone(response)
        new_data_series = response["newData"]
        self.assertIsInstance(new_data_series, Series)
        self.assertEqual(len(new_data_series.data), 0, "No new items should be fetched")
        self.assertEqual(response["latestTimestamp"], last_ts, "Timestamp should be what was passed in if no new data")

    def test_fetch_incremental_data_empty_source(self):
        # Source is empty (setUp resets it)
        response = fetch_incremental_data(data_source_name="test_source_empty", since_timestamp=None)

        self.assertIsNotNone(response)
        new_data_series = response["newData"]
        self.assertIsInstance(new_data_series, Series)
        self.assertEqual(len(new_data_series.data), 0)
        # The simulation logic in trikeshed_client returns effective_since_timestamp (0 if None)
        self.assertEqual(response["latestTimestamp"], 0)

        # Fetching with a specific timestamp from empty source
        response_with_ts = fetch_incremental_data(data_source_name="test_source_empty", since_timestamp=5)
        self.assertIsNotNone(response_with_ts)
        new_data_series_ts = response_with_ts["newData"]
        self.assertIsInstance(new_data_series_ts, Series)
        self.assertEqual(len(new_data_series_ts.data), 0)
        self.assertEqual(response_with_ts["latestTimestamp"], 5) # Timestamp should be what was passed

if __name__ == '__main__':
    unittest.main()
