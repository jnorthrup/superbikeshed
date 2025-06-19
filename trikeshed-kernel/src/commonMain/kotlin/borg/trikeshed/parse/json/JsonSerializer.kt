package borg.trikeshed.parse.json

import borg.trikeshed.services.*

/**
 * JSON serialization utilities for domain objects.
 */
object JsonSerializer {
    fun serializeDeal(deal: DealProxy): String {
        return buildString {
            append("{")
            if (deal.id.isNotEmpty()) {
                append("\"_id\":\"${deal.id}\",")
            }
            append("\"type\":\"deal\",")
            append("\"product\":\"${deal.product}\",")
            append("\"vendor\":\"${deal.vendor}\",")
            append("\"price\":${deal.price},")
            append("\"quantity\":${deal.quantity}")
            append("}")
        }
    }

    fun serializeVendor(vendor: VendorProxy): String {
        return buildString {
            append("{")
            if (vendor.id.isNotEmpty()) {
                append("\"_id\":\"${vendor.id}\",")
            }
            append("\"type\":\"vendor\",")
            append("\"name\":\"${vendor.name}\"")
            append("}")
        }
    }
} 