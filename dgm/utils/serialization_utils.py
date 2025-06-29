import json
from dgm.trikeshed_types import Tensor, Series, Join, HttpResponseData

def deserialize_trikeshed_json(json_string, main_type_parser):
    '''
    Deserializes a JSON string into appropriate DGM Python objects.
    main_type_parser is a function (e.g., Tensor.from_json_dict) that takes a dict
    and returns the main Python object.
    '''
    try:
        data_dict = json.loads(json_string)
        return main_type_parser(data_dict)
    except json.JSONDecodeError as e:
        print(f"JSONDecodeError: {e} in json_string: '{json_string[:100]}...'") # Log part of the string
        return None
    except Exception as e:
        print(f"Error during deserialization: {e}")
        return None

# Example parsers for Join (to be used with Join.from_json_dict)
def parse_tensor_from_dict(data_dict):
    if data_dict is None: return None
    return Tensor.from_json_dict(data_dict)

def parse_series_from_dict(data_dict):
    if data_dict is None: return None
    return Series.from_json_dict(data_dict)

def parse_http_response_data_from_dict(data_dict):
     if data_dict is None: return None
     # This uses the HttpResponseData.from_json_dict which has initial logic
     # for handling the structure produced by kotlinx.serialization for HttpResponse.
     return HttpResponseData.from_json_dict(data_dict)

# --- Example Usage (can be moved to tests later) ---
# To run these examples, you would typically do it from a script that can import dgm modules,
# or ensure your PYTHONPATH is set up if running directly.

# Example for Tensor:
# json_string_tensor = '{"shape": [2, 2], "data": [1, 2, 3, 4]}'
# tensor_obj = deserialize_trikeshed_json(json_string_tensor, Tensor.from_json_dict)
# print("Tensor Example:")
# print(tensor_obj)
# print(tensor_obj.shape if tensor_obj else "Error")
# print(tensor_obj.data if tensor_obj else "Error")
# print("-" * 20)

# Example for Series:
# json_string_series = '{"data": ["a", "b", "c"]}'
# series_obj = deserialize_trikeshed_json(json_string_series, Series.from_json_dict)
# print("Series Example:")
# print(series_obj)
# print(series_obj.data if series_obj else "Error")
# print("-" * 20)

# Example for Join of two Series:
# json_string_join_series = '{"a": {"data": [1,2]}, "b": {"data": [3,4]}}'
# joined_series_obj = deserialize_trikeshed_json(
#     json_string_join_series,
#     lambda d: Join.from_json_dict(d, type_a_parser=parse_series_from_dict, type_b_parser=parse_series_from_dict)
# )
# print("Join of Series Example:")
# print(joined_series_obj)
# if joined_series_obj:
#     print(f"Join A: {joined_series_obj.a}")
#     print(f"Join A Data: {joined_series_obj.a.data if joined_series_obj.a else 'Error'}")
#     print(f"Join B: {joined_series_obj.b}")
#     print(f"Join B Data: {joined_series_obj.b.data if joined_series_obj.b else 'Error'}")
# else:
#     print("Error deserializing Join of Series")
# print("-" * 20)

# Example for HttpResponseData (simplified, actual JSON from Kotlin will be more nested)
# This example assumes the JSON structure directly matches what HttpResponseData expects.
# The actual JSON from Kotlin for HttpResponse would be more complex due to polymorphic serialization of Body
# and the structure of Join.
#
# For HttpBody.Bytes, Kotlin might produce:
# {
#   "statusCode": 200,
#   "headers": {"Content-Type":["application/json"]},
#   "body": {
#     "type": "borg.trikeshed.net.http.ResponseBody.Bytes",
#     "content": [104, 101, 108, 108, 111] // "hello" as byte array
#   }
# }
# json_string_http_response_bytes = '''
# {
#   "statusCode": 200,
#   "headers": {"Content-Type":["application/json"], "X-Custom-Header": ["Value1", "Value2"]},
#   "body": {
#     "type": "borg.trikeshed.net.http.ResponseBody.Bytes",
#     "content": [123, 34, 107, 101, 121, 34, 58, 32, 34, 118, 97, 108, 117, 101, 34, 125]
#   }
# }
# '''
# http_response_obj_bytes = deserialize_trikeshed_json(json_string_http_response_bytes, parse_http_response_data_from_dict)
# print("HttpResponseData (Bytes Body) Example:")
# print(http_response_obj_bytes)
# if http_response_obj_bytes:
#     print(f"Status Code: {http_response_obj_bytes.status_code}")
#     print(f"Headers: {http_response_obj_bytes.headers}")
#     print(f"Body Bytes: {http_response_obj_bytes.body_bytes}")
#     print(f"Body Text: {http_response_obj_bytes.body_text}")
# else:
#     print("Error deserializing HttpResponseData with Bytes Body")
# print("-" * 20)

# For HttpBody.Empty:
# {
#   "statusCode": 204,
#   "headers": {},
#   "body": {
#     "type": "borg.trikeshed.net.http.ResponseBody.Empty"
#   }
# }
# json_string_http_response_empty = '''
# {
#   "statusCode": 204,
#   "headers": {},
#   "body": {
#     "type": "borg.trikeshed.net.http.ResponseBody.Empty"
#   }
# }
# '''
# http_response_obj_empty = deserialize_trikeshed_json(json_string_http_response_empty, parse_http_response_data_from_dict)
# print("HttpResponseData (Empty Body) Example:")
# print(http_response_obj_empty)
# if http_response_obj_empty:
#     print(f"Status Code: {http_response_obj_empty.status_code}")
#     print(f"Headers: {http_response_obj_empty.headers}")
#     print(f"Body Bytes: {http_response_obj_empty.body_bytes}")
#     print(f"Body Text: {http_response_obj_empty.body_text}")
# else:
#     print("Error deserializing HttpResponseData with Empty Body")
# print("-" * 20)
