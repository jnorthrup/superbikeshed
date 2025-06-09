package borg.longpoll

/**
 * Represents a notification about a change in a dataset.
 * This is typically used in long polling or pub/sub mechanisms to inform
 * interested parties about dataset updates.
 *
 * @property datasetId An identifier for the dataset that has changed. This could be an IPNS name, a URI, or any other unique string.
 * @property newManifestCid The CID of the new manifest for the dataset.
 * @property previousManifestCid An optional CID of the manifest before this change, if known. This can be used for diffing or version tracking.
 */
data class DatasetChangeNotification(
    val datasetId: String,
    val newManifestCid: String,
    val previousManifestCid: String? = null // Making it optional as per the thought process
)
