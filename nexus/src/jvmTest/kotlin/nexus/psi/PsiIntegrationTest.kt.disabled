package nexus.psi

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

// Import the PSI classes from the intellij-plugin module
import com.v2superbikeshed.nexus.psi.*

/**
 * PSI Integration Test Fixtures
 * 
 * Tests the PSI bridge functionality with mock data
 */
class PsiIntegrationTest {
    
    private lateinit var tempDir: Path
    private lateinit var testKotlinFile: Path
    
    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("nexus-psi-test")
        testKotlinFile = tempDir.resolve("TestFile.kt")
        
        // Create a test Kotlin file
        val kotlinCode = """
            package test.package
            
            import kotlin.String
            
            class TestClass {
                private val testProperty: String = "test"
                
                fun testFunction(param: String): String {
                    return param.uppercase()
                }
            }
            
            object TestObject {
                const val CONSTANT = "constant"
            }
        """.trimIndent()
        
        Files.write(testKotlinFile, kotlinCode.toByteArray())
    }
    
    @After
    fun tearDown() {
        Files.deleteIfExists(testKotlinFile)
        Files.deleteIfExists(tempDir)
    }
    
    @Test
    fun `Series operations work with PSI elements`() {
        // Test that Series operations work correctly
        val elements = listOf("element1", "element2", "element3")
        val series = Series.from(elements)
        
        assertEquals(3, series.toList().size)
        assertTrue(series.toList().contains("element1"))
    }
    
    @Test
    fun `Join operations work correctly`() {
        // Test Join operations
        val join = object : Join<String, Int> {
            override fun <C> map(transform: (String, Int) -> C): Series<C> {
                return Series.from(listOf(transform("test", 42)))
            }
            
            override fun filter(predicate: (String, Int) -> Boolean): Join<String, Int> {
                return this
            }
        }
        
        val result = join.map { str, num -> "$str-$num" }
        assertEquals(1, result.toList().size)
        assertEquals("test-42", result.toList().first())
    }
    
    @Test
    fun `PsiElementRef wrapper works correctly`() {
        // Test the PsiElementRef wrapper
        val mockElement = MockPsiElement("testElement")
        val ref = PsiElementRef(mockElement)
        
        assertEquals("testElement", ref.name)
        assertEquals("testElement", ref.text)
    }
    
    @Test
    fun `KtElementRef wrapper works correctly`() {
        // Test the KtElementRef wrapper
        val mockKtElement = MockKtElement("testKtElement")
        val ref = KtElementRef(mockKtElement)
        
        assertEquals("testKtElement", ref.name)
        assertEquals("testKtElement", ref.text)
    }
    
    @Test
    fun `KotlinAnalysisResult structure is correct`() {
        val declarations = listOf(KtElementRef(MockKtElement("decl1")))
        val types = listOf(KtTypeInfo(KtElementRef(MockKtElement("type1")), "String", false))
        val references = listOf(KtReferenceInfo(KtElementRef(MockKtElement("ref1")), null, "reference"))
        
        val result = KotlinAnalysisResult(
            declarations = declarations,
            types = types,
            references = references,
            structure = "Test Structure"
        )
        
        assertEquals(1, result.declarations.size)
        assertEquals(1, result.types.size)
        assertEquals(1, result.references.size)
        assertEquals("Test Structure", result.structure)
    }
    
    @Test
    fun `DataFlowInfo structure is correct`() {
        val inputElements = listOf(PsiElementRef(MockPsiElement("input")))
        val outputElements = listOf(PsiElementRef(MockPsiElement("output")))
        val dependencies = listOf(PsiElementRef(MockPsiElement("dep")))
        
        val dataFlow = DataFlowInfo(
            inputElements = inputElements,
            outputElements = outputElements,
            dependencies = dependencies
        )
        
        assertEquals(1, dataFlow.inputElements.size)
        assertEquals(1, dataFlow.outputElements.size)
        assertEquals(1, dataFlow.dependencies.size)
    }
}

