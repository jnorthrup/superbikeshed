#!/bin/bash

# Create target directories
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/bridge
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/datetime
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/io
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/serialization
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/num
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/csv
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/isam/meta
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/tilting/sorting
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/tilting/zran
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie
mkdir -p trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/cursor

# Move files from lib package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/DoubleDispatch.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/DoubleDispatch.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/EnumAccess.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/EnumAccess.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/HttpServer.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/HttpServer.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/JsonScanner.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/JsonScanner.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/JsonTypes.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/JsonTypes.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/KademliaNUID.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/KademliaNUID.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/Peano.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/Peano.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/PlatformCodec.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/PlatformCodec.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/Series2.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/Series2.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/Tensor.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/Tensor.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/Twins.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/Twins.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/WireProto.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/WireProto.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/bridge/MissingSymbols.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/bridge/MissingSymbols.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/bridge/UniversalBridge.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/bridge/UniversalBridge.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/datetime/DateTimeFormatter.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/datetime/DateTimeFormatter.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/io/HttpHeaders.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/io/HttpHeaders.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/io/IOConstants.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/io/IOConstants.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/io/NetworkOrder.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/io/NetworkOrder.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/serialization/MessagePackHazelcastSerializer.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/serialization/MessagePackHazelcastSerializer.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/series/ByteSeries.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series/ByteSeries.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/series/CharSeries.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series/CharSeries.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/series/CowSeriesHandle.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series/CowSeriesHandle.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/series/LongSeries.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series/LongSeries.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/series/MutableSeries.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series/MutableSeries.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/series/VersionedSeries.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/series/VersionedSeries.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/Combine.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/Combine.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/CommonSelector.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/CommonSelector.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/CZero.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/CZero.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/debug.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/debug.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/descriptiveSetNotation.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/descriptiveSetNotation.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/Either.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/Either.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/FibonacciReporter.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/FibonacciReporter.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/HumanReadableNumbers.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/HumanReadableNumbers.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/octals.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/octals.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/util/Predicate.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/lib/util/Predicate.kt

# Move files from num package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/num/BigDecimal.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/num/BigDecimal.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/num/RoundingMode.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/num/RoundingMode.kt

# Move files from parse package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/CSVUtil.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/CSVUtil.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/DelimitRange.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/DelimitRange.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/DocumentBitmap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/DocumentBitmap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/FibonacciSampler.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/FibonacciSampler.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/TypeEvidence.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/TypeEvidence.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/csv/CsvBitmap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/csv/CsvBitmap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/csv/CsvIndex.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/csv/CsvIndex.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/Json.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/Json.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonBitmap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonBitmap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonBitmapProcessor.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonBitmapProcessor.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonBitmapSimd.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonBitmapSimd.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonIndex.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonIndex.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonSerializer.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonSerializer.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonTensorFactory.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/parse/json/JsonTensorFactory.kt

# Move files from isam package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/isam/RecordMeta.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/isam/RecordMeta.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/isam/meta/PlatformCodec.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/isam/meta/PlatformCodec.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/isam/meta/SerializationFormat.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/isam/meta/SerializationFormat.kt

# Move files from tilting package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/tilting/sorting/SortChooser.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/tilting/sorting/SortChooser.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/tilting/zran/Point.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/tilting/zran/Point.kt

# Move files from common package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/_a.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/_a.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/_l.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/_l.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/_m.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/_m.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/_s.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/_s.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/_seq.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/_seq.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/ArraySet.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/ArraySet.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/HashSeriesSet.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/HashSeriesSet.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/HashSet.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/HashSet.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/Heap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/Heap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/NavigableSet.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/NavigableSet.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/s_.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/s_.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/SortedSet.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/SortedSet.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/Stack.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/Stack.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/StrictFibonacciHeap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/StrictFibonacciHeap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/TreeSet.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/TreeSet.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/JoinEntry.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/JoinEntry.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/NavigableMap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/NavigableMap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/SortedMap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/SortedMap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie/ArrayMap.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie/ArrayMap.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie/RadixTree.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie/RadixTree.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie/Trie.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/common/collections/associative/trie/Trie.kt

# Move files from cursor package
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/cursor/ColumnMeta.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/cursor/ColumnMeta.kt
mv Trikeshed/src/commonMain/kotlin/borg/trikeshed/cursor/TypeMemento.kt trikeshed-kernel/src/commonMain/kotlin/borg/trikeshed/cursor/TypeMemento.kt