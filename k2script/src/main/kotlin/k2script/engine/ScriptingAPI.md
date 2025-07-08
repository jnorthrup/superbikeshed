# Scripting API

TODO: Implement TrikeShed-integrated scripting engine with inline Metaseries value types.

Key requirements:
- Inline Metaseries Value Types for data transformation, with an automatic 'j factory' within the context that performs double dispatch (left/right shortest register, sometimes seen in old Twin constructors) to optimize to inline classes. Dispatches default to the old constructor, but primitive-to-primitive operations will describe their bits for maximum efficiency.
- play materialization operator
- @JvmInline value class wrappers
- Ontological typealiases for domain modeling

Removed ScriptEngine.kt due to parsing conflicts with special operators.