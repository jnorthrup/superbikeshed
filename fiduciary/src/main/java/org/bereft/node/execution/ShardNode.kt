package org.bereft.node.execution

import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bereft.node.events.CmcEvents
import org.bereft.node.events.ExchangeSummaryEvents
import org.bereft.node.writer.CmcWriter
import org.bereft.node.writer.ExchangeSummaryWriter
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    /**
     * must be reeeeally early here...
     */
    if (args.size > 0) {
        System.getProperties().load(FileInputStream(args[0]))
    }

    runBlocking { while (true) {
        launch { CmcWriter.writeCandles() }
        launch { ExchangeSummaryWriter.main() }
        launch { CmcEvents.main() }
        launch { ExchangeSummaryEvents.main() }
        joinAll()
            //should occur at never  +1 day
            delay(TimeUnit.DAYS.toMillis(1))
        }
    }
}