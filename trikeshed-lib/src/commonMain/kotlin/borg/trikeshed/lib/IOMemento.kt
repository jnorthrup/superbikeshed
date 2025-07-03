package borg.trikeshed.lib

interface IOMemento

{
    object IoBoolean : IOMemento
    object IoByte : IOMemento
    object IoShort : IOMemento
    object IoInt : IOMemento
    object IoLong : IOMemento
    object IoFloat : IOMemento
    object IoDouble : IOMemento
    object IoChar : IOMemento
    object IoString : IOMemento
    object IoVarchar : IOMemento
    object IoLocalDate : IOMemento
    object IoLocalDateTime : IOMemento
    object IoInstant : IOMemento
}