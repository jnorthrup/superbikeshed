package borg.trikeshed.views

/**
 * Represents a single operation in a view pipeline.
 *
 * @property type A string identifier for the type of operation (e.g., "filter", "select", "aggregate").
 * @property params A map of parameters for the operation. The keys are parameter names (strings),
 *                  and values can be of any type, depending on the operation's requirements.
 */
data class ViewOperation(
    val type: String,
    val params: Map<String, Any>
)

/**
 * Defines a "View" on a Trikeshed dataset. A view is a derived dataset
 * created by applying a pipeline of operations to a source dataset (which can be
 * identified by an IPNS name or a direct CID).
 *
 * @property name A human-readable name for this view definition.
 * @property sourceDatasetIpns The IPNS name of the source dataset. Either this or sourceDatasetCid must be provided.
 * @property sourceDatasetCid The direct CID of the source dataset's manifest. Either this or sourceDatasetIpns must be provided.
 * @property pipeline A list of [ViewOperation]s defining the transformation pipeline to apply to the source dataset.
 * @property description An optional human-readable description of what this view represents or achieves.
 */
data class ViewDefinition(
    val name: String,
    val sourceDatasetIpns: String? = null,
    val sourceDatasetCid: String? = null,
    val pipeline: List<ViewOperation>,
    val description: String? = null
) {
    init {
        require(sourceDatasetIpns != null || sourceDatasetCid != null) {
            "Either sourceDatasetIpns or sourceDatasetCid must be provided for a ViewDefinition."
        }
        require(sourceDatasetIpns == null || sourceDatasetCid == null) {
            "Only one of sourceDatasetIpns or sourceDatasetCid should be provided, not both."
        }
    }
}
