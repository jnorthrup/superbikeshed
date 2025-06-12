package borg.ipfs

/**
 * Type aliases and type definitions for IPFS-related types used in Trikeshed.
 */

/**
 * Represents an IPFS Content Identifier (CID).
 * This is a type-safe wrapper around the CID string.
 */
typealias IpfsCid = String

/**
 * Represents an IPNS name/key.
 * This is a type-safe wrapper around the IPNS name string.
 */
typealias IpnsName = String

/**
 * Represents an IPFS path, which can be either a CID path (/ipfs/...) or an IPNS path (/ipns/...).
 */
typealias IpfsPath = String

/**
 * Represents a topic name for IPFS PubSub.
 */
typealias IpfsTopic = String

/**
 * Represents a message payload for IPFS PubSub.
 */
typealias IpfsMessage = String

/**
 * Represents a dataset identifier in the Trikeshed system.
 * This is used to uniquely identify datasets across the system.
 */
typealias DatasetId = String

/**
 * Represents a version number for a dataset.
 * This is used to track different versions of the same dataset.
 */
typealias DatasetVersion = Long

/**
 * Represents a timestamp in milliseconds since the Unix epoch.
 */
typealias UnixTimestamp = Long

/**
 * Represents a map of custom metadata key-value pairs.
 */
typealias CustomMetadata = Map<String, String>

/**
 * Represents a peer ID in the IPFS network.
 */
typealias PeerId = String

/**
 * Represents a multiaddress string for IPFS peer connections.
 */
typealias Multiaddr = String

/**
 * Type alias for joining two types together.
 * This is used to combine types in a type-safe way.
 */
typealias Join<A, B> = Pair<A, B>

/**
 * Extension function to join two values together.
 */
infix fun <A, B> A.j(b: B): Join<A, B> = this to b 