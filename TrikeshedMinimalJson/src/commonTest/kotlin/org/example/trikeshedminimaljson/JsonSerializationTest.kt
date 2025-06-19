package org.example.trikeshedminimaljson

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException // Added import
import kotlin.test.*

class JsonSerializationTest {

    // DealProxy Tests
    @Test
    fun testSerializeDealProxyWithId() {
        val deal = DealProxy(id = "deal123", product = "Test Product", vendor = "Test Vendor", price = 99.99, quantity = 10)
        val jsonString = AppJson.encodeToString(deal)

        assertTrue(jsonString.contains("\"type\":\"deal\""), "JSON should contain type:deal")
        assertTrue(jsonString.contains("\"_id\":\"deal123\""), "JSON should contain _id")
        assertTrue(jsonString.contains("\"product\":\"Test Product\""), "JSON should contain product")
        assertTrue(jsonString.contains("\"vendor\":\"Test Vendor\""), "JSON should contain vendor")
        assertTrue(jsonString.contains("\"price\":99.99"), "JSON should contain price")
        assertTrue(jsonString.contains("\"quantity\":10"), "JSON should contain quantity")
    }

    @Test
    fun testSerializeDealProxyWithoutId() {
        val deal = DealProxy(product = "Another Product", vendor = "Another Vendor", price = 10.0, quantity = 1)
        val jsonString = AppJson.encodeToString(deal)

        assertTrue(jsonString.contains("\"type\":\"deal\""), "JSON should contain type:deal")
        assertFalse(jsonString.contains("\"_id\""), "JSON should NOT contain _id when id is empty")
        assertTrue(jsonString.contains("\"product\":\"Another Product\""), "JSON should contain product")
    }

    @Test
    fun testDeserializeDealProxyWithId() {
        val jsonString = """
            {
                "type": "deal",
                "_id": "deal456",
                "product": "Product From JSON",
                "vendor": "Vendor JSON",
                "price": 12.34,
                "quantity": 5
            }
        """.trimIndent()
        val deal = AppJson.decodeFromString<DealProxy>(jsonString)

        assertEquals("deal456", deal.id)
        assertEquals("Product From JSON", deal.product)
        assertEquals("Vendor JSON", deal.vendor)
        assertEquals(12.34, deal.price)
        assertEquals(5, deal.quantity)
    }

    @Test
    fun testDeserializeDealProxyWithoutId() {
        val jsonString = """
            {
                "type": "deal",
                "product": "Product NoId",
                "vendor": "Vendor NoId",
                "price": 1.0,
                "quantity": 1
            }
        """.trimIndent()
        val deal = AppJson.decodeFromString<DealProxy>(jsonString)

        assertEquals("", deal.id) // Expecting default empty string for id
        assertEquals("Product NoId", deal.product)
    }

    @Test
    fun testDeserializeDealProxyWrongType() {
        val jsonString = """
            {
                "type": "notadeal",
                "_id": "deal456",
                "product": "Product From JSON",
                "vendor": "Vendor JSON",
                "price": 12.34,
                "quantity": 5
            }
        """.trimIndent()
        assertFailsWith<IllegalArgumentException>("Should fail for wrong type") {
            AppJson.decodeFromString<DealProxy>(jsonString)
        }
    }

    // VendorProxy Tests
    @Test
    fun testSerializeVendorProxyWithId() {
        val vendor = VendorProxy(id = "vendor123", name = "Test Vendor Co")
        val jsonString = AppJson.encodeToString(vendor)

        assertTrue(jsonString.contains("\"type\":\"vendor\""), "JSON should contain type:vendor")
        assertTrue(jsonString.contains("\"_id\":\"vendor123\""), "JSON should contain _id")
        assertTrue(jsonString.contains("\"name\":\"Test Vendor Co\""), "JSON should contain name")
    }

    @Test
    fun testSerializeVendorProxyWithoutId() {
        val vendor = VendorProxy(name = "Another Vendor Co")
        val jsonString = AppJson.encodeToString(vendor)

        assertTrue(jsonString.contains("\"type\":\"vendor\""), "JSON should contain type:vendor")
        assertFalse(jsonString.contains("\"_id\""), "JSON should NOT contain _id when id is empty")
        assertTrue(jsonString.contains("\"name\":\"Another Vendor Co\""), "JSON should contain name")
    }

    @Test
    fun testDeserializeVendorProxyWithId() {
        val jsonString = """
            {
                "type": "vendor",
                "_id": "vendor456",
                "name": "Vendor From JSON"
            }
        """.trimIndent()
        val vendor = AppJson.decodeFromString<VendorProxy>(jsonString)

        assertEquals("vendor456", vendor.id)
        assertEquals("Vendor From JSON", vendor.name)
    }

    @Test
    fun testDeserializeVendorProxyWrongType() {
        val jsonString = """
            {
                "type": "notavendor",
                "_id": "vendor456",
                "name": "Vendor From JSON"
            }
        """.trimIndent()
         assertFailsWith<IllegalArgumentException>("Should fail for wrong type") {
            AppJson.decodeFromString<VendorProxy>(jsonString)
        }
    }

