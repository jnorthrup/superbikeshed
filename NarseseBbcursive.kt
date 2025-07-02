@file:Suppress("FunctionName")

package borg.trikeshed.parse.narsese

import borg.trikeshed.lib.*
import borg.trikeshed.parse.bbcursive.ann.Backtracking
import borg.trikeshed.parse.bbcursive.ann.Skipper
import borg.trikeshed.parse.bbcursive.ann.Infix
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicInteger

// bbcursive-style operator for ByteIndexedBuffer
fun interface BbOp { fun apply(buffer: ByteIndexedBuffer): ByteIndexedBuffer? }

/**
 * Fully left-recursive Narsese parser using bbcursive patterns
 * Semantic tags captured as tuple elements for runtime introspection
 */
object NarseseBbcursive {
    
    // Semantic node types as data
    data class NodeType(val id: Int, val name: String, val description: String)
    
    // Node type registry - these become tuple elements
    object Nodes {
        val VARIABLE = NodeType(100, "variable", "Variable binding")
        val WORD = NodeType(200, "word", "Atomic term")
        val OPERATION = NodeType(300, "operation", "Operation application")
        val COMPOUND = NodeType(400, "compound", "Compound term")
        val STATEMENT = NodeType(500, "statement", "Statement expression")
        val RELATIONSHIP = NodeType(501, "relationship", "Relational statement")
        val JUDGEMENT = NodeType(600, "judgement", "Truth judgement")
        val GOAL = NodeType(601, "goal", "Goal statement")
        val QUESTION = NodeType(602, "question", "Query statement")
        val DESIRE = NodeType(603, "desire", "Desire statement")
        val TASK = NodeType(700, "task", "Task with optional budget")
    }
    
    // Copula semantic tags
    data class Copula(val symbol: String, val name: String, val meaning: String)
    val copulas = listOf(
        Copula("-->", "inheritance", "is a"),
        Copula("<->", "similarity", "is similar to"),
        Copula("==>", "implication", "implies"),
        Copula("<=>", "equivalence", "is equivalent to"),
        Copula("=/>", "predictive_implication", "predicts"),
        Copula("=\\>", "retrospective_implication", "explains"),
        Copula("</>", "predictive_equivalence", "predicts equivalently"),
        Copula("<\\>", "retrospective_equivalence", "explains equivalently"),
        Copula("{--", "instance", "is an instance of"),
        Copula("--]", "property", "has property"),
        Copula("{-]", "instance_property", "instance with property"),
        Copula("--[", "internal_property", "has internal property"),
        Copula("[-]", "internal_instance_property", "internal instance-property")
    )
    
    // Conjunction semantic tags
    data class Conjunction(val symbol: String, val name: String, val temporal: String)
    val conjunctions = listOf(
        Conjunction("&&", "and", "parallel"),
        Conjunction("||", "or", "parallel"),
        Conjunction("&|", "sequential_and", "sequential"),
        Conjunction("&/", "sequential_and_timed", "sequential with interval"),
        Conjunction("&-", "parallel", "parallel events"),
        Conjunction("-", "negation", "atemporal"),
        Conjunction("~", "negation_alt", "atemporal")
    )
    
    // Tense semantic tags
    data class Tense(val symbol: String, val name: String, val time: String)
    val tenses = listOf(
        Tense(":|:", "present", "current moment"),
        Tense(":\\:", "past", "before now"),
        Tense(":/:", "future", "after now")
    )
    
    // Variable type semantic tags
    data class VarType(val prefix: Char, val name: String, val binding: String)
    val varTypes = listOf(
        VarType('$', "independent", "substitution"),
        VarType('#', "dependent", "skolemization"),
        VarType('?', "query", "pattern matching"),
        VarType('%', "pattern", "pattern variable")
    )
    
    // Parse result with semantic tags
    data class ParseResult(
        val success: Boolean,
        val position: Int,
        val nodeType: NodeType,
        val semanticTag: Any? = null
    )
    
    // Feature recording buffer with semantic tags
    val features = mutableListOf<Join<NodeType, Join<Int, Any?>>>()
    
    // Record parse features with semantic information
    fun recordFeature(nodeType: NodeType, position: Int, semanticTag: Any? = null) {
        features.add(nodeType j (position j semanticTag))
    }
    
