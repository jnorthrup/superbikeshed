#!/usr/bin/env kscript


@file:DependsOn("de.mpicbg.scicomp:kutils:0.9.0")
@file:DependsOn("de.mpicbg.scicomp:krangl:0.6")

import de.mpicbg.scicomp.kutils.evalBash
import krangl.*
import org.apache.commons.csv.CSVFormat
import kotlin.system.measureTimeMillis

require(args.size == 1) { "Usage: benchmark_kscript.kts <output_tsv>" }

//val resultFile = "scriptlet_runtimes_pr93.txt"
//val resultFile = "scriptlet_runtimes_master.txt"
val resultFile = args[0]

val examples = mapOf("printSum" to """println("1+2")""", "simpleSum" to """1+1""")


// first run with empty cache
fun clearCache() = evalBash("kscript --clear-cache")


fun evalKscript(kscriptlet: String, cached: Boolean = false): Long {
    if (cached) {
        require(evalBash("""kscript '$kscriptlet'""").exitCode == 0)
    } else {
        clearCache()
    }

    return measureTimeMillis {
        require(evalBash("""kscript '$kscriptlet'""").exitCode == 0) { "scriptlet eval failed for $kscriptlet" }
    }
}

//data class ScripletRuntime(val name:String, val cached:Boolean, val runtimeMs:Int)


//evalKscript(examples.values.first(), cached = true)
//val cachedR = (1..5).map {evalKscript(examples.values.first())}.map{ScripletRuntime}


//var runtimes = examples.toList().asDataFrame { mapOf("script_name" to it.first, "scriptlet" to it.second) }
//runtimes.outerJoin(DataFrame.builder("with_cached")(true, false))
//runtimes = runtimes.addColumn("runtime") { it["scriptlet"].asStrings().map { evalKscript(it!!) } }

System.err.println("running benchmark suite")

var runtimes = examples.toList().flatMap { ex ->
    listOf(true, false).flatMap { cached ->
        (1..10).toList().map { rep ->
            System.err.print(".")
            listOf(evalKscript(ex.second, cached)).asDataFrame { runtime ->
                mapOf(
                    "script_name" to ex.first,
                    //                "scriptlet" to ex.second,
                    "cached" to cached,
                    "runtime_ms" to runtime
                )
            }
        }
    }
}.bindRows()

runtimes.writeCSV(resultFile, CSVFormat.TDF)
