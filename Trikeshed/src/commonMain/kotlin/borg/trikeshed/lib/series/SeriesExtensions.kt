package borg.trikeshed.lib

// Series typealias is defined in Series.kt - removing duplicate

fun <T> Series<Series<T>>.flatten(): Series<T> {
    var totalSize = 0
    for (i in 0 until size) {
        totalSize += this[i].size
    }
    return totalSize j { i ->
        var remaining = i
        var currentSeries = 0
        while (remaining >= this[currentSeries].size) {
            remaining -= this[currentSeries].size
            currentSeries++
        }
        this[currentSeries][remaining]
    }
}

fun <T, R> Series<T>.mapToSeries(transform: (T) -> R): Series<R> {
    return size j { i -> transform(this[i]) }
} 