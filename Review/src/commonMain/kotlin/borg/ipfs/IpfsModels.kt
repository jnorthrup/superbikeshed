package borg.ipfs

// Consider adding @Serializable if kotlinx.serialization will be used at a later stage.
/**
 * Represents the manifest for a Trikeshed ISAM dataset stored in IPFS.
 * This manifest contains CIDs for the data and metadata files, along with
 * optional descriptive information like a dataset name and timestamp.
 *
 * @property dataCid The IPFS Content Identifier (CID) for the ISAM data file.
 * @property metaCid The IPFS Content Identifier (CID) for the ISAM metadata file.
 * @property name An optional human-readable name for the dataset.
 * @property timestamp An optional timestamp indicating when the dataset was created or published.
 * @property previousManifestCid An optional CID of the previous version of this dataset's manifest, for version history.
 * @property customMetadata An optional map for arbitrary key-value metadata.
 */
data class IpfsDatasetManifest(
    val dataCid: IpfsCid,
    val metaCid: IpfsCid,
    val name: String? = null,
    val timestamp: UnixTimestamp? = null,
    val previousManifestCid: IpfsCid? = null,
    val customMetadata: CustomMetadata? = null
)
