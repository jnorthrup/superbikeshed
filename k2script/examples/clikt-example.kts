//@file:DependsOn("com.github.ajalt:clikt:2.7.1")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.0.1")


import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int

class Hello : CliktCommand() {
    val count: Int by option(help="Number of greetings").int().default(1)
    val name: String by option(help="The person to greet").prompt("Your name")

    override fun run() {
        repeat(count) {
            echo("Hello $name!")
        }
    }
}
//@file:KotlinOpts("-J-Xmx5g")
//@file:KotlinOpts("-J-server")
//@file:CompilerOpts("-jvm-target 1.8")

Hello().main(args)
//Startup.main(args)