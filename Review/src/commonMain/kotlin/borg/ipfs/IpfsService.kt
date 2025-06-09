package borg.ipfs

import kotlin.coroutines.CoroutineContext

/**
 * Key for accessing the IpfsService in a CoroutineContext.
 */
object IpfsServiceKey : CoroutineContext.Key<IpfsService>

/**
 * Interface for interacting with an IPFS (InterPlanetary File System) node.
 * Allows adding data to IPFS, retrieving data, and managing IPNS records.
 */
interface IpfsService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = IpfsServiceKey

    /**
     * Adds the given byte array data to IPFS.
     *
     * @param data The ByteArray to add to IPFS.
     * @return The CID (Content Identifier) string of the added data.
     */
    suspend fun add(data: ByteArray): String

    /**
     * Retrieves data from IPFS based on its CID.
     *
     * @param cid The CID string of the content to retrieve.
     * @return The ByteArray content associated with the CID.
     */
    suspend fun cat(cid: String): ByteArray

    /**
     * Publishes a CID to an IPNS (InterPlanetary Name System) name.
     * This makes the CID resolvable via a human-readable name that can be updated.
     *
     * @param ipnsName The IPNS name to publish or update (e.g., k51q...).
     * @param cid The CID string to associate with the IPNS name.
     * @return A string confirming the publication, often the IPNS name or path.
     */
    suspend fun publishName(ipnsName: String, cid: String): String

    /**
     * Resolves an IPNS name to the CID it currently points to.
     *
     * @param ipnsName The IPNS name to resolve.
     * @return The CID string that the IPNS name resolves to.
     */
    suspend fun resolveName(ipnsName: String): String
}
