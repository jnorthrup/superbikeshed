package org.example.trikeshedminimaljson

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

// Global Json configuration
val AppJson = Json {
    prettyPrint = false // Changed to false
    encodeDefaults = false
}

// Custom Serializer for DealProxy
object DealProxySerializer : KSerializer<DealProxy> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DealProxy") {
        element<String>("type", isOptional = true)
        element<String>("_id", isOptional = true)
        element<String>("product")
        element<String>("vendor")
        element<Double>("price")
        element<Int>("quantity")
    }

    override fun serialize(encoder: Encoder, value: DealProxy) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, "deal")
        if (value.id.isNotEmpty()) {
            composite.encodeStringElement(descriptor, 1, value.id)
        }
        composite.encodeStringElement(descriptor, 2, value.product)
        composite.encodeStringElement(descriptor, 3, value.vendor)
        composite.encodeDoubleElement(descriptor, 4, value.price)
        composite.encodeIntElement(descriptor, 5, value.quantity)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): DealProxy {
        val composite = decoder.beginStructure(descriptor)
        var id = ""
        var product = ""
        var vendor = ""
        var price = 0.0
        var quantity = 0
        var typeFound = false
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> {
                    val typeValue = composite.decodeStringElement(descriptor, index)
                    require(typeValue == "deal") { "Type must be 'deal'" }
                    typeFound = true
                }
                1 -> id = composite.decodeStringElement(descriptor, index)
                2 -> product = composite.decodeStringElement(descriptor, index)
                3 -> vendor = composite.decodeStringElement(descriptor, index)
                4 -> price = composite.decodeDoubleElement(descriptor, index)
                5 -> quantity = composite.decodeIntElement(descriptor, index)
                else -> throw SerializationException("Unknown index $index")
            }
        }
        composite.endStructure(descriptor)
        require(typeFound) { "Type field 'type' was not present or not 'deal'" }
        return DealProxy(id, product, vendor, price, quantity)
    }
}

// Custom Serializer for VendorProxy
object VendorProxySerializer : KSerializer<VendorProxy> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("VendorProxy") {
        element<String>("type", isOptional = true)
        element<String>("_id", isOptional = true)
        element<String>("name")
    }

    override fun serialize(encoder: Encoder, value: VendorProxy) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, "vendor")
        if (value.id.isNotEmpty()) {
            composite.encodeStringElement(descriptor, 1, value.id)
        }
        composite.encodeStringElement(descriptor, 2, value.name)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): VendorProxy {
        val composite = decoder.beginStructure(descriptor)
        var id = ""
        var name = ""
        var typeFound = false
        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> {
                    val typeValue = composite.decodeStringElement(descriptor, index)
                    require(typeValue == "vendor") { "Type must be 'vendor'" }
                    typeFound = true
                }
                1 -> id = composite.decodeStringElement(descriptor, index)
                2 -> name = composite.decodeStringElement(descriptor, index)
                else -> throw SerializationException("Unknown index $index")
            }
        }
        composite.endStructure(descriptor)
        require(typeFound) { "Type field 'type' was not present or not 'vendor'" }
        return VendorProxy(id, name)
    }
}
