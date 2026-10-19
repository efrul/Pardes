package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.content.Intent

class TextActivity : BaseActivity() {
    companion object {
        const val EXTRA_QUERY = "query"

        fun start(context: Context, bookId: Long, lineIndex: Int?) {
            context.startActivity(Intent(context, TextActivity::class.java).putExtra("book", bookId).putExtra("line", lineIndex ?: -1))
        }
    }
}
