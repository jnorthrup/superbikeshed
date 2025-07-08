package org.bereft.node.config

import com.hazelcast.collection.IQueue
import com.hazelcast.config.Config
import com.hazelcast.core.Hazelcast
import com.hazelcast.cp.IAtomicReference
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.bereft.model.CmcCurrency
import org.bereft.model.ExchangeSummary

object MeshNode {
    val gson = NodeConfig.gson


    val exchangeSummariesQ: IQueue<ExchangeSummary>
        get() = runBlocking {
            meshOp().getQueue<ExchangeSummary>("ExchangeSummaries")
        }

    val cmcUpdatesQ: IQueue<List<CmcCurrency>>
        get() = runBlocking {
            meshOp().getQueue<List<CmcCurrency>>("CoinMarketCapUpdates")
        }

    val hz
        get() = {
            runBlocking {
                async {

                    Hazelcast.getOrCreateHazelcastInstance(Config(NodeConfig.sessionKey))
                }

            }
        }()

    suspend fun meshOp() = hz.await()
    suspend fun iAtomicCmcReference(): IAtomicReference<List<CmcCurrency>> = meshOp().cpSubsystem.getAtomicReference(
        "atomic/Cmc")!!
}
