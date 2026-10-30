package com.provisionstore.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitsTest {

    @Test
    fun kgEntryIsGrams() {
        assertEquals(0.25, parseEntry("250", "kg")!!, 1e-9)
        assertEquals("250", 0.25.toEntry("kg"))
    }

    @Test
    fun smallKgStockShowsGrams() {
        assertEquals("750 g", 0.75.stockText("kg"))
        assertEquals("1.5 kg", 1.5.stockText("kg"))
    }

    @Test
    fun litreUsesMillilitres() {
        assertEquals(0.5, parseEntry("500", "litre")!!, 1e-9)
        assertEquals("500 ml", 0.5.stockText("litre"))
    }

    @Test
    fun countedUnitsAreUnchanged() {
        assertEquals(3.0, parseEntry("3", "pc")!!, 1e-9)
        assertEquals("3 pc", 3.0.stockText("pc"))
        assertTrue(quickAmounts("pc").isEmpty())
    }

    @Test
    fun invalidEntryIsNull() {
        assertNull(parseEntry("abc", "kg"))
    }
}
