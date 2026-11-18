package com.vocab.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vocab.app.R
import com.vocab.app.data.Category
import com.vocab.app.data.Speaker
import com.vocab.app.data.Srs
import com.vocab.app.data.Word
import com.vocab.app.data.WordPicker
import com.vocab.app.data.WordRepository
import com.vocab.app.databinding.ActivityMainBinding
import com.vocab.app.databinding.ItemCategoryBinding
import com.vocab.app.widget.VocabWidget

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var srs: Srs
    private lateinit var speaker: Speaker
    private var current: Word? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        srs = Srs(this)
        speaker = Speaker(this)

        b.btnRandNext.setOnClickListener { showRandom() }
        b.btnRandSpeak.setOnClickListener { current?.let { speaker.speak(it.word) } }
        b.cardRandom.setOnClickListener { current?.let { WordDetailSheet.show(this, it, speaker) } }

        b.btnReview.setOnClickListener { startActivity(StudyActivity.intent(this, null, StudyActivity.MODE_REVIEW)) }
        b.btnLearnNew.setOnClickListener { startActivity(StudyActivity.intent(this, null, StudyActivity.MODE_NEW)) }
        b.btnQuizAll.setOnClickListener { startActivity(QuizActivity.intent(this, null)) }

        handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    /** Opened by tapping the widget: show that word. */
    private fun handleWidgetIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_WORD_ID) ?: return
        intent.removeExtra(EXTRA_WORD_ID)
        WordRepository.byId(this, id)?.let {
            current = it
            fromWidget = true
            bindRandom(it)
            WordDetailSheet.show(this, it, speaker)
        }
    }

    override fun onResume() {
        super.onResume()
        if (current == null || !fromWidget) showRandom()
        fromWidget = false
        renderStats()
        renderCategories()
    }

    private var fromWidget = false

    override fun onStop() {
        super.onStop()
        // Leaving the app (back to the phone's home screen) → widget shows a new word.
        VocabWidget.refresh(this)
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun showRandom() {
        WordPicker.pick(this, exclude = current?.id)?.let { current = it; bindRandom(it) }
    }

    private fun bindRandom(w: Word) {
        b.tvRandCat.text = "💡 Từ gợi nhớ · ${w.category.emoji} ${w.category.title}"
        b.tvRandEmoji.text = w.emoji
        b.tvRandWord.text = w.word
        b.tvRandIpa.text = "${w.ipa}  ·  ${w.pos}"
        b.tvRandMeaning.text = w.meaning
        b.tvRandTip.text = "💡 ${w.tip}"
    }

    private fun renderStats() {
        val all = WordRepository.all(this)
        val (_, mastered, due) = srs.counts(all)
        b.tvStreak.text = "🔥 ${srs.streak} ngày"
        b.tvToday.text = "✨ ${srs.newToday}/${srs.dailyNewGoal} mới"
        b.tvMastered.text = "🏆 $mastered thuộc"
        b.btnReview.text = if (due > 0) "🔁 Ôn tập ngay ($due từ đến hạn)" else "✅ Đã ôn hết — học thêm từ mới nhé"
    }

    private fun renderCategories() {
        b.categoryContainer.removeAllViews()
        val colors = mapOf(Category.IT to R.color.cat_it, Category.DAILY to R.color.cat_daily, Category.MACHINE to R.color.cat_machine)
        Category.entries.forEach { cat ->
            val words = WordRepository.byCategory(this, cat)
            val (learned, mastered, due) = srs.counts(words)
            val item = ItemCategoryBinding.inflate(LayoutInflater.from(this), b.categoryContainer, true)
            item.tvEmoji.text = cat.emoji
            item.tvTitle.text = cat.title
            item.tvSub.text = "Đã học $learned/${words.size} · Thuộc $mastered" + if (due > 0) " · 🔴 $due cần ôn" else ""
            item.progress.setProgressCompat(if (words.isEmpty()) 0 else learned * 100 / words.size, false)
            item.progress.setIndicatorColor(ContextCompat.getColor(this, colors.getValue(cat)))
            item.btnLearn.setOnClickListener { startActivity(StudyActivity.intent(this, cat, StudyActivity.MODE_MIXED)) }
            item.btnQuiz.setOnClickListener { startActivity(QuizActivity.intent(this, cat)) }
            item.btnList.setOnClickListener { startActivity(WordListActivity.intent(this, cat)) }
        }
    }

    companion object {
        const val EXTRA_WORD_ID = "word_id"
    }
}
