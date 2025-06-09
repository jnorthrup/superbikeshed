@file:Suppress("ControlFlowWithEmptyBody")

package borg.trikeshed.parse.json

import borg.trikeshed.common.collections.s_

import borg.trikeshed.lib.CharSeries.Companion.unbrace
import borg.trikeshed.lib.CharSeries.Companion.unquote
import borg.trikeshed.lib.Either
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.Twin
import borg.trikeshed.lib.combine
import borg.trikeshed.lib.first
import borg.trikeshed.lib.get
import borg.trikeshed.lib.second
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.`▶`
import borg.trikeshed.lib.α
import borg.trikeshed.lib.CharSeries
import borg.trikeshed.lib.debug
import borg.trikeshed.lib.drop
import borg.trikeshed.lib.isEmpty
import borg.trikeshed.lib.j
import borg.trikeshed.lib.map
import borg.trikeshed.lib.parseDoubleOrNull
import borg.trikeshed.lib.slice
import borg.trikeshed.lib.size
import borg.trikeshed.lib.take
import borg.trikeshed.lib.trim
import borg.trikeshed.lib.zipWithNext
// Removed MutableSeries and mutableSeriesOf imports
// import borg.trikeshed.lib.MutableSeries
// import borg.trikeshed.lib.mutableSeriesOf
import borg.trikeshed.core.emptySeries // Ensure core imports if not covered by lib
import borg.trikeshed.core.toSeries // Ensure core imports if not covered by lib


// Type aliases for clarity and expressiveness
typealias JsonBounds = Twin<Int> // (openIdx j closeIdx)
typealias JsonCommaIndices = Series<Int> // commaIdxs
typealias JsonStructuralIndices = Join<JsonBounds, JsonCommaIndices> // (openIdx j closeIdx) j commaIdxs

typealias JsonSegmentContent = Series<Char> // The raw characters of a segment
typealias JsonSegment = Join<JsonBounds, JsonSegmentContent> // (bounds) j raw_chars

typealias JsonParseContext = Join<JsonStructuralIndices, Series<Char>> // (structural_indices) j source_chars

typealias JsonPathElement = Either<String, Int>
typealias JsonPath = Series<JsonPathElement>

fun JsonStructuralIndices.toCharSeries(source: Series<Char>): Series<Char> = source[a.a until a.b]

val List<*>.toJsPath: JsonPath
    get() = this.toSeries() α {
        when (it) {
            is String -> JsonPathElement.left(it)
            is Int -> JsonPathElement.right(it)
            else -> throw IllegalArgumentException("expected String or Int, got ${it}")
        }
    }

/** delimiter-exclusive segments in a splittable json element.
 *  e.g. ",1," in "[0,1,2,3]" would be "1"
 *
 *  "[ 0 , " in "[0,1,2,3]" would be "0"
 *
 *  " 3 ]" in "[0,1,2,3]" would be "3"
 */
val JsonParseContext.segments: Iterable<JsonSegment>
    get() {
        val (structuralIndices, sourceChars) = this
        val (openIdx, closeIdx) = structuralIndices.first
        val commaIdxs: JsonCommaIndices = combine(s_[openIdx], structuralIndices.second, s_[closeIdx])
        return commaIdxs.`▶`.zipWithNext().map { (a: Int, b: Int) -> a.inc() j b }.toList() α { it j sourceChars }
    }

/** a json parser that indexes and optionally reifies the json chars
 * and provides a way to query it.
 */
