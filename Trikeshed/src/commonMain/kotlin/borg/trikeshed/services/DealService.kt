package borg.trikeshed.services

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
class DealProxy(private val data: Join<String, Map<String, Any>>) {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DealProxy) return false

        if (id != other.id) return false
        if (product != other.product) return false
        if (vendor != other.vendor) return false
        if (price != other.price) return false
        if (quantity != other.quantity) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + product.hashCode()
        result = 31 * result + vendor.hashCode()
        result = 31 * result + price.hashCode()
        result = 31 * result + quantity.hashCode()
        return result
    }
}

/**
 * Value class representing a vendor.
 */
class VendorProxy(private val data: Join<String, Map<String, String>>) {
    val id: String get() = data.first
    val name: String get() = data.second["name"] ?: ""

    companion object {
        operator fun invoke(
            id: String = "",
            name: String = ""
        ): VendorProxy = VendorProxy(Join(id, mapOf("name" to name)))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VendorProxy) return false

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

/**
 * Value class representing a CouchDB transaction result.
 */
class CouchTxProxy(private val data: Join<String, Map<String, String>>) {
    val id: String get() = data.first
    val ok: Boolean get() = data.second["ok"] == "true"
    val rev: String? get() = data.second["rev"]
    val error: String? get() = data.second["error"]
    val reason: String? get() = data.second["reason"]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CouchTxProxy) return false

        if (id != other.id) return false
        if (ok != other.ok) return false
        if (rev != other.rev) return false
        if (error != other.error) return false
        if (reason != other.reason) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ok.hashCode()
        result = 31 * result + (rev?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (reason?.hashCode() ?: 0)
        return result
    }
}