    // bbcursive sequencing with backtracking
    fun bb(buffer: ByteIndexedBuffer?, vararg ops: BbOp): ByteIndexedBuffer? {
        var result = buffer
        val startPos = buffer?.pos ?: return null
        
        for (op in ops) {
            result = op.apply(result ?: run {
                buffer.pos(startPos) // backtrack on failure
                return null
            }) ?: run {
                buffer.pos(startPos) // backtrack on failure
                return null
            }
        }
        return result
    }
    
    // Character literal matching
    fun ch(c: Char): BbOp = BbOp { buffer ->
        if (buffer.hasRemaining && buffer.get == c.code.toByte()) buffer else null
    }
    
    // String literal matching with semantic tag
    fun str(s: String, tag: Any? = null): BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        for (c in s) {
            if (!buffer.hasRemaining || buffer.get != c.code.toByte()) {
                buffer.pos(startPos)
                return@BbOp null
            }
        }
        tag?.let { recordFeature(Nodes.WORD, startPos, it) }
        buffer
    }
    
    // Optional operator
    fun opt(op: BbOp): BbOp = BbOp { buffer ->
        op.apply(buffer) ?: buffer
    }
    
    // Alternative operator with semantic tag extraction
    fun anyOf(alternatives: List<Pair<BbOp, Any?>>): BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        for ((op, tag) in alternatives) {
            op.apply(buffer)?.let { 
                tag?.let { recordFeature(Nodes.WORD, startPos, it) }
                return@BbOp it 
            }
            buffer.pos(startPos) // reset for next attempt
        }
        null
    }
    
    // Zero or more with separator (left-recursive)
    fun zeroOrMore(separator: BbOp, item: BbOp, nodeType: NodeType): BbOp = BbOp { buffer ->
        var result = buffer
        var count = 0
        
        while (true) {
            val sepPos = result.pos
            val sep = separator.apply(result)
            if (sep == null) break
            
            val itemPos = sep.pos
            val parsed = item.apply(sep)
            if (parsed == null) {
                result.pos(sepPos) // backtrack before separator
                break
            }
            
            recordFeature(nodeType, itemPos, count)
            result = parsed
            count++
        }
        
        result
    }
    
    // Whitespace skipper
    @Skipper
    val ws: BbOp = BbOp { buffer -> buffer.skipWs }
    
    // Terminal symbols
    val dot = ch('.')
    val exclamation = ch('!')
    val question = ch('?')
    val amp = ch('&')
    val percent = ch('%')
    val dollar = ch('$')
    val semicolon = ch(';')
    val comma = ch(',')
    val lt = ch('<')
    val gt = ch('>')
    val lparen = ch('(')
    val rparen = ch(')')
    val quote = ch('"')
    
    // Copula operators with semantic tags
    val copula: BbOp = anyOf(copulas.map { cop ->
        str(cop.symbol) to cop
    })
    
    // Conjunction operators with semantic tags
    val conjunction: BbOp = anyOf(conjunctions.map { conj ->
        str(conj.symbol) to conj
    })
    
    // Tense markers with semantic tags
    val tense: BbOp = anyOf(tenses.map { t ->
        str(t.symbol) to t
    })
    
    // Variable types with semantic tags
    val variable: BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        
        val varTypeChar = buffer.get
        val varType = varTypes.find { it.prefix == varTypeChar.toInt().toChar() }
        
        if (varType == null) {
            buffer.pos(startPos)
            return@BbOp null
        }
        
        // Variable name - alphanumeric
        var nameStart = buffer.pos
        val nameChars = mutableListOf<Char>()
        while (buffer.hasRemaining) {
            val c = buffer.get
            val char = c.toInt().toChar()
            if (!char.isLetterOrDigit()) {
                buffer.dec()
                break
            }
            nameChars.add(char)
        }
        
        if (buffer.pos == nameStart) {
            buffer.pos(startPos)
            return@BbOp null
        }
        
        val varName = nameChars.joinToString("")
        recordFeature(Nodes.VARIABLE, startPos, varType j varName)
        buffer
    }
    
    // Numeric value parser
    val numeric: BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        var hasDigits = false
        val isNegative = buffer.hasRemaining && buffer.get == '-'.code.toByte()
        
        if (!isNegative && buffer.pos > startPos) {
            buffer.dec() // wasn't a sign
        }
        
        // Integer part
        while (buffer.hasRemaining && buffer.get.toInt().toChar().isDigit()) {
            hasDigits = true
        }
        if (!hasDigits) {
            buffer.pos(startPos)
            return@BbOp null
        }
        buffer.dec()
        
        // Optional decimal
        if (buffer.hasRemaining && buffer.get == '.'.code.toByte()) {
            while (buffer.hasRemaining && buffer.get.toInt().toChar().isDigit()) {}
            buffer.dec()
        } else {
            buffer.dec()
        }
        
        buffer
    }
    
    // Truth value with semantic tags: %frequency[;confidence]%
    val truth: BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        bb(buffer, percent)?.let { b1 ->
            numeric.apply(b1)?.let { b2 ->
                val freqEnd = b2.pos
                val freq = buffer.slice.pos(startPos + 1).lim(freqEnd).decodeToString()
                
                val confOp = BbOp { buf -> bb(buf, semicolon, numeric) }
                val confResult = opt(confOp).apply(b2)!!
                val conf = if (confResult.pos > b2.pos) {
                    buffer.slice.pos(b2.pos + 1).lim(confResult.pos).decodeToString()
                } else null
                
                bb(confResult, percent)?.also {
                    recordFeature(Nodes.WORD, startPos, "truth" j (freq j conf))
                }
            }
        }
    }
    
    // Budget value with semantic tags: $priority[;durability]$
    val budget: BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        bb(buffer, dollar)?.let { b1 ->
            numeric.apply(b1)?.let { b2 ->
                val prioEnd = b2.pos
                val priority = buffer.slice.pos(startPos + 1).lim(prioEnd).decodeToString()
                
                val durOp = BbOp { buf -> bb(buf, semicolon, numeric) }
                val durResult = opt(durOp).apply(b2)!!
                val durability = if (durResult.pos > b2.pos) {
                    buffer.slice.pos(b2.pos + 1).lim(durResult.pos).decodeToString()
                } else null
                
                bb(durResult, dollar)?.also {
                    recordFeature(Nodes.WORD, startPos, "budget" j (priority j durability))
                }
            }
        }
    }
    
    // Quoted string for words
    val quotedString: BbOp = BbOp { buffer ->
        val startPos = buffer.pos
        bb(buffer, quote) ?: return@BbOp null
        
        val chars = mutableListOf<Char>()
        while (buffer.hasRemaining) {
            val c = buffer.get
            when (c) {
                '"'.code.toByte() -> {
                    recordFeature(Nodes.WORD, startPos, "quoted" j chars.joinToString(""))
                    return@BbOp buffer
                }
                '\\'.code.toByte() -> {
                    if (buffer.hasRemaining) {
                        chars.add(buffer.get.toInt().toChar())
                    }
                }
                else -> chars.add(c.toInt().toChar())
            }
        }
        null
    }
    
    // Word - either quoted string or alphanumeric with semantic tag
    val word: BbOp = anyOf(listOf(
        quotedString to null,
        BbOp { buffer ->
            val start = buffer.pos
            val chars = mutableListOf<Char>()
            while (buffer.hasRemaining) {
                val c = buffer.get.toInt().toChar()
                if (c.isLetterOrDigit() || c == '_') {
                    chars.add(c)
                } else {
                    buffer.dec()
                    break
                }
            }
            if (chars.isNotEmpty()) {
                recordFeature(Nodes.WORD, start, "atom" j chars.joinToString(""))
                buffer
            } else null
        } to null
    ))
    
    // Forward declarations for left-recursive grammar
    lateinit var term: BbOp
    lateinit var statement: BbOp
    lateinit var compoundTerm: BbOp
    
    // Relationship with semantic tags: <term copula term>
    val relationship: BbOp = BbOp { buffer ->
        val start = buffer.pos
        bb(buffer, ws, lt, ws)?.let { b1 ->
            term.apply(b1)?.let { b2 ->
                bb(b2, ws)?.let { b3 ->
                    copula.apply(b3)?.let { b4 ->
                        bb(b4, ws)?.let { b5 ->
                            term.apply(b5)?.let { b6 ->
                                bb(b6, ws, gt)?.also {
                                    recordFeature(Nodes.RELATIONSHIP, start, features.takeLast(3))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Operation with semantic tags: (^operator term*)
    val operation: BbOp = BbOp { buffer ->
        val start = buffer.pos
        bb(buffer, lparen, ch('^'))?.let { b1 ->
            word.apply(b1)?.let { b2 ->
                val opName = features.last()
                val afterOp = zeroOrMore(comma, term, Nodes.WORD).apply(b2)
                bb(afterOp, rparen)?.also {
                    val args = features.takeLast(features.size - features.indexOf(opName) - 1)
                    recordFeature(Nodes.OPERATION, start, opName j args)
                }
            }
        }
    }
    
    // List parser for compound terms with semantic tags
    fun listParser(open: String, sep: BbOp, close: String, nodeType: NodeType): BbOp = BbOp { buffer ->
        val start = buffer.pos
        bb(buffer, str(open), ws)?.let { buf ->
            val beforeTerms = features.size
            // First element
            term.apply(buf)?.let { first ->
                // Rest of elements (left-recursive accumulation)
                val rest = zeroOrMore(BbOp { b -> bb(b, ws, sep, ws) }, term, nodeType).apply(first)
                bb(rest, ws, str(close))?.also {
                    val terms = features.drop(beforeTerms)
                    recordFeature(nodeType, start, terms)
                }
            } ?: bb(buf, str(close))?.also {
                recordFeature(nodeType, start, emptyList<Any>())
            }
        }
    }
    
    // Compound term with semantic tags: (conjunction term*)
    val compoundTermImpl: BbOp = BbOp { buffer ->
        listParser("(", conjunction, ")", Nodes.COMPOUND).apply(buffer)
    }
    
    // Statement with semantic tags: relationship | operation | term
    val statementImpl: BbOp = BbOp { buffer ->
        val start = buffer.pos
        val beforeParse = features.size
        anyOf(listOf(
            relationship to "relationship",
            operation to "operation", 
            term to "term"
        )).apply(buffer)?.also {
            val content = features.drop(beforeParse)
            recordFeature(Nodes.STATEMENT, start, content)
        }
    }
    
    // Term with semantic tags: word | variable | compoundTerm | statement  
    val termImpl: BbOp = BbOp { buffer ->
        anyOf(listOf(
            word to "word",
            variable to "variable",
            compoundTerm to "compound",
            statement to "statement"
        )).apply(buffer)
    }
    
    // Judgement with semantic tags: statement . [tense] [truth]
    val judgement: BbOp = BbOp { buffer ->
        val start = buffer.pos
        val beforeParse = features.size
        bb(buffer, statement, ws, dot, ws, opt(tense), ws, opt(truth))?.also {
            val parts = features.drop(beforeParse)
            recordFeature(Nodes.JUDGEMENT, start, parts)
        }
    }
    
    // Goal with semantic tags: statement ! [truth]
    val goal: BbOp = BbOp { buffer ->
        val start = buffer.pos
        val beforeParse = features.size
        bb(buffer, statement, ws, exclamation, ws, opt(truth))?.also {
            val parts = features.drop(beforeParse)
            recordFeature(Nodes.GOAL, start, parts)
        }
    }
    
    // Question with semantic tags: statement ? [tense]
    val questionOp: BbOp = BbOp { buffer ->
        val start = buffer.pos
        val beforeParse = features.size
        bb(buffer, statement, ws, question, ws, opt(tense))?.also {
            val parts = features.drop(beforeParse)
            recordFeature(Nodes.QUESTION, start, parts)
        }
    }
    
    // Desire with semantic tags: statement & [tense]
    val desire: BbOp = BbOp { buffer ->
        val start = buffer.pos
        val beforeParse = features.size
        bb(buffer, statement, ws, amp, ws, opt(tense))?.also {
            val parts = features.drop(beforeParse)
            recordFeature(Nodes.DESIRE, start, parts)
        }
    }
    
    // Sentence with semantic tags: judgement | goal | question | desire
    val sentence: BbOp = anyOf(listOf(
        judgement to "judgement",
        goal to "goal",
        questionOp to "question",
        desire to "desire"
    ))
    
    // Task with semantic tags: [budget] sentence
    val task: BbOp = BbOp { buffer ->
        val start = buffer.pos
        val beforeParse = features.size
        bb(buffer, ws, opt(budget), ws, sentence, ws)?.also {
            val parts = features.drop(beforeParse)
            recordFeature(Nodes.TASK, start, parts)
        }
    }
    
    // Initialize forward references
    init {
        term = termImpl
        statement = statementImpl
        compoundTerm = compoundTermImpl
    }
    
    // Parse function with semantic extraction
    fun parse(narseseString: String): Join<Boolean, List<Join<NodeType, Join<Int, Any?>>>> {
        features.clear()
        
        val buffer = ByteIndexedBuffer(narseseString)
        val result = task.apply(buffer)
        
        val success = result != null && !result.hasRemaining
        return success j features.toList()
    }
}