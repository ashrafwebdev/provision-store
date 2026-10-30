package com.provisionstore.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {

    @Test
    fun moneyShowsRupeesAndPaise() {
        assertEquals("₹12.50", 1250L.money())
    }

    @Test
    fun perUnitShowsPriceForUnit() {
        assertEquals("₹120.00 per kg", 12000L.perUnit("kg"))
    }

    @Test
    fun qtyDropsTrailingZeros() {
        assertEquals("2", 2.0.qty())
        assertEquals("1.25", 1.25.qty())
    }

    @Test
    fun parseMoneyReadsRupeesAsPaise() {
        assertEquals(1250L, parseMoney("12.5"))
        assertNull(parseMoney(""))
    }
}
