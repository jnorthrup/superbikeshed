// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/Fail.kt ===
// =====================================================================
package borg.trikeshed.acapulco

import com.binance.api.client.BinanceApiClientFactory

class Fail {
    fun go() {
        val bFac = BinanceApiClientFactory.newInstance(true, true)
        val traderClient = bFac.newRestClient()
    }
}

fun main() {
    Fail().go()
}
