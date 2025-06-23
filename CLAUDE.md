# SuperBikeShed Global Instructions

## RUNTIME MEMORY

- for loops in kotlin are the gold standard of performance intent and foreach is something else
- when running gradle "--console=plain --no-daemon "
- ordinary usecases involve doing conditional native repo determiniation in gradle and not all targets
- our gradle should always defer to superbikeshed/ gradle for versions info and not alter them.  our targets are common,conditionally-local-native,wasm,jvm
- most of the time you just copy trikeshed gradle for a new project
- while oraacle is tyrannizing us with native access put        24 +      jvmArgs("--enable-native-access=ALL-UNNAMED") in JAVA_OPTS exported
- preference: size j { ... } < size j ::get 

## Migration Memories

- **Shunned Classes Memory**:
  - Defer use of `List<T>`
  - Defer use of `Pair<A,B>`
  - Prefer `Series<T>`, `primitive array`, `Join<A,B>` instead

## Memory: Code Cleaning Liberties

- if it wasn't mentioned before we do not tolerate "cleaning" liberties at all.  we need all our code and we paid you for all our code and do not give rights of disposal.  you may move code to a musem area and we will find a model that can do your job for you later and delete you when we have time.  that is all

## Memory: Museum Preservation

- museums outside of compilation are the last ditch when you cannot fix something and OUR RULES PREVENT DELETING CODE AND RANDOM "CLEANING"

## Memory: Project Documentation and Markdown

- no new markdown can be written without reading all the child (1 deep, summaries accepted) and sibling markdown of a project.  so consolidate often
- when reading our project markdowns more than 25 lines at a time create a summary doc to assist in toplevel reads

## Running Phase: Series → Indexed Import Alias Migration

**Current Status**: Running phase for cosmetic migration to Indexed naming

- Add `import borg.trikeshed.lib.Series as Indexed` to files using Series
- Use `Indexed<T>` instead of `Series<T>` in new code and updated files  
- Lazy migration - one file at a time, no pressure
- All Series extension functions work automatically with Indexed alias
- Eventually IntelliJ inline when ready to make permanent
- This prevents system shock and dueling architect AIs during transition
 
## Safety Features

- **2-Factor Reach Analysis**: Check direct + transitive impact before changes
- **Conflict Detection**: Executor prevents overlapping modifications
- **Risk Assessment**: Low/medium/high risk levels for different operations
- **Executive Escalation**: Human approval required for high-risk changes
- **Rollback Capability**: All changes planned with undo procedures

- i migrated Series to Indexed.

## Acronyms and Clarifications

- CCEK means "CoroutineContextElementKey"

## Todo Tasks

- todo: port the columnar/ superannutated test over to ci integration test with spansh nightly galaxy snapshot parser ljson kzran demo 

## Fiduciary Memory

- fiduciary will use lawful and legal taxonomies with a general penchant for a cite name, a blurb, link, and a blackboard of strings and things

## Markets and Information Volume

- todo: markets attention in moneyfan.  llm token markets, storage object markets, everything this would use to grow with money in information volume and p2p enablement

## CI/CD Zero Error Release System

- **Zero Error Release**: Automated releases only when build/test errors = 0
- **Branch Structure**: dev → release → main
- **Auto-tagging**: Creates semantic version tags (vYYYY.MM.DD-commit) 
- **Release Gates**: All tests pass + zero compilation errors + zero test failures
- **Workflow**: Push to dev → CI validates → Auto-release if zero errors → Merge to release branch

## Memory: Professional Code Guidelines

- professional code only no demos or mockups - TODO() is acceptable but no hype no documentation, thats just a shim.