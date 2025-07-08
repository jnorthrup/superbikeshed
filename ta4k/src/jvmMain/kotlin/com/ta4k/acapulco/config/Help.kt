package com.ta4k.acapulco.config

import java.nio.file.Path
import java.nio.file.Paths

object Help {
    val mpKeyFile = ConfigValue("mpKeyFile", Paths.get("config", "mp.key"))
    val mpImportDir = ConfigValue("mpImportDir", Paths.get("data", "import"))
    val binDir = ConfigValue("binDir", Paths.get("bin"))
    val tmpDir = ConfigValue("tmpDir", Paths.get("tmp"))
    val agentDir = ConfigValue("agentDir", Paths.get("data", "agents"))
    val tickerAssets = ConfigValue("tickerAssets", "BTC/USDT,ETH/USDT")
    val horizonDepthMinutes = ConfigValue("horizonDepthMinutes", "60")
    val ISAMWriterThreads = ConfigValue("ISAMWriterThreads", "4")
    val episodeLength = ConfigValue("episodeLength", "1000")
    val NormalizeInstants = ConfigValue("NormalizeInstants", "true")
}

class ConfigValue(val name: String, val defaultValue: Any) {
    val value: Any
        get() = System.getProperty(name) ?: defaultValue
} 