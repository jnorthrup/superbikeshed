 
## RUNTIME MEMORY

- remove all kotlin serialization references as soon as you see one
- for loops in kotlin are the gold standard of performance intent and foreach is something else
- when running gradle "--console=plain --no-daemon "
- ordinary usecases involve doing conditional native repo determiniation in gradle and not all targets
- our gradle should always defer to superbikeshed/ gradle for versions info and not alter them.  our targets are common,conditionally-local-native,wasm,jvm 
- most of the time you just copy trikeshed gradle for a new project

## Migration Memories

- **Shunned Classes Memory**: 
  - Defer use of `List<T>`
  - Defer use of `Pair<A,B>`
  - Prefer `Series<T>`, `primitive array`, `Join<A,B>` instead

## Memory: CCEK Meaning

- CCEK stands for CoroutineContextElement.Key

## Memory: Code Cleaning Liberties

- if it wasn't mentioned before we do not tolerate "cleaning" liberties at all.  we need all our code and we paid you for all our code and do not give rights of disposal.  you may move code to a musem area and we will find a model that can do your job for you later and delete you when we have time.  that is all

## Memory: Typealias Taxonomical Ontology Design

- Before adding new design to the code, design the Typealias taxonomical ontology according to the specification language
- Leverage `Join`, and `MetaSeries` derived types like `Series` and `LongSeries`

## Memory: Project Setup

- before creating new code open trikeshed CoreTypes.kt first so its in the context

## Memory: Museum Preservation

- museums outside of compilation are the last ditch when you cannot fix something and OUR RULES PREVENT DELETING CODE AND RANDOM "CLEANING"