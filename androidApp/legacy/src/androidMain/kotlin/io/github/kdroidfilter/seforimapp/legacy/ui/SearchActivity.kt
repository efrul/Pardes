package io.github.kdroidfilter.seforimapp.legacy.ui

import android.content.Context
import android.content.Intent

class SearchActivity : BaseActivity() {
    companion object {
        const val EXTRA_QUERY = "query"

        fun start(context: Context, bookId: Long, lineIndex: Int?) {
            context.startActivity(Intent(context, SearchActivity::class.java).putExtra("book", bookId).putExtra("line", lineIndex ?: -1))
        }
    }
}
