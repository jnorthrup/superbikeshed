package borg.trikeshed.lib

@JvmInline
value class HttpHeaderName(val value: String)

typealias ContentTypeHeader = HttpHeaderName("Content-Type")
typealias ConnectionHeader = HttpHeaderName("Connection")
typealias HostHeader = HttpHeaderName("Host")
typealias AcceptHeader = HttpHeaderName("Accept")
typealias UserAgentHeader = HttpHeaderName("User-Agent")
typealias ContentLengthHeader = HttpHeaderName("Content-Length")