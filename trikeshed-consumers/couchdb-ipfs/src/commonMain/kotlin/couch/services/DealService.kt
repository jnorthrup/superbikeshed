package borg.trikeshed.consumers.couchdbipfs.services

import borg.trikeshed.lib.*

/**
 * Service interface for managing deals and vendors.
 */
interface DealService {
    suspend fun findDeal(id: String): DealProxy?
    suspend fun findDealsByProduct(query: String): Series<DealProxy>
    suspend fun persistDeal(deal: DealProxy): CouchTxProxy
    suspend fun getVendors(): Series<VendorProxy>
}

/**
 * Value class representing a deal.
 */
@JvmInline
value class DealProxy(private val data: Join<String, Map<String, Any>>) {
    val id: String get() = data.first
    val product: String get() = data.second["product"] as? String ?: ""
    val vendor: String get() = data.second["vendor"] as? String ?: ""
    val price: Double get() = data.second["price"] as? Double ?: 0.0
    val quantity: Int get() = data.second["quantity"] as? Int ?: 0

    companion object {
        operator fun invoke(
            id: String = "",
            product: String = "",
            vendor: String = "",
            price: Double = 0.0,
            quantity: Int = 0
        ): DealProxy = DealProxy(Join(id, mapOf(
            "product" to product,
            "vendor" to vendor,
            "price" to price,
            "quantity" to quantity
        )))
    }
}

/**
 * Value class representing a vendor.
 */
@JvmInline
value class VendorProxy(private val data: Join<String, Map<String, String>>) {
    val id: String get() = data.first
    val name: String get() = data.second["name"] ?: ""

    companion object {
        operator fun invoke(
            id: String = "",
            name: String = ""
        ): VendorProxy = VendorProxy(Join(id, mapOf("name" to name)))
    }
}

/**
 * Value class representing a CouchDB transaction result.
 */
@JvmInline
value class CouchTxProxy(private val data: Join<String, Map<String, String>>) {
    val id: String get() = data.first
    val ok: Boolean get() = data.second["ok"] == "true"
    val rev: String? get() = data.second["rev"]
    val error: String? get() = data.second["error"]
    val reason: String? get() = data.second["reason"]
} 