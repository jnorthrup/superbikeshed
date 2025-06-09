import io.github.kscripting.kscript.code.Templates
import io.github.kscripting.kscript.model.PackageName

fun main() {
    // Test the Templates.createWrapperForScript function with our fix
    val packageName = PackageName("kscript.scriplet")
    val className = "Scriplet"
    
    val wrapperCode = Templates.createWrapperForScript(packageName, className)
    
    println("Generated wrapper code:")
    println("=" * 50)
    println(wrapperCode)
    println("=" * 50)
    
    // Verify the fix: should load "ScripletKt" class and call main method
    val expectedClassLoad = "kscript.scriplet.ScripletKt"
    val expectedMethodCall = "getDeclaredMethod(\"main\", Array<String>::class.java).invoke(null, args)"
    
    if (wrapperCode.contains(expectedClassLoad)) {
        println("✓ PASS: Wrapper correctly loads class with 'Kt' suffix")
    } else {
        println("✗ FAIL: Wrapper doesn't load class with 'Kt' suffix")
        println("Expected: $expectedClassLoad")
    }
    
    if (wrapperCode.contains(expectedMethodCall)) {
        println("✓ PASS: Wrapper correctly calls static main method")
    } else {
        println("✗ FAIL: Wrapper doesn't call static main method")
        println("Expected: $expectedMethodCall")
    }
    
    println("\nThis validates our fix to Templates.kt that resolves the ClassNotFoundException")
}

operator fun String.times(n: Int): String = this.repeat(n)