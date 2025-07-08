# Protocols and Service Implementation TODOs

This document details the major protocol and service implementation goals, with technical breakdowns and actionable TODOs for each.

---

## 1. Gossip Service: TTL, Storm Prevention, Subscription Management

**Description:**
- Peer-to-peer message dissemination with hop/time-limited propagation (TTL)
- Storm prevention (deduplication, rate limiting, backoff)
- Topic-based subscription management

**TODO:**
- [ ] Design message format with unique IDs and TTL fields
- [ ] Implement deduplication cache (e.g., LRU or bloom filter)
- [ ] Add rate limiting/backoff for message flow
- [ ] Create subscription registry and topic filtering
- [ ] Write integration tests for storms and TTL expiry

---

## 2. Agent Bus Integration: QUIC Transport & TrikeShed Reactor

**Description:**
- Internal event bus for agent communication
- QUIC transport (UDP-based, multiplexed, secure)
- TrikeShed reactor: event-driven, non-blocking I/O

**TODO:**
- [ ] Define agent bus message schema and serialization
- [ ] Implement QUIC endpoints (client/server)
- [ ] Adapt TrikeShed reactor for QUIC events
- [ ] Bridge agent bus events to/from QUIC streams
- [ ] Test with simulated clusters and partitions

---

## 3. HTTP/0.9 over QUIC Streams

**Description:**
- Minimal HTTP: single-line GET, no headers, raw body
- Each request/response mapped to a QUIC stream

**TODO:**
- [ ] Implement HTTP/0.9 parser/serializer
- [ ] Map requests/responses to QUIC bidirectional streams
- [ ] Demo client/server for GET requests
- [ ] Add tests for malformed requests and teardown

---

## 4. HTTP/1.0 & 1.1: Persistent Connections, Chunked Encoding, Pipelining

**Description:**
- HTTP/1.0: headers, status codes, no persistent connections by default
- HTTP/1.1: persistent connections, chunked encoding, pipelining

**TODO:**
- [ ] Implement HTTP/1.0/1.1 parsers/serializers
- [ ] Support persistent connections (keep-alive/close)
- [ ] Implement chunked transfer encoding
- [ ] Add request pipelining and out-of-order response handling
- [ ] Write conformance and stress tests

---

## 5. HTTP/2: HPACK, Server Push, Stream Priorities

**Description:**
- Binary framing, multiplexed streams, HPACK header compression
- Server push, stream prioritization

**TODO:**
- [ ] Implement HTTP/2 frame parser/state machine
- [ ] Integrate HPACK compression/decompression
- [ ] Add server push and stream prioritization
- [ ] Map HTTP/2 streams to QUIC (if needed)
- [ ] Interoperability tests with real clients/servers

---

## 6. HTTP/3: QPACK, Frame Handling

**Description:**
- HTTP/2 semantics over QUIC, QPACK header compression
- Out-of-order delivery, proper frame parsing

**TODO:**
- [ ] Implement HTTP/3 frame parser/state machine
- [ ] Integrate QPACK compression/decompression
- [ ] Map HTTP/3 streams to QUIC
- [ ] Add tests for out-of-order delivery and resets
- [ ] Validate with real HTTP/3 endpoints

---

## 7. Kotlin Common Crypto: TLS 1.3, Key Exchange, Cipher Suites

**Description:**
- Multiplatform crypto abstraction for hash, AEAD, key exchange
- TLS 1.3 primitives

**TODO:**
- [ ] Define crypto interfaces (hash, AEAD, key exchange)
- [ ] Implement/bind to platform crypto providers
- [ ] Integrate with TLS 1.3 handshake/record layer
- [ ] Write test vectors for primitives

---

## 8. TLS 1.3 Handshake: 0-RTT, Session Resumption

**Description:**
- Secure, minimal handshake
- 0-RTT data, session tickets for fast reconnects

**TODO:**
- [ ] Implement TLS 1.3 handshake state machine
- [ ] Add 0-RTT support and replay protection
- [ ] Implement session ticket issuance/validation
- [ ] Write handshake transcript/key schedule tests

---

## 9. SSH Protocol: Key Exchange, Channel Multiplexing, Forwarding

**Description:**
- Secure shell protocol, interactive login, tunneling
- Key exchange, channel multiplexing, forwarding (TCP, X11, agent)

**TODO:**
- [ ] Implement SSH transport and binary packet protocol
- [ ] Add key exchange and authentication
- [ ] Implement channel multiplexing/window management
- [ ] Add port/X11/agent forwarding
- [ ] Integration tests with OpenSSH

---

## 10. CouchDB Client: REST API, Replication, Change Feeds

**Description:**
- HTTP-based, document-oriented DB client
- Full REST API, replication, change feeds

**TODO:**
- [ ] Implement all REST endpoints (CRUD, views, admin)
- [ ] Add replication protocol (push/pull, conflict resolution)
- [ ] Implement change feed listener (long-poll, continuous)
- [ ] Tests for replication and change feed edge cases

---

## 11. IPFS Integration: DAG, Pinning, PubSub, IPLD

**Description:**
- Content-addressed storage, DAG ops, pinning, PubSub, IPLD

**TODO:**
- [ ] Implement DAG add/get/traverse
- [ ] Add pinning/unpinning
- [ ] Integrate PubSub for messaging
- [ ] Add IPLD model/codecs
- [ ] Tests for DAG traversal and PubSub reliability

---

## 12. Zero Compilation Errors & CI/CD Green

**Description:**
- All code builds cleanly across all targets
- Automated CI/CD checks

**TODO:**
- [ ] Set up CI/CD for all targets
- [ ] Add exhaustive test coverage
- [ ] Fix all compilation/test errors as they arise
- [ ] Enforce zero-error policy before merging

---

## 13. Generic Protocol Ingress/Egress with CoroutineContext and Metaseries

**Description:**
- Establish a standardized pattern for injecting ingress and egress channels into protocol implementations using Kotlin CoroutineContext.
- Leverage factories and double dispatch for flexible channel provision based on context.
- Utilize `metaseries` as a codex for defining and managing protocol-specific channel types and their associated metadata.

**Rationale:**
- Enhances testability by allowing easy mocking and substitution of network I/O.
- Promotes modularity and separation of concerns, decoupling protocol logic from underlying transport.
- Facilitates dynamic protocol behavior based on runtime context (e.g., SOCKS proxy, encryption layers).
- Provides a clear, extensible mechanism for defining and discovering protocol-specific I/O capabilities.

**TODO:**
- [ ] Define a generic `ProtocolChannelFactory` interface that takes `CoroutineContext` and returns `SocksIngressChannel` and `SocksEgressChannel` (or more generic `ProtocolIngressChannel`/`ProtocolEgressChannel`).
- [ ] Implement concrete `ProtocolChannelFactory` for various transports (e.g., direct TCP, QUIC, SOCKS).
- [ ] Develop a `MetaseriesCodex` to register and discover `ProtocolChannelFactory` implementations based on protocol identifiers and context attributes.
- [ ] Refactor existing protocol implementations (e.g., SSH, HTTP) to consume channels from the `CoroutineContext` via the `MetaseriesCodex`.
- [ ] Implement double dispatch mechanism within protocol operations to select appropriate channel handling based on the `CoroutineContext`.
- [ ] Create comprehensive unit and integration tests for the `ProtocolChannelFactory` and `MetaseriesCodex`.
- [ ] Document the new ingress/egress pattern and its usage for future protocol development.