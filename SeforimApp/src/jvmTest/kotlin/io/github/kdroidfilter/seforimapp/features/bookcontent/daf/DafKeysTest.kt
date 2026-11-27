package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The daf's key: the daf button's click of the tab it is pressed in, while that button is shown. */
class DafKeysTest {
    @Test
    fun `the key is the click of the tab's button, and none without it`() {
        val keys = DafKeys()
        var clicks = 0
        val click: () -> Unit = { clicks++ }
        assertFalse(keys.click("a"), "no button shown")
        keys.set("a", click)
        assertTrue(keys.click("a"))
        assertFalse(keys.click("b"), "another tab's")
        assertFalse(keys.click(null), "no reader tab")
        assertEquals(1, clicks)
        // A button gone after its tab's next one was set leaves that one
        keys.clear("a") {}
        assertTrue(keys.click("a"))
        keys.clear("a", click)
        assertFalse(keys.click("a"))
        assertEquals(2, clicks)
    }
}
