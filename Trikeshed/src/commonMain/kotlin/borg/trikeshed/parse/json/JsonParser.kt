package borg.trikeshed.parse.json

import borg.trikeshed.lib.*
import borg.trikeshed.lib.CharSeries.Companion.unbrace
import borg.trikeshed.lib.CharSeries.Companion.unquote

typealias JsElement = Join<Twin<Int>, Series<Int>> //(openIdx j closeIdx) j commaIdxs
typealias JsIndex = Join<Twin<Int>, Series<Char>> //(twin j src)
typealias JsContext = Join<JsElement, Series<Char>>

typealias JsPathElement = Either<String, Int>
typealias JsPath = Series<JsPathElement>

private fun logDebug(t: () -> String) {} //logging turned off for now

fun JsIndex.toSeries(): Series<Char> = this.second [ a.a until a.b]

val List<*>.toJsPath: JsPath
 get() = this.toSeries() α {
 when (it) {
 is String -> JsPathElement.left(it)
 is Int -> JsPathElement.right(it)
 else -> throw IllegalArgumentException("expected String or Int, got $it")
 }
 }

val JsContext.segments: Iterable<JsIndex>
 get() {
 val (element, src) = this
 val (openIdx, closeIdx) = element.first
 val commaIdxs: Series<Int> = combine(s_[openIdx], element.second, s_[closeIdx])
 return commaIdxs. `play` .zipWithNext().map { (a: Int, b: Int) -> a.inc() j b }.toList() α { it j src }
 }

object JsonParser {
 
 fun index(
 src: Series<Char>,
 depths: MutableList<Int>? = null,
 takeFirst: Int? = null,
 ): JsElement {
 var depth = 0
 var openIdx = -1
 var closeIdx = -1
 val commaIdxs: MutableList<Int> = mutableListOf()
 var insideQuote = false
 var escapeNextChar = false
 var maxDepth = 0
 
 for (i in 0 until src.size) {
 val c: Char = src[i]
 when {
 
 insideQuote -> when {
 escapeNextChar -> escapeNextChar = false
 c == '\\' -> escapeNextChar = true
 c == '\"' -> insideQuote = false
 }
 c == '\"' -> insideQuote = true
 '{', '[' -> if (!insideQuote) {
 if (depth == 0) openIdx = i
 depth++
 if (depth > maxDepth) maxDepth = depth
 }
 '}', ']' -> if (!insideQuote) {
 depth--
 if (depth == 0) {
 closeIdx = i
 break
 }
 }
 ',' -> if (!insideQuote && depth == 1) {
 commaIdxs.add(i)
 depths?.add(maxDepth)
 maxDepth = 0
 if (takeFirst != null && commaIdxs.size >= takeFirst) break
 }
 }
 }
 return (openIdx j closeIdx) j commaIdxs.toIntArray().toSeries()
 }

 fun reify(src1: Series<Char>): Any? {
 val src: CharSeries = CharSeries(src1).trim
 
 return when (val c: Char = if (src.hasRemaining) src[src.pos] else return null) {
 '{', '[' -> {
 val index: JsElement = index(src)
 val (openIdx: Int, closeIdx: Int) = index.first
 val commaIdxs: Series<Int> = index.second
 
 val isObj = '{' == c
 
 val combine: Series<Int> = combine(s_[openIdx], commaIdxs, s_[closeIdx])
 if (commaIdxs.isEmpty() && src.rem > 2) {
 val (before, after) = combine.toArray()
 val possiblyEmpty = src.clone().lim(after).pos(before + 1).trim
 if (!possiblyEmpty.hasRemaining)
 return if (isObj) emptyMap<String, Any?>() else emptyList<Any?>()
 else
 return if (isObj) {
 val entry = reify(possiblyEmpty) as? String ?: return emptyMap<String, Any?>()
 val colonIdx = entry.indexOf(':')
 if (colonIdx == -1) return emptyMap<String, Any?>()
 val key = entry.substring(0, colonIdx).trim().removeSurrounding("\"")
 val value = reify(entry.substring(colonIdx + 1).trim().toSeries())
 mapOf(key to value)
 } else listOf(reify(possiblyEmpty))
 }
 
 if (isObj) {
 (index(src) j src).segments.associate { segment ->
 val cs = CharSeries(segment.toSeries())
 if (!cs.seekTo(':')) throw Exception("Malformed object entry: ${cs.asString()}")
 val value = reify(cs.slice)
 val key = reify(cs.pos(0).lim(cs.pos - 1)) as String
 key to value
 }
 } else {
 (index(src) j src).segments.map { segment -> reify(segment.toSeries()) }.toList()
 }
 }
 '"' -> {
 val content = src.clone()
 unquote(content)
 content.asString()
 }
 't', 'f' -> 't' == c
 'n' -> null
 else -> src.asString().toDoubleOrNull()
 }
 }

 private fun String.toSeries(): Series<Char> = length j { index: Int -> this[index] }


 fun jsPath(
 context: JsContext,
 path: JsPath,
 reifyResult: Boolean = true,
 depths: List<Int>? = null,
 ): Any? {
 if (path.isEmpty()) return if (reifyResult) reify(context.second) else context.second
 val (pathHead: JsPathElement, pathTail: JsPath) = path.first() j path.drop(1)

 return pathHead.fold(
 selectByKey(context, pathTail, reifyResult),
 selectByIndex(context, pathTail, reifyResult)
 )
 }

 private fun selectByKey(
 context: JsContext,
 pathTail: Series<Either<String, Int>>,
 reifyResult: Boolean,
 ): (String) -> Any? = { key: String ->
 var r: Any? = null
 val cs = CharSeries(context.second).trim
 if (unbrace(cs)) {
 for (segment in (context.segments α { t ->
 val (bounds: Twin<Int>, src: Series<Char>) = t
 val (pos, lim) = bounds
 CharSeries(src, pos, lim).trim
 })) {
 val tmp = (segment.trim).slice
 if (!tmp.seekTo(':')) continue
 val colonPos = tmp.pos
 val value = tmp.slice
 val key0 = tmp.pos(0).lim(colonPos - 1).trim
 
 if (unquote(key0) && key0.asString() == key) {
 r = resumePath(pathTail, reifyResult, value)
 break
 }
 }
 }
 r
 }


 private fun resumePath(
 pathTail: Series<Either<String, Int>>,
 reifyResult: Boolean,
 tmp: CharSeries,
 ): Any? {
 return if (pathTail.isEmpty()) {
 if (reifyResult) reify(tmp.slice) else tmp.slice
 } else {
 val depths1: MutableList<Int> = mutableListOf()
 val nextPath = pathTail.take(1).first()
 jsPath(
 index(tmp, depths1, nextPath.rightOrNull?.inc()) j tmp,
 pathTail,
 reifyResult,
 depths1
 )
 }
 }

 fun selectByIndex(
 context: JsContext,
 pathTail: Series<Either<String, Int>>,
 reifyResult: Boolean,
 ): (Int) -> Any? = { idx: Int ->
 var r: Any? = null
 val (element: JsElement, src) = context
 val (twin, _) = element
 val (pos, lim) = twin
 val cs = CharSeries(src, pos, lim).trim
 val inObj = cs[0] == '{'
 do {
 val tmp = CharSeries(context.segments.elementAtOrNull(idx)?.toSeries() ?: break)
 val value: CharSeries = if (inObj) {
 if (!tmp.seekTo(':')) break
 tmp.slice
 } else tmp

 r = resumePath(pathTail, reifyResult, value)
 } while (false)
 r
 }
}