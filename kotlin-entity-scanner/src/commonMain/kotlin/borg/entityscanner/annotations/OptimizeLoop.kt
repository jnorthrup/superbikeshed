package borg.entityscanner.annotations

/**
 * Instructs the KSP processor to generate an optimized forEach loop
 * for this type, if its size is known at compile time and small enough.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OptimizeLoop 