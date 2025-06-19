package  org.bereft.node.config

import vec.util.debug
import vec.util.logDebug
import vec.util.path
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.days

object Help {
    val W= EnvConfig("WANN_W","x","WANN WEIGHT")
    val withTornadoVm= EnvConfig("WITH_TORNADO","false")
    val assetsShuffled= EnvConfig("SHUFFLE_ASSETS","x","a max difficulty option")
    val NormalizeInstants = EnvConfig("NORMALIZE_INSTANTS", "true", "prefer time components 0..1 instead of one-hot encoding ni MuxIO")
    val useSingleWidth = EnvConfig("USE_SINGLE_WIDTH", "false", "use original smallest possible GnomeEval logic with 1 width")
    val reservedHidden = EnvConfig("RESERVED_HIDDEN", "0", "number of dead hidden nodes on init.")
    val initialElites = EnvConfig("BOOT_ELITES", Int.MAX_VALUE.toString(), "Number of Elites to seed from best-to-worst.  2 will iterate quickly with some time before pop growth noticable on big models.")
    val evalThreads = EnvConfig("EVAL_THREADS", min(Runtime.getRuntime().availableProcessors().toLong(), Runtime.getRuntime().maxMemory() / 1_500_000_000L).toString(), docString = "fixed size of  ISAM creation threadpool including the bash scripts.  your funeral!")
    val ISAMWriterThreads = EnvConfig("ISAMWRITER_THREADS", max(1, Runtime.getRuntime().freeMemory() / 4_000_000_000L).toString(), docString = "fixed size of  ISAM creation threadpool including the bash scripts.  your funeral!")
    val JsonReplayCapture = EnvConfig("JSON_REPLAY", "false", "snapshots the inputs, the wallet patch, and the entry impulse state before eval for json inclusion.  reduces iteration speed as much as 10-30%")
    val orderExpirationLength = EnvConfig("ORDER_TIMEOUT", "5", "number of generations before a limit order expires")
    val movingAvgSize = EnvConfig("MA", "60", docString = "Score Moving Avg size")
    val bias = EnvConfig("BIAS", docString = "Bias Nodes")
    val hidden = EnvConfig("HIDDEN", docString = "Hidden Nodes")
    /*350*/  val useSimd = EnvConfig("USE_SIMD", "true", "valid values here are 'true' for Vector API, 'FMA' for intrinsic with -XX:+UseFMA, and any other string for jit")
    val population = EnvConfig("POPULATION", (Runtime.getRuntime().availableProcessors() * 128).toString(), "defaults to cores*128")
    val runMode = EnvConfig("RUN_MODE", "realtime", "mode to run")
    val connectionDensity = EnvConfig("CONN_DENSITY", "falseness", "Double chance of setting link to 1")
    val rollingHistorySize = EnvConfig("ROLLING_HISTORY_SIZE", 14.days.inWholeMinutes.toString())
    val horizonDepthMinutes = EnvConfig("HORIZON_DEPTH",
            "3000",
            """each asset will display at most this # of compressed indexing values.  so if there are 2 million columns we can condense this down to 5000 for all """.trimMargin())
    val homeDir = EnvConfig("HOME",
            System.getProperty("user.home", Files.createTempDirectory("elsalvador").toRealPath().toString()),
            """~/""".trimMargin())
    val mpData = EnvConfig("MP_DATA", (homeDir.value).path.resolve("mpdata").toString(), """ussually ~/mpdata""".trimMargin())
    val mpEliteJson = EnvConfig("MP_ELITE", (mpData.value).path.resolve("json").toString(), """ussually ~/mpdata/json""".trimMargin())
    val mpImportDir = EnvConfig("MP_IMPORT",
            (mpData.value).path.resolve("import").toString(),
            """holds isam and csv hierarchically ussually ~/mpdata/cache""".trimMargin())
    val mpCacheDir = EnvConfig("MP_CACHE",
            (mpData.value).path.resolve("cache").toString(),
            """stores idempotent exchange data zipfiles in a hierarchy""".trimMargin())
    val mpKeyFile = EnvConfig("MP_KEYFILE", (mpData.value).path.resolve(".keys/testnet.csv").toString(), """stores exchange keys in 3 columns name, api , secret""".trimMargin())
    val projDir = EnvConfig("PROJ_DIR",
            Paths.get(".").toAbsolutePath().toString().debug { logDebug { "WORKDIR=$it" } },
            """stores idempotent exchange data zipfiles in a hierarchy""".trimMargin())
    val agentDir = EnvConfig("AGENT_DIR",
            Path(projDir.value, "elsalvador", "target").toString(),
            """stores idempotent exchange data zipfiles in a hierarchy""".trimMargin())
    val binDir = EnvConfig("BIN_DIR",
            Path(projDir.value, "bin").toString(),
            """batch operation scripts dir""".trimMargin())
    val tmpDir = EnvConfig("TEMP", "/tmp", """self explanatory""".trimMargin())
    val valueAsset = EnvConfig("VALUE_ASSET", "USDT", "the scoring value equivalence")
    val tickerAssets = EnvConfig("TRACKED_ASSETS", "BNB/USDT", """these are the live updated tickers """.trimMargin())
    val timeAphasia = EnvConfig("TIME_APHASIA", "false", """bounce around the timeline for training""".trimMargin())
    val episodeLength = EnvConfig("EPISODE_LEN", "60", "Number of asset klines to store in an episode")
    val vmArgsSuggestion = EnvConfig("JAVA_ARGS", "-ea -Xmx9G --add-modules jdk.incubator.vector -XX:+UseFMA  -XX:-UseZGC  -XX:-UseG1GC  -XX:+UseParallelGC", "suggested verbose runtime jvm args")
    val progArgs = EnvConfig("_____", "BOOT_ELITES=2;CONN_DENSITY=.1;EVAL_THREADS=16 ;HIDDEN=121;HORIZON_DEPTH=15;JSON_REPLAY=true;MA=60;POPULATION=512;RESERVED_HIDDEN=100;ROLLING_HISTORY_SIZE=60;TRACKED_ASSETS=ADA/USDT LTC/BTC LTC/ETH DOGE/BTC BNB/USDT BNB/ETH BTC/USDT  ETH/USDT BNB/BTC LINK/USDT LTC/USDT   XLM/USDT XTZ/USDT XMR/BNB;USE_SIMD=true;USE_SINGLE_WIDTH=x", docString = "all the things being benchmarked at the time of checking this in ")
}

fun main() {
    EnvConfig.registry.forEach { println(it.toString()) }
}
