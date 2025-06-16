package borg.trikeshed.acapulco.runtime

import java.util.*

val RANDOM = Random(System.currentTimeMillis())

var counter = 0
/*

fun randomUsers(usercount: Int) = (0..usercount).map {
    Avatar(
            pairOf(
                    arrayOf(FirstNames.values(), Surnames.values()).map {
                        it[RANDOM.nextInt(it.size)].name
                    }.toTypedArray())
    )
}
*/

/**
 * arg0 is the marketplace region/site
 * arg1 is the subtopic name or sectionname
 */
typealias marketArgTuple = Array<out String>
/*
fun main(args: marketArgTuple) {
    val chomskyBot = chomskyBot(randomUsers(3),
                                Corpus.chomsky,
                                Topic("any and all"), 10)
    chomskyBot.forEach(::println)
}*/

fun marketName(args: marketArgTuple) = when {
    args.isNotEmpty() -> args[0]
    else -> "marketplace"
}

fun sectionName(args: marketArgTuple) = marketName(args) + "." + when {
    args.size > 1 -> args[1]
    else -> "fleamarket"
}

/** a logger
 *
 */
fun info(msg: String) = System.err.println("INFO: " + System.currentTimeMillis() + ":" + msg)



