class Tensor:
    def __init__(self, shape, data):
        self.shape = shape  # list of ints
        self.data = data    # list of values

    @classmethod
    def from_json_dict(cls, json_dict):
        if not isinstance(json_dict, dict):
            raise TypeError(f"Expected a dict for Tensor.from_json_dict, got {type(json_dict)}")
        return cls(json_dict['shape'], json_dict['data'])

    def __repr__(self):
        return f"Tensor(shape={self.shape}, data_len={len(self.data) if self.data is not None else 0})"

class Series:
    def __init__(self, data):
        self.data = data  # list of values

    @classmethod
    def from_json_dict(cls, json_dict):
        if not isinstance(json_dict, dict):
            raise TypeError(f"Expected a dict for Series.from_json_dict, got {type(json_dict)}")
        return cls(json_dict['data'])

    def __repr__(self):
        return f"Series(data_len={len(self.data) if self.data is not None else 0})"

class Join:
    def __init__(self, a, b):
        self.a = a
        self.b = b

    @classmethod
    def from_json_dict(cls, json_dict, type_a_parser=None, type_b_parser=None):
        if not isinstance(json_dict, dict):
            raise TypeError(f"Expected a dict for Join.from_json_dict, got {type(json_dict)}")
        # type_a_parser and type_b_parser are functions that can convert
        # the dict representation of a and b into their respective Python objects.
        # If None, assumes they are primitive types or already parsed dicts.
        val_a = type_a_parser(json_dict['a']) if type_a_parser and json_dict.get('a') is not None else json_dict.get('a')
        val_b = type_b_parser(json_dict['b']) if type_b_parser and json_dict.get('b') is not None else json_dict.get('b')
        return cls(val_a, val_b)

    def __repr__(self):
        return f"Join(a={repr(self.a)}, b={repr(self.b)})"

class HttpResponseData:
    def __init__(self, status_code, headers, body_bytes=None, body_text=None):
        self.status_code = status_code # int
        self.headers = headers         # dict
        self.body_bytes = body_bytes   # bytes
        self.body_text = body_text     # str

    @classmethod
    def from_json_dict(cls, json_dict):
        if not isinstance(json_dict, dict):
            raise TypeError(f"Expected a dict for HttpResponseData.from_json_dict, got {type(json_dict)}")

        # This is a placeholder and needs careful mapping based on actual JSON from TrikeShed.
        # The actual JSON for HttpResponse from TrikeShed is:
        # {"a": SerializableJoin (for HttpRequestLine) , "b": SerializableJoin (for HttpHeaders and HttpBody)}
        # HttpRequestLine: {"a": HttpMethod, "b": {"a": HttpRequestPath, "b": HttpScheme}} (Not directly in HttpResponse)
        # HttpResponseLine (part of HttpResponse's 'a'): {"a": HttpVersion, "b": {"a": HttpStatusCode, "b": HttpReasonPhrase}}
        # HttpHeaders (part of HttpResponse's 'b'.a): This is complex, a CoreTensorCursorWithMeta<String>
        # HttpBody (part of HttpResponse's 'b'.b): Sealed class HttpBody.Empty or HttpBody.Bytes

        # Simplified parsing for now, focusing on the structure we defined for Kotlin's HttpResponse
        # data class HttpResponse(
        #    val statusCode: Int,
        #    val headers: HttpHeaders, // Map<String, List<String>>
        #    val body: ResponseBody    // Sealed class: Empty or Bytes (which contains ByteArray)
        # )

        status_code = json_dict.get('statusCode', 500)
        headers = json_dict.get('headers', {})

        body_dict = json_dict.get('body', {})
        body_bytes = None
        body_text = None

        # The Kotlin side serializes HttpBody polymorphically.
        # For HttpBody.Bytes, it would be like: {"type": "borg.trikeshed.net.http.ResponseBody.Bytes", "content": "base64_encoded_byte_array_or_list_of_ints"}
        # For HttpBody.Empty, it would be like: {"type": "borg.trikeshed.net.http.ResponseBody.Empty"}
        # kotlinx.serialization by default uses fully qualified names for polymorphic types.

        if isinstance(body_dict, dict):
            body_type = body_dict.get('type')
            if body_type and 'Bytes' in body_type:
                # Assuming 'content' is a list of integers (byte values) or base64 string
                # For now, let's assume it's a list of integers as ByteArray might be serialized that way by default to JSON.
                byte_content = body_dict.get('content')
                if isinstance(byte_content, list):
                    body_bytes = bytes(byte_content)
                    try:
                        body_text = body_bytes.decode('utf-8')
                    except UnicodeDecodeError:
                        body_text = None # Or some placeholder like "[binary data]"
                elif isinstance(byte_content, str): # Potentially base64 encoded
                    import base64
                    try:
                        body_bytes = base64.b64decode(byte_content)
                        try:
                            body_text = body_bytes.decode('utf-8')
                        except UnicodeDecodeError:
                            body_text = None
                    except Exception: # Broad exception for base64 decoding errors
                        body_bytes = None
                        body_text = byte_content # Keep original string if not valid base64
            elif body_type and 'Empty' in body_type:
                pass # body_bytes and body_text remain None

        return cls(status_code, headers, body_bytes, body_text)

    def __repr__(self):
        return f"HttpResponseData(status_code={self.status_code}, headers_count={len(self.headers)}, body_present={self.body_bytes is not None or self.body_text is not None})"
