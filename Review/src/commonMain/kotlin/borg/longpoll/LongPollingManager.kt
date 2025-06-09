package borg.longpoll

// borg.longpoll.DatasetChangeNotification is in the same package, explicit import not strictly needed
// but can be added if preferred: import borg.longpoll.DatasetChangeNotification

/**
 * Interface for a manager that actively polls for dataset changes using a long polling strategy.
 * This is distinct from [ChangeNotificationService] which might use various backend mechanisms (including PubSub).
 * The LongPollingManager specifically implements a client-side or server-assisted long poll.
 */
interface LongPollingManager {

    /**
     * Waits for a change to occur in the specified dataset, identified by its ID.
     * This function will typically maintain an open connection (long poll) to a server
     * or periodically check an IPNS record or an IPFS PubSub topic (simulating long polling if direct PubSub Flow isn't used)
     * until a change is detected or the timeout occurs.
     *
     * @param datasetId The identifier of the dataset to monitor (e.g., IPNS name).
     * @param lastKnownManifestCid The CID of the last known manifest for this dataset.
     *                             If provided, the manager will only return a notification
     *                             if the new manifest CID is different from this one.
     *                             If null, any current version will be considered a "change".
     * @param timeoutMillis The maximum time in milliseconds to wait for a change before returning.
     *                      A value of 0 or less might indicate an indefinite wait, depending on implementation.
     * @return A [DatasetChangeNotification] if a change is detected within the timeout period and
     *         (if `lastKnownManifestCid` was provided) the new manifest CID is different.
     *         Returns `null` if the timeout is reached before a change is detected or if the
     *         current manifest CID matches `lastKnownManifestCid`.
     */
    suspend fun waitForChange(
        datasetId: String,
        lastKnownManifestCid: String?,
        timeoutMillis: Long
    ): DatasetChangeNotification?
}