    // CouchTxProxy Tests
    @Test
    fun testSerializeCouchTxProxyAllFields() {
        val tx = CouchTxProxy(id = "tx123", ok = true, rev = "rev1", error = "someError", reason = "someReason")
        val jsonString = AppJson.encodeToString(tx)

        assertTrue(jsonString.contains("\"id\":\"tx123\""))
        assertTrue(jsonString.contains("\"ok\":true"))
        assertTrue(jsonString.contains("\"rev\":\"rev1\""))
        assertTrue(jsonString.contains("\"error\":\"someError\""))
        assertTrue(jsonString.contains("\"reason\":\"someReason\""))
    }

    @Test
    fun testSerializeCouchTxProxyOptionalFieldsNull() {
        val tx = CouchTxProxy(id = "tx456", ok = false, rev = null, error = null, reason = null)
        val jsonString = AppJson.encodeToString(tx)

        assertTrue(jsonString.contains("\"id\":\"tx456\""))
        assertTrue(jsonString.contains("\"ok\":false"))
        assertFalse(jsonString.contains("\"rev\""), "JSON should not contain rev if null")
        assertFalse(jsonString.contains("\"error\""), "JSON should not contain error if null")
        assertFalse(jsonString.contains("\"reason\""), "JSON should not contain reason if null")
    }

    @Test
    fun testDeserializeCouchTxProxyAllFields() {
        val jsonString = """
            {
                "id": "tx789",
                "ok": true,
                "rev": "rev2",
                "error": "dbError",
                "reason": "conflict"
            }
        """.trimIndent()
        val tx = AppJson.decodeFromString<CouchTxProxy>(jsonString)

        assertEquals("tx789", tx.id)
        assertEquals(true, tx.ok)
        assertEquals("rev2", tx.rev)
        assertEquals("dbError", tx.error)
        assertEquals("conflict", tx.reason)
    }

    @Test
    fun testDeserializeCouchTxProxyMissingOptionalFields() {
        val jsonString = """
            {
                "id": "tx101",
                "ok": false
            }
        """.trimIndent()
        val tx = AppJson.decodeFromString<CouchTxProxy>(jsonString)

        assertEquals("tx101", tx.id)
        assertEquals(false, tx.ok)
        assertNull(tx.rev)
        assertNull(tx.error)
        assertNull(tx.reason)
    }

    @Test
    fun testDeserializeDealProxyFieldOrder() {
        val jsonString = """
            {
                "product": "Shuffled Product",
                "type": "deal",
                "quantity": 3,
                "price": 5.55,
                "vendor": "Shuffled Vendor",
                "_id": "dealShuffled"
            }
        """.trimIndent()
        val deal = AppJson.decodeFromString<DealProxy>(jsonString)

        assertEquals("dealShuffled", deal.id)
        assertEquals("Shuffled Product", deal.product)
        assertEquals("Shuffled Vendor", deal.vendor)
        assertEquals(5.55, deal.price)
        assertEquals(3, deal.quantity)
    }

    @Test
    fun testDeserializeDealProxyMissingType() {
        val jsonString = """
            {
                "_id": "dealNoType",
                "product": "Product Missing Type",
                "vendor": "Vendor Missing Type",
                "price": 1.23,
                "quantity": 1
            }
        """.trimIndent()
        val exception = assertFailsWith<IllegalArgumentException>("Should fail when type field is missing") {
            AppJson.decodeFromString<DealProxy>(jsonString)
        }
        // Depending on the KSerializer's internal checks, the message might vary.
        // For the current custom serializer, it's "Type field 'type' was not present or not 'deal'"
        assertTrue(exception.message?.contains("Type field 'type' was not present") == true)
    }

    @Test
    fun testDeserializeVendorProxyFieldOrder() {
        val jsonString = """
            {
                "name": "Shuffled Vendor Name",
                "type": "vendor",
                "_id": "vendorShuffled"
            }
        """.trimIndent()
        val vendor = AppJson.decodeFromString<VendorProxy>(jsonString)

        assertEquals("vendorShuffled", vendor.id)
        assertEquals("Shuffled Vendor Name", vendor.name)
    }

    @Test
    fun testDeserializeVendorProxyMissingType() {
        val jsonString = """
            {
                "_id": "vendorNoType",
                "name": "Vendor Name Missing Type"
            }
        """.trimIndent()
        val exception = assertFailsWith<IllegalArgumentException>("Should fail when type field is missing") {
            AppJson.decodeFromString<VendorProxy>(jsonString)
        }
        assertTrue(exception.message?.contains("Type field 'type' was not present") == true)
    }

    @Test
    fun testDeserializeCouchTxProxyWithUnknownFields() {
        val jsonString = """
            {
                "id": "txUnknown",
                "ok": true,
                "rev": "rev789",
                "unknownField": "someValue",
                "anotherUnknown": 123
            }
        """.trimIndent()
        // Expecting SerializationException because ignoreUnknownKeys is false by default
        assertFailsWith<SerializationException>("Should fail due to unknown fields") {
            AppJson.decodeFromString<CouchTxProxy>(jsonString)
        }
    }
}
