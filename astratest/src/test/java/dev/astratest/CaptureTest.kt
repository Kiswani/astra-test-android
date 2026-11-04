package dev.astratest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTest {
    @Test fun ringKeepsTheNewest() {
        val r = Ring<Int>(3); (1..5).forEach { r.push(it) }
        assertEquals(listOf(3, 4, 5), r.all())
    }
    @Test fun recordDropsQuery() {
        Capture.recordCall("POST", "https://api.example.com/v1/cart?token=secret#x", 500, 12)
        val last = Capture.network.all().last()
        assertEquals("https://api.example.com/v1/cart", last.url)
        assertEquals(500, last.status)
    }
    @Test fun parsesLogcatLines() {
        val l = Capture.parseLogcat("1730000000.123  1234  1235 E Checkout: promo returned percent")!!
        assertEquals("error", l.level)
        assertEquals(1730000000123L, l.at)
        assertEquals("Checkout: promo returned percent", l.message)
        assertNull(Capture.parseLogcat("--------- beginning of main"))
    }
}
