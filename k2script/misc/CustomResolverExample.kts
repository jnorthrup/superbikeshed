package kscript.app

import kotlin.script.experimental.dependencies.CompoundDependenciesResolver
import kotlin.script.experimental.dependencies.FileSystemDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

suspend fun main() {
    val jcenterResolver = MavenDependenciesResolver().apply {
        addRepository(RepositoryCoordinates("https://jcenter.bintray.com") )
    }

    val resolver = CompoundDependenciesResolver(FileSystemDependenciesResolver(), MavenDependenciesResolver(), jcenterResolver)
//    resolver.addRepository()

    val example = "de.mpicbg.scicomp:kutils:0.7"
//    val example = "log4j:log4j:1.2+"

    val results = resolver.resolve(example)

    print(results)
}
