package com.vocab.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vocab.app.R
import com.vocab.app.data.WordPicker
import com.vocab.app.data.WordRepository
import com.vocab.app.ui.MainActivity

/**
 * Home-screen widget showing a random word (like a bank app's quick widget).
 * A new word appears: every 30 min (system), when tapping "↻ Từ khác",
 * and every time you leave the app and go back to the home screen.
 */
class VocabWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids, newWord = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refresh(context)
        } else super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_REFRESH = "com.vocab.app.widget.REFRESH"
        private const val PREFS = "widget"

        /** Show a fresh random word on every placed widget. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VocabWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, newWord = true)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, newWord: Boolean) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastId = prefs.getString("current", null)
            val word = if (newWord) WordPicker.pick(context, exclude = lastId)
                       else WordRepository.byId(context, lastId) ?: WordPicker.pick(context)
            word ?: return
            prefs.edit().putString("current", word.id).apply()

            val views = RemoteViews(context.packageName, R.layout.widget_vocab).apply {
                setTextViewText(R.id.w_cat, "${word.category.emoji} ${word.category.title}")
                setTextViewText(R.id.w_emoji, word.emoji)
                setTextViewText(R.id.w_word, word.word)
                setTextViewText(R.id.w_ipa, "${word.ipa}  ·  ${word.pos}")
                setTextViewText(R.id.w_meaning, word.meaning)
                setTextViewText(R.id.w_example, "“${word.exampleEn}”")

                val refresh = Intent(context, VocabWidget::class.java).setAction(ACTION_REFRESH)
                setOnClickPendingIntent(R.id.w_refresh, PendingIntent.getBroadcast(
                    context, 1, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                val open = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_WORD_ID, word.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                    context, 2, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
