package borg.trikeshed.lib

/**
 * IOMemento - Type descriptors for columnar I/O operations
 * 
 * Represents the type information for database columns and 
 * serialization/deserialization operations.
 */
sealed class IOMemento {
    object IoBoolean : IOMemento()
    object IoByte : IOMemento()
    object IoShort : IOMemento()
    object IoInt : IOMemento()
    object IoLong : IOMemento()
    object IoFloat : IOMemento()
    object IoDouble : IOMemento()
    object IoChar : IOMemento()
    object IoString : IOMemento()
    data class IoVarchar(val maxLength: Int = 255) : IOMemento()
    object IoLocalDate : IOMemento()
    object IoLocalDateTime : IOMemento()
    object IoInstant : IOMemento()
    object IoBigDecimal : IOMemento()
    object IoBigInteger : IOMemento()
    object IoUUID : IOMemento()
    object IoByteArray : IOMemento()
    object IoJson : IOMemento()
    object IoXml : IOMemento()
    
    // Custom types
    data class IoEnum(val enumClass: String) : IOMemento()
    data class IoArray(val elementType: IOMemento) : IOMemento()
    data class IoMap(val keyType: IOMemento, val valueType: IOMemento) : IOMemento()
    data class IoCustom(val className: String) : IOMemento()
}