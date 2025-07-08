package org.bereft.node.events

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bereft.node.config.MeshNode
import org.bereft.node.config.MeshNode.iAtomicCmcReference
import org.bereft.node.index.CmcCurrencies
import java.util.concurrent.TimeUnit
import kotlin.random.Random


object CmcEvents {
    val lockName: String get() = "lock/Cmc"
    val aSingleMinute = TimeUnit.MINUTES.toMinutes(1)

    /**
     * creates a hazelcast lock called "lock/Cmc"
     *
     * polls coinmarketcap using a variable  durationlock wait
     *
     * mandatory 1 second cooldown per attempt follows lock success or failure.
     */

    fun main() {
        do runBlocking {
            launch {
                val atomicReference = iAtomicCmcReference()
                if (MeshNode.meshOp().cpSubsystem.getLock(lockName).tryLock(
                        Random.nextLong(30, 45),
                        TimeUnit.SECONDS
                    )
                ) {
                    System.err.println("+=== locked $lockName")
                    val cmcCurrencies = CmcCurrencies( )
                    atomicReference.set(cmcCurrencies)
                    launch {
                        MeshNode.cmcUpdatesQ.add(cmcCurrencies)
                    }
                } else {
                    System.err.println("-=== nolock $lockName ")
                    atomicReference.get()
                }
            }.join()
            //mandatory cooldown
            delay(/*1, TimeUnit.MINUTES*/aSingleMinute)
        } while (true)
    }


}
