@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.daf

import io.github.kdroidfilter.seforimapp.features.bookcontent.state.BookContentStateManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A tab shown as its Vilna page is shown so again when the session is restored. */
class DafViewStateTest {
    @Test
    fun theDafViewIsKeptWithTheTab() {
        val store = TabPersistedStateStore()
        val manager = BookContentStateManager("tab-daf", store)
        assertFalse(manager.state.value.content.dafView)
        manager.updateContent { copy(dafView = true) }
        manager.saveAllStates()
        val restored = BookContentStateManager("tab-daf", store)
        assertTrue(restored.state.value.content.dafView)

        // Through the session file's ProtoBuf, whose fields are numbered by their order
        val saved = assertNotNull(store.get("tab-daf"))
        val bytes = ProtoBuf.encodeToByteArray(TabPersistedState.serializer(), saved)
        val read = ProtoBuf.decodeFromByteArray(TabPersistedState.serializer(), bytes)
        assertTrue(read.bookContent.dafView)
        assertFalse(read.bookContent.shnayimMikra)
    }
}
