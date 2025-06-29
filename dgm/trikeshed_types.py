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


class TrikeshedQuicConfig:
    def __init__(self,
                 stream_buffer_size: int = 64 * 1024, # Default from Kotlin QuicConnection
                 max_concurrent_streams: int = (1 << 62), # Default from Kotlin QuicConnection.MAX_STREAMS
                 enable_0rtt: bool = True,
                 congestion_control_algorithm: str = "cubic",
                 initial_connection_flow_control_window: int = 64 * 1024, # Default from Kotlin QuicConfig
                 initial_stream_flow_control_window: int = 32 * 1024, # Default from Kotlin QuicConfig
                 max_ack_delay_ms: int = 25, # Default from Kotlin QuicConfig
                 default_stream_priority: int = 10 # Default from Kotlin QuicConfig
                 ):
        self.stream_buffer_size = stream_buffer_size
        self.max_concurrent_streams = max_concurrent_streams
        self.enable_0rtt = enable_0rtt
        self.congestion_control_algorithm = congestion_control_algorithm
        self.initial_connection_flow_control_window = initial_connection_flow_control_window
        self.initial_stream_flow_control_window = initial_stream_flow_control_window
        self.max_ack_delay_ms = max_ack_delay_ms
        self.default_stream_priority = default_stream_priority

        # Validation matching Kotlin's QuicConfig
        if not isinstance(self.stream_buffer_size, int) or self.stream_buffer_size <=0:
            # Kotlin uses Int? so it can be null, Python equivalent is Optional[int]
            # For simplicity, here making it an int with a positive value check.
            # Or allow None and handle it. Let's stick to int for now matching common defaults.
            raise ValueError("Stream buffer size must be a positive integer.")
        if not (isinstance(self.max_concurrent_streams, int) and self.max_concurrent_streams > 0):
            raise ValueError("Max concurrent streams must be a positive integer.")
        if not isinstance(self.enable_0rtt, bool):
            raise ValueError("Enable 0RTT must be a boolean.")
        if not (isinstance(self.congestion_control_algorithm, str) and self.congestion_control_algorithm.strip()):
            raise ValueError("Congestion control algorithm name cannot be blank.")
        if not (isinstance(self.initial_connection_flow_control_window, int) and self.initial_connection_flow_control_window >= 0):
            raise ValueError("Initial connection flow control window cannot be negative.")
        if not (isinstance(self.initial_stream_flow_control_window, int) and self.initial_stream_flow_control_window >= 0):
            raise ValueError("Initial stream flow control window cannot be negative.")
        if not (isinstance(self.max_ack_delay_ms, int) and self.max_ack_delay_ms >= 0):
            raise ValueError("Max ACK delay cannot be negative.")
        if not (isinstance(self.default_stream_priority, int) and self.default_stream_priority >= 0):
            raise ValueError("Default stream priority cannot be negative.")

    def to_json_dict(self):
        return {
            "streamBufferSize": self.stream_buffer_size,
            "maxConcurrentStreams": self.max_concurrent_streams,
            "enable0RTT": self.enable_0rtt,
            "congestionControlAlgorithm": self.congestion_control_algorithm,
            "initialConnectionFlowControlWindow": self.initial_connection_flow_control_window,
            "initialStreamFlowControlWindow": self.initial_stream_flow_control_window,
            "maxAckDelayMs": self.max_ack_delay_ms,
            "defaultStreamPriority": self.default_stream_priority,
        }

    def __repr__(self):
        return (f"TrikeshedQuicConfig(stream_buffer_size={self.stream_buffer_size}, "
                f"max_concurrent_streams={self.max_concurrent_streams}, enable_0rtt={self.enable_0rtt}, "
                f"congestion_control_algorithm='{self.congestion_control_algorithm}', "
                f"initial_connection_flow_control_window={self.initial_connection_flow_control_window}, "
                f"initial_stream_flow_control_window={self.initial_stream_flow_control_window}, "
                f"max_ack_delay_ms={self.max_ack_delay_ms}, default_stream_priority={self.default_stream_priority})")
