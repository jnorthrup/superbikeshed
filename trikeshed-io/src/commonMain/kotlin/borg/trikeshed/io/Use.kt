@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)

package borg.trikeshed.io


/**
 * Executes the given [block] function on this [Usable] resource and then closes it down correctly whether an exception
 * is thrown or not.
 *
 * @param block the function to execute.
 * @return the result of the [block] function.
 */
inline fun <T : Usable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        if (this != null) {
            if (exception == null) {
                close()
            } else {
                try {
                    close()
                } catch (closeException: Throwable) {
                    exception.addSuppressed(closeException)
                }
            }
        }
    }
} 