object JsonParser {
    /** includes open and close braces and provides a list of comma indexes*/
    fun index(
        src: Series<Char>,
        /** depths parameter removed for this refactoring iteration.
         * depths: MutableSeries<Int>? = null,
         */
        /*  * an optional int that gives you n commas max, presuming undefined null bias in the last comma */
        takeFirst: Int? = null,
    ): JsonStructuralIndices {
        var depth = 0
        var openIdx = -1
        var closeIdx = -1
        val tempCommaIdxs = mutableListOf<Int>() // Changed from MutableSeries to MutableList
        var insideQuote = false
        var escapeNextChar = false
        var maxDepth = 0 // Retained for depth calculation, but not added to any collection in this func
        for (i in 0 until src.size) {
            val c: Char = src[i]
            when {

                insideQuote -> when {
                    escapeNextChar -> escapeNextChar = false
                    c == '\\' -> escapeNextChar = true
                    c == '"' -> insideQuote = false
                }

                else -> when (c) {

                    '{', '[' -> {
                        depth++.also { if (it > maxDepth) maxDepth = it }
                        if (depth == 1) openIdx = i
                    }

                    '}', ']' -> {
                        // if (depth == 1) depths?.add(maxDepth) // depths related logic removed
                        depth--
                        if (depth == 0) {
                            closeIdx = i
                            break
                        }
                    }

                    ',' -> if (depth == 1) {
                        tempCommaIdxs.add(i) // Changed from commaIdxs.add(i)

                        //record and reset maxDepth
                        // depths?.add(maxDepth) // depths related logic removed
                        maxDepth = 0 // maxDepth reset retained for correct depth calculation if needed later
                        if (takeFirst != null && tempCommaIdxs.size >= takeFirst) break // Used tempCommaIdxs
                    }

                    '"' -> insideQuote = true
                }
            }
        }
        return (openIdx j closeIdx) j tempCommaIdxs.toSeries() // Convert temp list to Series


    }

    fun reify(
        /** includes open and close braces, or both quotes, or the raw type*/
        src1: Series<Char>,
    ): Any? {
        val src: CharSeries = CharSeries(src1).trim

        return when (val c: Char = src.mk.get) {
            '{', '[' -> {
                val structuralIndices: JsonStructuralIndices = index(src)
                val (openIdx: Int, closeIdx: Int) = structuralIndices.first
                val commaIdxs: JsonCommaIndices = structuralIndices.second

                val isObj = '{' == c
                //if obj we create k-v pairs otherwise we create values

                //iterate  segments exclusive of src first and last and commas in the middle
                val combine: Series<Int> = combine(s_[openIdx], commaIdxs, s_[closeIdx])
                if (commaIdxs.isEmpty()) {
                    val (before, after) = combine.toArray()
                    val possiblyEmpty = src.clone().lim(after).pos(before + 1).trim
                    if (!possiblyEmpty.hasRemaining)
                        return if (isObj) emptyMap<String, Any?>()
                        else emptyArray<Any?>()
                }

                combine.`▶`.zipWithNext().map { (before, after) ->
                    if (isObj) {
                        val tmp = CharSeries(src[before.inc() until after]).trim
                        require(tmp.seekTo('"')) {
                            "malformed open quote in ${tmp.take(40).asString()}"
                        }
                        tmp.pos.let { openQuote ->
                            require(tmp.seekTo('"', '\\')) {
                                "malformed close-quote in ${tmp.take(40).asString()}"
                            }
                            (tmp.pos - 1).let { closeQuote ->
                                require(tmp.seekTo(':')) {
                                    "expected colon in ${tmp.take(40).asString()}"
                                }
                                tmp.slice.let { valueContext ->
                                    tmp.lim(closeQuote).pos(openQuote).asString() j reify(valueContext)
                                }
                            }
                        }
                    } else reify(CharSeries(src[before.inc() until after]).trim)
                }.let {
                    if (isObj) it.associate {
                        val join = it as Join<*, *>
                        val (key, value) = join
                        key.let {
                            it as? String ?: (it as? Series<Char>)?.asString() ?: (it as? CharSeries)?.asString()
                            ?: it
                        } to value
                    } else it as? String ?: (it as? Series<Char>)?.asString() ?: (it as? CharSeries)?.asString()
                    ?: it
                }
            }

            '"' -> {
                val beg = src.pos
                val seekTo = src.seekTo('"', '\\')
                if (!seekTo) throw Exception("expected end of quoted string")
                val end = src.pos - 1
                src.lim(end).pos(beg).asString()
            }

            't', 'f' -> 't' == c
//            'n' -> null
            else -> src.res.slice.parseDoubleOrNull()
        }
    }

