package borg.trikeshed.isam.meta

/**
 * Input/Output memento for storing metadata about data structures.
 * Used in ISAM data files and cursor operations.
 */
class IOMemento {
    var name: String? = null
    var type: String? = null  
    var width: Int? = null
    var nullable: Boolean? = null
    var encoding: String? = null
    var format: String? = null
    
    companion object {
        fun create(name: String? = null, type: String? = null, width: Int? = null, nullable: Boolean? = null): IOMemento {
            return IOMemento().apply {
                this.name = name
                this.type = type
                this.width = width
                this.nullable = nullable
            }
        }
    }
}