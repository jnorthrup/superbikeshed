package com.superbikeshed.trikeshed

import kotlinx.coroutines.flow.Flow

// Base interface for protocol stacks - allows platform-specific implementations
interface ProtocolStack {
    suspend fun serveQUIC(port: Int): QUICServer
    suspend fun serveHTTP2(port: Int): HTTP2Server
    suspend fun serveWebTransport(port: Int): WebTransportServer
    suspend fun servegRPC(port: Int): GRPCServer
    suspend fun serveMemcached(port: Int): MemcachedServer
    suspend fun serveRedis(port: Int): RedisServer
    suspend fun serveMySQL(port: Int): MySQLServer
    suspend fun servePostgreSQL(port: Int): PostgreSQLServer
    suspend fun serveScylla(port: Int): ScyllaServer
    suspend fun serveNATS(port: Int): NATSServer
    suspend fun serveKafka(port: Int): KafkaServer
}

// Protocol server base interface
interface ProtocolServer {
    suspend fun start()
    suspend fun stop()
    suspend fun stats(): ServerStats
    suspend fun connections(): Flow<Connection>
}

// Connection abstraction
interface Connection {
    val id: String
    val protocol: Protocol
    val localAddress: SocketAddress
    val remoteAddress: SocketAddress
    suspend fun send(data: ByteArray): Result<Unit>
    suspend fun receive(): Result<ByteArray>
    suspend fun close()
}

enum class Protocol {
    QUIC,
    HTTP2,
    HTTP3,
    WEBTRANSPORT,
    GRPC,
    MEMCACHED,
    REDIS,
    MYSQL,
    POSTGRESQL,
    SCYLLADB,
    NATS,
    KAFKA,
    COUCHDB
}

data class ServerStats(
    val connections: Long,
    val requestsPerSecond: Double,
    val bytesPerSecond: Long,
    val latencyP50: Double,
    val latencyP99: Double,
    val latencyP999: Double,
    val errors: Long,
    val cpuUsage: Double,
    val memoryUsage: Long
)

// QUIC Server - integrates with existing Trikeshed QUIC implementation
interface QUICServer : ProtocolServer {
    suspend fun acceptStream(): Result<QUICStream>
    suspend fun openStream(connectionId: String): Result<QUICStream>
    suspend fun datagram(data: ByteArray): Result<Unit>
}

// HTTP/2 Server
interface HTTP2Server : ProtocolServer {
    suspend fun handleRequest(handler: suspend (HTTP2Request) -> HTTP2Response)
    suspend fun pushPromise(streamId: Int, headers: Map<String, String>): Result<Int>
}

// WebTransport Server
interface WebTransportServer : ProtocolServer {
    suspend fun acceptSession(): Result<WebTransportSession>
}

interface WebTransportSession {
    suspend fun acceptStream(): Result<WebTransportStream>
    suspend fun openStream(): Result<WebTransportStream>
    suspend fun datagram(data: ByteArray): Result<Unit>
}

interface WebTransportStream : QUICStream

// gRPC Server
interface GRPCServer : ProtocolServer {
    suspend fun registerService(service: GRPCService)
    suspend fun unregisterService(name: String)
}

interface GRPCService {
    val name: String
    suspend fun handleUnary(method: String, request: ByteArray): Result<ByteArray>
    suspend fun handleServerStream(method: String, request: ByteArray): Flow<ByteArray>
    suspend fun handleClientStream(method: String, requests: Flow<ByteArray>): Result<ByteArray>
    suspend fun handleBidiStream(method: String, requests: Flow<ByteArray>): Flow<ByteArray>
}

// Memcached Server
interface MemcachedServer : ProtocolServer {
    suspend fun get(key: String): Result<ByteArray?>
    suspend fun set(key: String, value: ByteArray, flags: Int = 0, exptime: Int = 0): Result<Unit>
    suspend fun delete(key: String): Result<Boolean>
    suspend fun stats(): Map<String, String>
}

// Redis Server
interface RedisServer : ProtocolServer {
    suspend fun command(cmd: RedisCommand): Result<RedisResponse>
    suspend fun pipeline(commands: List<RedisCommand>): Result<List<RedisResponse>>
    suspend fun subscribe(channels: List<String>): Flow<RedisMessage>
}