    /** a recursive depth-first search of the json tree, the path is a series of strings and ints,
     *  the ints are indexes into elements, the strings are keys into objects exclusively
     *  @param context the current context, the element and the src
     *  @param path the path to the desired node
     *  @param depths the depths of the nodes, this is used to skip nodes that are too shallow
     *  @param reifyResult whether to reify the payload or return the JsonSegment
     *  @return on success the payload, on any other outcome Unit is returned
     */
    fun jsPath(
        /**contains the indexes and the src chars */
        context: JsonParseContext,
        /** strings will enter by obj key, indexes will deliver nth slot for obj or array*/
        path: JsonPath,
        /**whether success payload is reified or JsonSegment */
        reifyResult: Boolean = true,
        /**path and depth have a relationship, path needs depth to succeed, so this can aid in skipping shallow
        nodes */
        depths: List<Int>? = null,
    ): Any? {
        val (pathHead: JsonPathElement, pathTail: JsonPath) = path.first() j path.drop(1)

        return pathHead.fold(
            /** this, String branch, performs the search of a key by descending into each segment, and looking for a
             * key that matches, and then will recurse into that, or return the desired form
             */
            selectByKey(context, pathTail, reifyResult),
            /** this will check for enough segments to select the desired element, and
             *  then will recurse into that or return the desired form, discarding the
             *  segment key as necessary.  This works on both obj and array */
            selectByIndex(context, pathTail, reifyResult)
        )
    }

    private fun selectByKey(
        context: JsonParseContext,
        pathTail: JsonPath,
        reifyResult: Boolean,
    ): (String) -> Any? = { key: String ->
        val (structuralIndices: JsonStructuralIndices, sourceChars) = context
        var r: Any? = Unit
        val cs = CharSeries(sourceChars).trim
        if (unbrace(cs)) {
            for (segment in (context.segments α { t ->
                val (bounds: JsonBounds, src: JsonSegmentContent) = t
                val (pos, lim) = bounds
                CharSeries(src, pos, lim).trim
            })) {
                val tmp = (segment.trim).slice
                val valueMark = tmp.seekTo(':')
                if (!valueMark) continue // Malformed segment, skip
                val value = tmp.slice //
                val key0 = (tmp.dec().flip()).trim

                if (unquote(key0) && key0.seekTo(key.toSeries()) && (!key0.hasRemaining)) {
                    r = resumePath(pathTail, reifyResult, value)
                    break
                }
            }
        }
        r
    }


    private fun resumePath(
        pathTail: JsonPath,
        reifyResult: Boolean,
        tmp: CharSeries,
    ): Any? {
        return if (pathTail.isEmpty()) {
            if (reifyResult) reify(tmp.slice) else tmp.slice

        } else {
            // val depths1: MutableSeries<Int> = mutableSeriesOf() // Removed depths1
            val nextPath = pathTail.take(1).first()
            jsPath(
                // index no longer takes depths1. Pass null for depths in jsPath call.
                index(tmp, takeFirst = nextPath.rightOrNull?.inc()) j tmp,
                pathTail,
                reifyResult,
                null // depths argument to jsPath is now null
            )
        }
    }

    fun selectByIndex(
        context: JsonParseContext,
        pathTail: JsonPath,
        reifyResult: Boolean,
    ): (Int) -> Any? = { idx: Int ->
        var r: Any? = Unit
        val (structuralIndices: JsonStructuralIndices, sourceChars) = context
        val (pos, lim) = structuralIndices.first
        val cs = CharSeries(sourceChars, pos, lim).trim
        val inObj = cs[0] == '{'
        do {
            val tmp = CharSeries(context.segments.elementAtOrNull(idx)?.toCharSeries(sourceChars) ?: break)
            val value: CharSeries = if (inObj) {
                if (!tmp.seekTo(':')) break
                tmp.slice
            } else tmp

            r = resumePath(pathTail, reifyResult, value)
        } while (false)
        r
    }
}
