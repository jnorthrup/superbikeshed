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
     * @return The CID (Content Identifier) of the added data.
     */
    suspend fun add(data: ByteArray): IpfsCid

    /**
     * Retrieves data from IPFS based on its CID.
     *
     * @param cid The CID of the content to retrieve.
     * @return The ByteArray content associated with the CID.
     */
    suspend fun cat(cid: IpfsCid): ByteArray

    /**
     * Publishes a CID to an IPNS (InterPlanetary Name System) name.
     * This makes the CID resolvable via a human-readable name that can be updated.
     *
     * @param ipnsName The IPNS name to publish or update.
     * @param cid The CID to associate with the IPNS name.
     * @return The IPNS path that was published.
     */
    suspend fun publishName(ipnsName: IpnsName, cid: IpfsCid): IpfsPath

    /**
     * Resolves an IPNS name to the CID it currently points to.
     *
     * @param ipnsName The IPNS name to resolve.
     * @return The CID that the IPNS name resolves to.
     */
    suspend fun resolveName(ipnsName: IpnsName): IpfsCid

    /**
     * Adds a key-value series to IPFS.
     *
     * @param series The key-value series to add.
     * @return The CID of the added series.
     */
    suspend fun <T> addSeries(series: KeyValueSeries<T>): IpfsCid

    /**
     * Retrieves a key-value series from IPFS.
     *
     * @param cid The CID of the series to retrieve.
     * @return The retrieved key-value series.
     */
    suspend fun <T> getSeries(cid: IpfsCid): KeyValueSeries<T>

    /**
     * Creates a tensor slice from the given ranges and metadata.
     *
     * @param slice The tensor slice to create.
     * @return The CID of the created slice.
     */
    suspend fun createSlice(slice: TensorSlice): IpfsCid

    /**
     * Retrieves a tensor slice from IPFS.
     *
     * @param cid The CID of the slice to retrieve.
     * @return The retrieved tensor slice.
     */
    suspend fun getSlice(cid: IpfsCid): TensorSlice

    /**
     * Executes a tensor query to find key-value pairs.
     *
     * @param query The query to execute.
     * @return A list of CIDs pointing to the matching key-value pairs.
     */
    suspend fun executeQuery(query: TensorQuery): List<IpfsCid>

    /**
     * Updates a cursor position in a tensor.
     *
     * @param cursor The new cursor position.
     * @return The CID of the updated cursor.
     */
    suspend fun updateCursor(cursor: TensorCursor): IpfsCid

    /**
     * Retrieves the current cursor position for a tensor.
     *
     * @param cid The CID of the tensor.
     * @return The current cursor position.
     */
    suspend fun getCursor(cid: IpfsCid): TensorCursor
}
