package fiduciary.attention

import kotlin.test.Test
import kotlin.test.assertEquals
import borg.trikeshed.lib.j
import kotlinx.coroutines.runBlocking

class FiduciaryAttentionContextTest {
    @Test/**indeed, perhaps we need a rete engine for file navigation with decompression? i was hoping static dispatch would be adequate... but yes now we come to the interest in the conversations from patrick devine about the fidelity funds and the mutual funds and trust funds 
     */
    fun testDrivingInterest() {
        // Minimal stub for context and metadata
        val metadata = object {
            internal val map = mutableMapOf<String, String>()
            fun add(key: String, value: String) { map[key] = value }
            fun get(key: String): String? = map[key]
        }
        val parseContext = Any()
        val ioContext = Any()
        val context = object {
            val metadata = metadata
            val parseContext = parseContext
            val ioContext = ioContext
        }
        // Simulate document creation with driving interest
        metadata.add("drivingInterest", "trust law")
        // Minimal DocumentAttention and FiduciaryAttention stubs
        val doc = object {}
        val fid = object {
            val document = doc
            val context = context
            fun drivingInterest(): String? = context.metadata.get("drivingInterest")
        }
        assertEquals("trust law", fid.drivingInterest())
    }

    @Test
    fun testAttentionProvidesNewOptions() {
        // Initial context with driving interest
        val metadata = object {
            internal val map = mutableMapOf<String, String>()
            fun add(key: String, value: String) { map[key] = value }
            fun get(key: String): String? = map[key]
        }
        metadata.add("drivingInterest", "trust law")
        val context = object {
            val metadata = metadata
        }
        // TDD: Declare how Attention should look
        class Attention(val context: Any) {
            fun options(): List<String> {
                // Fake it: return hardcoded options for now
                return listOf("fidelity funds", "mutual funds", "trust funds")
            }
        }
        val attention = Attention(context)
        val options = attention.options()
        // Assert that attention provides the expected new options
        kotlin.test.assertTrue(options.contains("fidelity funds"))
        kotlin.test.assertTrue(options.contains("mutual funds"))
        kotlin.test.assertTrue(options.contains("trust funds"))
        kotlin.test.assertEquals(3, options.size)
    }

    @Test
    fun testAttentionPresentsOptionsToInterest() {
        // Root: file listing with Patrick's URL
        val fileListing = listOf("patrick_devine.txt", "other_fund.txt")
        val rootInterest = object {
            val files = fileListing
        }
        // Attention presents options at the root
        class Attention(val interest: Any) {
            fun options(): List<String> = when (interest) {
                is String -> when (interest) {
                    "patrick_devine.txt" -> listOf("fidelity funds", "mutual funds", "trust funds")
                    "other_fund.txt" -> listOf("index funds", "etfs")
                    else -> emptyList()
                }
                is rootInterest::class -> rootInterest.files
                else -> emptyList()
            }
        }
        // At root, present file options
        val rootAttention = Attention(rootInterest)
        val rootOptions = rootAttention.options()
        kotlin.test.assertTrue(rootOptions.contains("patrick_devine.txt"))
        kotlin.test.assertTrue(rootOptions.contains("other_fund.txt"))
        // Simulate reading patrick_devine.txt
        val patrickAttention = Attention("patrick_devine.txt")
        val patrickOptions = patrickAttention.options()
        kotlin.test.assertTrue(patrickOptions.contains("fidelity funds"))
        kotlin.test.assertTrue(patrickOptions.contains("mutual funds"))
        kotlin.test.assertTrue(patrickOptions.contains("trust funds"))
        // Simulate reading other_fund.txt
        val otherAttention = Attention("other_fund.txt")
        val otherOptions = otherAttention.options()
        kotlin.test.assertTrue(otherOptions.contains("index funds"))
        kotlin.test.assertTrue(otherOptions.contains("etfs"))
    }

    @Test
    fun testInlineDoubleDispatchExtractAndAnalyze() = kotlinx.coroutines.runBlocking {
        // Set up real FiduciaryContext
        val metadata = org.apache.tika.metadata.Metadata()
        val parseContext = org.apache.tika.parser.ParseContext()
        val ioContext = borg.trikeshed.io.IOContext()
        val fidContext = fiduciary.attention.FiduciaryContext(ioContext j (metadata j parseContext))

        // Create a DocumentAttention for a PDF
        val doc = fidContext.document(0L, 50000L, "application/pdf")
        val tikaSource = fidContext.tika("/path/to/document.pdf")

        // Double dispatch: extract then analyze
        val text = doc.extract(tikaSource)
        val nlpSource = fidContext.nlp(text)
        val concepts = doc.analyze(nlpSource)

        // Assert on the results (using the mock implementations)
        kotlin.test.assertTrue(text.contains("Extracted PDF content"))
        kotlin.test.assertEquals(3, concepts.a)
        val conceptList = List(concepts.a) { concepts.b(it) }
        kotlin.test.assertTrue(conceptList.contains("person"))
        kotlin.test.assertTrue(conceptList.contains("organization"))
        kotlin.test.assertTrue(conceptList.contains("location"))
    }

    @Test
    fun testAttentionDerivedResultsTDD() {
        // TDD: Specify expected attention-derived results
        val context = object {
            val metadata = mutableMapOf<String, String>()
        }
        context.metadata["focus"] = "fiduciary trust"
        // Simulate attention-derived result logic
        class Attention(val context: Any) {
            fun derivedResults(): List<String> {
                // TDD: hardcoded expected results for now
                return listOf("trust analysis", "fund breakdown", "compliance check")
            }
        }
        val attention = Attention(context)
        val results = attention.derivedResults()
        kotlin.test.assertTrue(results.contains("trust analysis"))
        kotlin.test.assertTrue(results.contains("fund breakdown"))
        kotlin.test.assertTrue(results.contains("compliance check"))
        kotlin.test.assertEquals(3, results.size)
    }
} 