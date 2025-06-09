package borg.longpoll

import kotlinx.coroutines.flow.Flow
// borg.longpoll.DatasetChangeNotification is in the same package, explicit import not strictly needed
// but can be added if preferred: import borg.longpoll.DatasetChangeNotification

/**
 * Interface for a service that provides notifications about dataset changes.
 * This service allows clients to subscribe to a stream of changes for specific datasets.
 */
interface ChangeNotificationService {
    /**
     * Retrieves a [Flow] of [DatasetChangeNotification]s for a given dataset.
     * The flow will emit a new notification whenever a change is detected for the specified dataset.
     *
     * Implementations of this service might use various mechanisms to detect changes,
     * such as long polling an IPNS record, subscribing to an IPFS PubSub topic,
     * or monitoring an external change feed.
     *
     * @param datasetId The unique identifier of the dataset to monitor for changes
     *                  (e.g., an IPNS name or a project-specific ID).
     * @return A [Flow] that emits [DatasetChangeNotification] objects as changes occur.
     */
    fun getNotifications(datasetId: String): Flow<DatasetChangeNotification>
}
