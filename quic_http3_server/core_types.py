from typing import NewType, List, Tuple, Union, Optional
from enum import Enum

# Primitive Types
HttpPath = NewType('HttpPath', str)
HttpHeaderKey = NewType('HttpHeaderKey', str)
HttpHeaderValue = NewType('HttpHeaderValue', str)
HttpBody = NewType('HttpBody', bytes)
HttpStatusCode = NewType('HttpStatusCode', int)

class HttpMethod(Enum):
    GET = "GET"
    POST = "POST"
    PUT = "PUT"
    DELETE = "DELETE"
    HEAD = "HEAD"
    OPTIONS = "OPTIONS"
    PATCH = "PATCH"

    @classmethod
    def from_string(cls, s: str) -> 'HttpMethod':
        s_upper = s.upper()
        try:
            return cls(s_upper)
        except ValueError:
            raise ValueError(f"Unknown or unsupported HTTP method: {s_upper}")

HttpHeaders = NewType('HttpHeaders', List[Tuple[HttpHeaderKey, HttpHeaderValue]])

class ParsedHttpRequest:
    def __init__(self,
                 method: HttpMethod,
                 path: HttpPath,
                 headers: HttpHeaders,
                 body: HttpBody = HttpBody(b"")):
        self.method: HttpMethod = method
        self.path: HttpPath = path
        self.headers: HttpHeaders = headers
        self.body: HttpBody = body

    def get_header_value(self, key: Union[HttpHeaderKey, str]) -> Optional[HttpHeaderValue]:
        search_key = str(key).lower()
        for h_key, h_val in self.headers:
            if str(h_key).lower() == search_key:
                return h_val
        return None

    def __repr__(self) -> str:
        return f"ParsedHttpRequest(method={self.method.value}, path='{self.path}', headers={self.headers}, body_len={len(self.body)})"

class ServerHttpResponse:
    def __init__(self,
                 status_code: HttpStatusCode,
                 headers: Optional[HttpHeaders] = None,
                 body: HttpBody = HttpBody(b"")):
        self.status_code: HttpStatusCode = status_code
        self.headers: HttpHeaders = headers if headers is not None else HttpHeaders([])
        self.body: HttpBody = body

    def add_header(self, key: Union[HttpHeaderKey, str], value: Union[HttpHeaderValue, str]) -> None:
        if self.headers is None: # Should not be reachable with current __init__
             self.headers = HttpHeaders([])
        self.headers.append((HttpHeaderKey(str(key)), HttpHeaderValue(str(value))))

    def __repr__(self) -> str:
        return f"ServerHttpResponse(status_code={self.status_code}, headers={self.headers}, body_len={len(self.body)})"

if __name__ == '__main__':
    # Test HttpMethod
    get_method = HttpMethod.from_string("GET")
    print(f"Method: {get_method}")
    try:
        HttpMethod.from_string("INVALID")
    except ValueError as e:
        print(f"Error for INVALID method: {e}")

    # Test ParsedHttpRequest
    req_h = HttpHeaders([
        (HttpHeaderKey("Content-Type"), HttpHeaderValue("application/json")),
        (HttpHeaderKey("X-Custom"), HttpHeaderValue("TestValue"))  # Corrected HttpValue to HttpHeaderValue
    ])
    request_obj = ParsedHttpRequest(
        method=get_method,
        path=HttpPath("/test_path"),
        headers=req_h,
        body=HttpBody(b'{"data": "example"}')
    )
    print(f"Request: {request_obj}")
    print(f"Content-Type from request: {request_obj.get_header_value('content-type')}")
    print(f"X-Custom from request: {request_obj.get_header_value(HttpHeaderKey('X-Custom'))}")

    # Test ServerHttpResponse
    response_obj = ServerHttpResponse(
        status_code=HttpStatusCode(200),
        body=HttpBody(b"This is a response body.")
    )
    response_obj.add_header("Content-Type", "text/plain; charset=utf-8")
    response_obj.add_header(HttpHeaderKey("X-Response-Header"), HttpHeaderValue("AnotherValue"))
    print(f"Response: {response_obj}")