data class RedisCommand(val command: String, val args: List<ByteArray>)
sealed class RedisResponse {
    data class Simple(val value: String) : RedisResponse()
    data class Error(val message: String) : RedisResponse()
    data class Integer(val value: Long) : RedisResponse()
    data class Bulk(val data: ByteArray?) : RedisResponse()
    data class Array(val elements: List<RedisResponse>) : RedisResponse()
}
data class RedisMessage(val channel: String, val data: ByteArray)

// MySQL Server
interface MySQLServer : ProtocolServer {
    suspend fun query(sql: String): Result<MySQLResult>
    suspend fun prepare(sql: String): Result<MySQLPreparedStatement>
    suspend fun beginTransaction(): Result<MySQLTransaction>
}

interface MySQLResult {
    val columns: List<MySQLColumn>
    suspend fun rows(): Flow<List<Any?>>
}

data class MySQLColumn(val name: String, val type: String)

interface MySQLPreparedStatement {
    suspend fun execute(params: List<Any?>): Result<MySQLResult>
    suspend fun close()
}

interface MySQLTransaction {
    suspend fun commit(): Result<Unit>
    suspend fun rollback(): Result<Unit>
}

// PostgreSQL Server
interface PostgreSQLServer : ProtocolServer {
    suspend fun query(sql: String, params: List<Any?> = emptyList()): Result<PostgreSQLResult>
    suspend fun prepare(name: String, sql: String): Result<Unit>
    suspend fun execute(name: String, params: List<Any?>): Result<PostgreSQLResult>
    suspend fun copyIn(table: String): Result<PostgreSQLCopyIn>
    suspend fun copyOut(query: String): Flow<ByteArray>
}

interface PostgreSQLResult {
    val fields: List<PostgreSQLField>
    suspend fun rows(): Flow<List<Any?>>
}

data class PostgreSQLField(val name: String, val oid: Int, val format: Int)

interface PostgreSQLCopyIn {
    suspend fun data(chunk: ByteArray): Result<Unit>
    suspend fun done(): Result<Long>
    suspend fun fail(error: String): Result<Unit>
}

// ScyllaDB Server
interface ScyllaServer : ProtocolServer {
    suspend fun execute(cql: String, params: List<Any?> = emptyList()): Result<ScyllaResult>
    suspend fun prepare(cql: String): Result<ScyllaPrepared>
    suspend fun batch(statements: List<ScyllaStatement>): Result<Unit>
}

interface ScyllaResult {
    val columns: List<ScyllaColumn>
    suspend fun rows(): Flow<List<Any?>>
}

data class ScyllaColumn(val name: String, val type: String)
data class ScyllaStatement(val cql: String, val params: List<Any?>)

interface ScyllaPrepared {
    val id: ByteArray
    suspend fun execute(params: List<Any?>): Result<ScyllaResult>
}

// NATS Server
interface NATSServer : ProtocolServer {
    suspend fun publish(subject: String, data: ByteArray): Result<Unit>
    suspend fun subscribe(subject: String): Flow<NATSMessage>
    suspend fun request(subject: String, data: ByteArray, timeout: Long = 5000): Result<NATSMessage>
}

data class NATSMessage(val subject: String, val data: ByteArray, val replyTo: String?)

// Kafka Server
interface KafkaServer : ProtocolServer {
    suspend fun produce(topic: String, key: ByteArray?, value: ByteArray, partition: Int? = null): Result<KafkaOffset>
    suspend fun consume(topics: List<String>, groupId: String): Flow<KafkaRecord>
    suspend fun commitOffset(topic: String, partition: Int, offset: Long): Result<Unit>
    suspend fun metadata(): Result<KafkaMetadata>
}

data class KafkaOffset(val topic: String, val partition: Int, val offset: Long)
data class KafkaRecord(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val key: ByteArray?,
    val value: ByteArray,
    val timestamp: Long
)
data class KafkaMetadata(
    val brokers: List<KafkaBroker>,
    val topics: Map<String, KafkaTopicMetadata>
)
data class KafkaBroker(val id: Int, val host: String, val port: Int)
data class KafkaTopicMetadata(val partitions: List<KafkaPartitionMetadata>)
data class KafkaPartitionMetadata(
    val id: Int,
    val leader: Int,
    val replicas: List<Int>,
    val isr: List<Int>
)

// Socket address abstraction
data class SocketAddress(
    val host: String,
    val port: Int,
    val type: AddressType = AddressType.IPV4
)

enum class AddressType {
    IPV4,
    IPV6,
    UNIX
}

// HTTP/2 types
data class HTTP2Request(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray?
)

data class HTTP2Response(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray?
)