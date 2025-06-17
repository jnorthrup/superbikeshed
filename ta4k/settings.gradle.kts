rootProject.name = "ta4k"

include(":Trikeshed")
project(":Trikeshed").projectDir = file("../Trikeshed")

include(":quantstats-core")
project(":quantstats-core").projectDir = file("./quantstats-core")
