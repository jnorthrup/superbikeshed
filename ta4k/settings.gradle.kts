rootProject.name = "ta4k"

include(":Trikeshed")
project(":Trikeshed").projectDir = file("../Trikeshed")

include(":Trikeshed:cursors")
project(":Trikeshed:cursors").projectDir = file("../Trikeshed/cursors")

include(":Trikeshed:vec")
project(":Trikeshed:vec").projectDir = file("../Trikeshed/vec")

include(":quantstats-core")
project(":quantstats-core").projectDir = file("./quantstats-core")