// Mock classes for testing
class MockPsiElement(override val name: String) : com.intellij.psi.PsiElement {
    override fun getProject() = throw UnsupportedOperationException()
    override fun getManager() = throw UnsupportedOperationException()
    override fun getText() = name
    override fun getTextRange() = throw UnsupportedOperationException()
    override fun getStartOffsetInParent() = throw UnsupportedOperationException()
    override fun getTextLength() = throw UnsupportedOperationException()
    override fun findElementAt(offset: Int) = throw UnsupportedOperationException()
    override fun findReferenceAt(offset: Int) = throw UnsupportedOperationException()
    override fun getTextOffset() = throw UnsupportedOperationException()
    override fun getContainingFile() = throw UnsupportedOperationException()
    override fun getOriginalElement() = throw UnsupportedOperationException()
    override fun getChildren() = emptyArray()
    override fun getParent() = throw UnsupportedOperationException()
    override fun getFirstChild() = throw UnsupportedOperationException()
    override fun getLastChild() = throw UnsupportedOperationException()
    override fun getNextSibling() = throw UnsupportedOperationException()
    override fun getPrevSibling() = throw UnsupportedOperationException()
    override fun getNode() = throw UnsupportedOperationException()
    override fun accept(visitor: com.intellij.psi.PsiElementVisitor) = throw UnsupportedOperationException()
    override fun acceptChildren(visitor: com.intellij.psi.PsiElementVisitor) = throw UnsupportedOperationException()
    override fun copy() = throw UnsupportedOperationException()
    override fun add(element: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun addBefore(element: com.intellij.psi.PsiElement, anchor: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun addAfter(element: com.intellij.psi.PsiElement, anchor: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun checkAdd(element: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun addRange(first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun addRangeBefore(element: com.intellij.psi.PsiElement, first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun addRangeAfter(element: com.intellij.psi.PsiElement, first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun delete() = throw UnsupportedOperationException()
    override fun checkDelete() = throw UnsupportedOperationException()
    override fun deleteChildRange(first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun replace(newElement: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun isValid() = true
    override fun isWritable() = true
    override fun getReference() = throw UnsupportedOperationException()
    override fun getReferences() = emptyArray()
    override fun <T : com.intellij.psi.PsiElement?> putUserData(key: com.intellij.openapi.util.Key<T>, value: T?) = throw UnsupportedOperationException()
    override fun <T : com.intellij.psi.PsiElement?> getUserData(key: com.intellij.openapi.util.Key<T>) = throw UnsupportedOperationException()
    override fun <T : com.intellij.psi.PsiElement?> putCopyableUserData(key: com.intellij.openapi.util.Key<T>, value: T?) = throw UnsupportedOperationException()
    override fun <T : com.intellij.psi.PsiElement?> getCopyableUserData(key: com.intellij.openapi.util.Key<T>) = throw UnsupportedOperationException()
    override fun getLanguage() = throw UnsupportedOperationException()
    override fun getResolveScope() = throw UnsupportedOperationException()
    override fun getUseScope() = throw UnsupportedOperationException()
    override fun processDeclarations(processor: com.intellij.psi.PsiScopeProcessor, substitutor: com.intellij.psi.PsiSubstitutor?, lastParent: com.intellij.psi.PsiElement?, place: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun getNavigationElement() = throw UnsupportedOperationException()
    override fun isEquivalentTo(another: com.intellij.psi.PsiElement?) = false
    override fun getTextRange() = throw UnsupportedOperationException()
}

class MockKtElement(override val name: String?) : org.jetbrains.kotlin.psi.KtElement {
    override fun getProject() = throw UnsupportedOperationException()
    override fun getManager() = throw UnsupportedOperationException()
    override fun getText() = name ?: "mock"
    override fun getTextRange() = throw UnsupportedOperationException()
    override fun getStartOffsetInParent() = throw UnsupportedOperationException()
    override fun getTextLength() = throw UnsupportedOperationException()
    override fun findElementAt(offset: Int) = throw UnsupportedOperationException()
    override fun findReferenceAt(offset: Int) = throw UnsupportedOperationException()
    override fun getTextOffset() = throw UnsupportedOperationException()
    override fun getContainingFile() = throw UnsupportedOperationException()
    override fun getOriginalElement() = throw UnsupportedOperationException()
    override fun getChildren() = emptyArray()
    override fun getParent() = throw UnsupportedOperationException()
    override fun getFirstChild() = throw UnsupportedOperationException()
    override fun getLastChild() = throw UnsupportedOperationException()
    override fun getNextSibling() = throw UnsupportedOperationException()
    override fun getPrevSibling() = throw UnsupportedOperationException()
    override fun getNode() = throw UnsupportedOperationException()
    override fun accept(visitor: com.intellij.psi.PsiElementVisitor) = throw UnsupportedOperationException()
    override fun acceptChildren(visitor: com.intellij.psi.PsiElementVisitor) = throw UnsupportedOperationException()
    override fun copy() = throw UnsupportedOperationException()
    override fun add(element: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun addBefore(element: com.intellij.psi.PsiElement, anchor: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun addAfter(element: com.intellij.psi.PsiElement, anchor: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun checkAdd(element: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun addRange(first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun addRangeBefore(element: com.intellij.psi.PsiElement, first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun addRangeAfter(element: com.intellij.psi.PsiElement, first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun delete() = throw UnsupportedOperationException()
    override fun checkDelete() = throw UnsupportedOperationException()
    override fun deleteChildRange(first: com.intellij.psi.PsiElement?, last: com.intellij.psi.PsiElement?) = throw UnsupportedOperationException()
    override fun replace(newElement: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun isValid() = true
    override fun isWritable() = true
    override fun getReference() = throw UnsupportedOperationException()
    override fun getReferences() = emptyArray()
    override fun <T : com.intellij.psi.PsiElement?> putUserData(key: com.intellij.openapi.util.Key<T>, value: T?) = throw UnsupportedOperationException()
    override fun <T : com.intellij.psi.PsiElement?> getUserData(key: com.intellij.openapi.util.Key<T>) = throw UnsupportedOperationException()
    override fun <T : com.intellij.psi.PsiElement?> putCopyableUserData(key: com.intellij.openapi.util.Key<T>, value: T?) = throw UnsupportedOperationException()
    override fun <T : com.intellij.psi.PsiElement?> getCopyableUserData(key: com.intellij.openapi.util.Key<T>) = throw UnsupportedOperationException()
    override fun getLanguage() = throw UnsupportedOperationException()
    override fun getResolveScope() = throw UnsupportedOperationException()
    override fun getUseScope() = throw UnsupportedOperationException()
    override fun processDeclarations(processor: com.intellij.psi.PsiScopeProcessor, substitutor: com.intellij.psi.PsiSubstitutor?, lastParent: com.intellij.psi.PsiElement?, place: com.intellij.psi.PsiElement) = throw UnsupportedOperationException()
    override fun getNavigationElement() = throw UnsupportedOperationException()
    override fun isEquivalentTo(another: com.intellij.psi.PsiElement?) = false
    override fun getTextRange() = throw UnsupportedOperationException()
    override fun accept(visitor: org.jetbrains.kotlin.psi.KtVisitor<*, *>) = throw UnsupportedOperationException()
    override fun acceptChildren(visitor: org.jetbrains.kotlin.psi.KtVisitor<*, *>) = throw UnsupportedOperationException()
} 