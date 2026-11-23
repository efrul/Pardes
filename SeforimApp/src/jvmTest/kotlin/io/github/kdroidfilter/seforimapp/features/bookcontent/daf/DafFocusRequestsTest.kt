package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The comment a commentary's tab opens its tractate's daf at (request 83 of docs/DESKTOP_STAGE3_PLAN.md: "פתיחה של צורת
 * הדף מרשי תפתח את כל האפשרויות כמו גמרא"): taken once, by that tab's daf of that tractate.
 */
class DafFocusRequestsTest {
    @Test
    fun `a comment asked of a tab's daf is taken once, by the daf of its tractate`() {
        val requests = DafFocusRequests()
        requests.request("tab", tractateId = 103, commentId = 953_826)
        assertNull(requests.take("other", 103), "another tab's daf")
        assertEquals(953_826L, requests.take("tab", 103))
        assertNull(requests.take("tab", 103), "once")

        // Another tractate's daf shown first: the request is dropped
        requests.request("tab", 103, 953_827)
        assertNull(requests.take("tab", 124))
        assertNull(requests.take("tab", 103))
    }
}
