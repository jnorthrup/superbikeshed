package borg.trikeshed

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TrikeShedCommand(
    val name: String,                  // Command name/alias
    val description: String = "",      // Help/description
    val group: String = "default",     // Optional grouping/category
    val order: Int = 0,                // Optional order/priority
    val options: Array<Option> = []    // CLI options/arguments
) {
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Option(
        val name: String,              // Option/argument name
        val description: String = "",  // Help/description
        val required: Boolean = false, // Is this option required?
        val defaultValue: String = "", // Default value if not provided
        val type: KClass<*> = String::class // Type of the option (for validation)
    )
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TrikeShedParms(
    val parmsName: String,           // Name/alias for the parameter set
    val description: String = ""     // Help/description
) 