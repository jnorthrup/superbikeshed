# Scripting API

TODO: Implement TrikeShed-integrated scripting engine with Series<T> and Join<A,B> support.

Key requirements:
- Series<T> for data processing
- α transforms for data transformation
- ▶ materialization operator (use backticks: `▶`)
- Join<A,B> composition with j operator
- @JvmInline value class wrappers
- Ontological typealiases for domain modeling

Removed ScriptEngine.kt due to parsing conflicts with special operators.