import unittest
import json
from dgm.trikeshed_types import Tensor, Series, Join, HttpResponseData
# YourPythonDataType is defined in trikeshed_client, so importing from there for consistency with the prompt
from dgm.utils.trikeshed_client import YourPythonDataType
from dgm.utils.serialization_utils import (
    deserialize_trikeshed_json,
    parse_tensor_from_dict, # This is Tensor.from_json_dict
    parse_series_from_dict, # This is Series.from_json_dict
    parse_http_response_data_from_dict # This is HttpResponseData.from_json_dict
)

# Define a helper parser for YourPythonDataType for Join tests, as per prompt
def parse_your_python_data_type_from_dict(data_dict):
    if data_dict is None: return None
    # Assuming YourPythonDataType.from_json_dict exists and works as expected
    return YourPythonDataType.from_json_dict(data_dict)

class TestTrikeshedTypesAndSerialization(unittest.TestCase):

    def test_tensor_deserialization_success(self):
        json_string = '{"shape": [2, 2], "data": [1, 2, 3, 4]}'
        # Pass the class method directly as the parser
        tensor = deserialize_trikeshed_json(json_string, Tensor.from_json_dict)
        self.assertIsNotNone(tensor)
        self.assertIsInstance(tensor, Tensor)
        self.assertEqual(tensor.shape, [2, 2])
        self.assertEqual(tensor.data, [1, 2, 3, 4])

        json_string_empty_data = '{"shape": [0], "data": []}' # Test with empty data, shape [0]
        tensor_empty = deserialize_trikeshed_json(json_string_empty_data, Tensor.from_json_dict)
        self.assertIsNotNone(tensor_empty)
        self.assertEqual(tensor_empty.shape, [0])
        self.assertEqual(tensor_empty.data, [])

        json_string_2d_empty_dim = '{"shape": [2, 0], "data": []}' # Test with 2D tensor, one dim is 0
        tensor_2d_empty_dim = deserialize_trikeshed_json(json_string_2d_empty_dim, Tensor.from_json_dict)
        self.assertIsNotNone(tensor_2d_empty_dim)
        self.assertEqual(tensor_2d_empty_dim.shape, [2,0])
        self.assertEqual(tensor_2d_empty_dim.data, [])


    def test_series_deserialization_success(self):
        json_string = '{"data": ["a", "b", "c"]}'
        series = deserialize_trikeshed_json(json_string, Series.from_json_dict)
        self.assertIsNotNone(series)
        self.assertIsInstance(series, Series)
        self.assertEqual(series.data, ["a", "b", "c"])

        json_string_empty = '{"data": []}'
        series_empty = deserialize_trikeshed_json(json_string_empty, Series.from_json_dict)
        self.assertIsNotNone(series_empty)
        self.assertEqual(series_empty.data, [])

    def test_join_deserialization_simple_dicts(self):
        # Test Join of two simple dicts
        json_string_simple_join = '{"a": {"keyA": "valA"}, "b": {"keyB": "valB"}}'
        simple_join = deserialize_trikeshed_json(json_string_simple_join, Join.from_json_dict) # Uses default None parsers
        self.assertIsNotNone(simple_join)
        self.assertIsInstance(simple_join.a, dict)
        self.assertEqual(simple_join.a['keyA'], 'valA')
        self.assertIsInstance(simple_join.b, dict)
        self.assertEqual(simple_join.b['keyB'], 'valB')

    def test_join_deserialization_nested_series_of_your_type(self):
        json_string_join = '''
        {
            "a": {"data": [{"id": "j_item1", "value": 1.0, "timestamp": 10}]},
            "b": {"data": [{"id": "j_item2", "value": 2.0, "timestamp": 20}]}
        }
        '''
        # Define a parser for a Series of YourPythonDataType
        def series_of_your_data_type_parser(series_dict_data):
            if series_dict_data is None: return None
            items = [YourPythonDataType.from_json_dict(item) for item in series_dict_data.get("data", [])]
            return Series(items)

        joined_obj = deserialize_trikeshed_json(
            json_string_join,
            lambda d: Join.from_json_dict(d,
                                          type_a_parser=series_of_your_data_type_parser,
                                          type_b_parser=series_of_your_data_type_parser)
        )
        self.assertIsNotNone(joined_obj)
        self.assertIsInstance(joined_obj.a, Series)
        self.assertTrue(len(joined_obj.a.data) > 0)
        self.assertIsInstance(joined_obj.a.data[0], YourPythonDataType)
        self.assertEqual(joined_obj.a.data[0].id, "j_item1")

        self.assertIsInstance(joined_obj.b, Series)
        self.assertTrue(len(joined_obj.b.data) > 0)
        self.assertIsInstance(joined_obj.b.data[0], YourPythonDataType)
        self.assertEqual(joined_obj.b.data[0].id, "j_item2")

    def test_your_python_data_type_deserialization_direct(self):
        json_string = '{"id": "direct_item", "value": 99.9, "timestamp": 123}'
        # Using deserialize_trikeshed_json with YourPythonDataType.from_json_dict
        item = deserialize_trikeshed_json(json_string, YourPythonDataType.from_json_dict)
        self.assertIsNotNone(item)
        self.assertIsInstance(item, YourPythonDataType)
        self.assertEqual(item.id, "direct_item")
        self.assertEqual(item.value, 99.9)
        self.assertEqual(item.timestamp, 123)

    def test_deserialize_json_invalid_json_string(self):
        json_string = '{"shape": [2, 2], "data": [1, 2, 3, 4' # Malformed JSON
        tensor = deserialize_trikeshed_json(json_string, Tensor.from_json_dict)
        self.assertIsNone(tensor) # Expect None due to JSONDecodeError

    def test_deserialize_json_missing_fields_tensor(self):
        # Tensor.from_json_dict raises KeyError if 'shape' or 'data' are missing.
        # deserialize_trikeshed_json catches this and returns None.
        json_string_no_shape = '{"data": [1, 2, 3, 4]}'
        self.assertIsNone(deserialize_trikeshed_json(json_string_no_shape, Tensor.from_json_dict))

        json_string_no_data = '{"shape": [2,2]}'
        self.assertIsNone(deserialize_trikeshed_json(json_string_no_data, Tensor.from_json_dict))

    def test_http_response_data_deserialization_empty_body(self):
        json_string = '''
        {
          "statusCode": 204,
          "headers": {"X-Test": ["empty"]},
          "body": {
            "type": "borg.trikeshed.net.http.ResponseBody.Empty"
          }
        }
        '''
        # Use HttpResponseData.from_json_dict as the parser
        response_data = deserialize_trikeshed_json(json_string, HttpResponseData.from_json_dict)
        self.assertIsNotNone(response_data)
        self.assertEqual(response_data.status_code, 204)
        self.assertEqual(response_data.headers, {"X-Test": ["empty"]})
        self.assertIsNone(response_data.body_bytes)
        self.assertIsNone(response_data.body_text)

    def test_http_response_data_deserialization_bytes_body_as_list(self):
        json_string = '''
        {
          "statusCode": 200,
          "headers": {"Content-Type": ["text/plain"]},
          "body": {
            "type": "borg.trikeshed.net.http.ResponseBody.Bytes",
            "content": [104, 101, 108, 108, 111]
          }
        }
        ''' # "hello"
        response_data = deserialize_trikeshed_json(json_string, HttpResponseData.from_json_dict)
        self.assertIsNotNone(response_data)
        self.assertEqual(response_data.status_code, 200)
        self.assertEqual(response_data.body_bytes, b"hello")
        self.assertEqual(response_data.body_text, "hello")

    def test_http_response_data_deserialization_bytes_body_as_base64(self):
        json_string = '''
        {
          "statusCode": 200,
          "headers": {"Content-Type": ["application/octet-stream"]},
          "body": {
            "type": "borg.trikeshed.net.http.ResponseBody.Bytes",
            "content": "aGVsbG8gd29ybGQ="
          }
        }
        ''' # "hello world" base64 encoded
        response_data = deserialize_trikeshed_json(json_string, HttpResponseData.from_json_dict)
        self.assertIsNotNone(response_data)
        self.assertEqual(response_data.status_code, 200)
        self.assertEqual(response_data.body_bytes, b"hello world")
        self.assertEqual(response_data.body_text, "hello world")

if __name__ == '__main__':
    unittest.main()
