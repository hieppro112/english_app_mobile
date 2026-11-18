package com.vocab.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.vocab.app.data.Category
import com.vocab.app.data.Grade
import com.vocab.app.data.Speaker
import com.vocab.app.data.Srs
import com.vocab.app.data.Word
import com.vocab.app.data.WordRepository
import com.vocab.app.databinding.ActivityStudyBinding
import kotlin.math.max

/**
 * Flashcard session with active recall + spaced repetition:
 * 1. See the English word (+ emoji, audio) and try to recall the meaning.
 * 2. Reveal meaning, example sentence and a Vietnamese memory tip.
 * 3. Rate yourself — the app schedules the next review accordingly.
 */
class StudyActivity : AppCompatActivity() {
    private lateinit var b: ActivityStudyBinding
    private lateinit var srs: Srs
    private lateinit var speaker: Speaker
    private val queue = ArrayDeque<Word>()
    private var total = 0
    private val finished = mutableSetOf<String>()
    private var current: Word? = null
    private var category: Category? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStudyBinding.inflate(layoutInflater)
        setContentView(b.root)
        srs = Srs(this)
        speaker = Speaker(this)
        category = Category.of(intent.getStringExtra(EXTRA_CAT))
        val mode = intent.getIntExtra(EXTRA_MODE, MODE_MIXED)

        b.tvTitle.text = category?.let { "${it.emoji} ${it.title}" } ?: when (mode) {
            MODE_REVIEW -> "🔁 Ôn tập"
            MODE_NEW -> "✨ Từ mới"
            else -> "📚 Học"
        }
        b.btnBack.setOnClickListener { finish() }
        b.btnSpeak.setOnClickListener { current?.let { speaker.speak(it.word) } }
        b.card.setOnClickListener { reveal() }
        b.cardContent.setOnClickListener { reveal() }
        b.btnShow.setOnClickListener { reveal() }
        b.btnAgain.setOnClickListener { grade(Grade.AGAIN) }
        b.btnHard.setOnClickListener { grade(Grade.HARD) }
        b.btnGood.setOnClickListener { grade(Grade.GOOD) }
        b.btnEasy.setOnClickListener { grade(Grade.EASY) }
        b.btnDoneHome.setOnClickListener { finish() }
        b.btnDoneQuiz.setOnClickListener {
            startActivity(QuizActivity.intent(this, category)); finish()
        }

        queue.addAll(buildSession(mode))
        total = queue.size
        next()
    }

    override fun onDestroy() { speaker.shutdown(); super.onDestroy() }

    private fun buildSession(mode: Int): List<Word> {
        val pool = category?.let { WordRepository.byCategory(this, it) } ?: WordRepository.all(this)
        val now = System.currentTimeMillis()
        val due = pool.filter { srs.isDue(it.id, now) }.sortedBy { srs.state(it.id)?.due ?: 0 }.take(50)
        val newCount = max(5, srs.dailyNewGoal - srs.newToday)
        val fresh = pool.filter { srs.isNew(it.id) }.shuffled().take(newCount)
        return when (mode) {
            MODE_REVIEW -> due.ifEmpty { fresh }
            MODE_NEW -> fresh
            else -> due + fresh
        }
    }

    private fun next() {
        val w = queue.removeFirstOrNull()
        if (w == null) { showDone(); return }
        current = w
        b.progress.max = max(1, total)
        b.progress.setProgressCompat(finished.size, true)
        b.tvCounter.text = "${finished.size}/$total"

        b.tvBadge.text = if (srs.isNew(w.id)) "🆕 Từ mới · ${w.category.title}" else "🔁 Ôn lại · ${w.category.title}"
        b.tvEmoji.text = w.emoji
        b.tvWord.text = w.word
        b.tvIpa.text = "${w.ipa} · ${w.pos}"
        b.tvMeaning.text = w.meaning
        b.tvExampleEn.text = "“${w.exampleEn}”"
        b.tvExampleVi.text = w.exampleVi
        b.tvTip.text = "💡 Mẹo nhớ: ${w.tip}"

        b.backSide.visibility = View.GONE
        b.tvHint.visibility = View.VISIBLE
        b.btnShow.visibility = View.VISIBLE
        b.gradeBar.visibility = View.GONE
        b.card.animate().cancel()
        b.card.rotationY = 0f
        speaker.speak(w.word)
    }

    private fun reveal() {
        val w = current ?: return
        if (b.backSide.visibility == View.VISIBLE) return
        b.card.animate().rotationY(90f).setDuration(120).withEndAction {
            b.backSide.visibility = View.VISIBLE
            b.tvHint.visibility = View.GONE
            b.card.rotationY = -90f
            b.card.animate().rotationY(0f).setDuration(120).start()
        }.start()
        b.btnShow.visibility = View.GONE
        b.gradeBar.visibility = View.VISIBLE
        b.btnAgain.text = label(w, Grade.AGAIN)
        b.btnHard.text = label(w, Grade.HARD)
        b.btnGood.text = label(w, Grade.GOOD)
        b.btnEasy.text = label(w, Grade.EASY)
    }

    private fun label(w: Word, g: Grade) = "${g.label}\n${srs.intervalText(srs.preview(w.id, g))}"

    private fun grade(g: Grade) {
        val w = current ?: return
        srs.answer(w.id, g)
        if (g == Grade.AGAIN) {
            // Show it again a few cards later in this same session.
            val pos = minOf(3, queue.size)
            queue.add(pos, w)
        } else {
            finished += w.id
        }
        next()
    }

    private fun showDone() {
        b.card.visibility = View.GONE
        b.btnShow.visibility = View.GONE
        b.gradeBar.visibility = View.GONE
        b.progress.setProgressCompat(b.progress.max, true)
        b.tvCounter.text = "$total/$total"
        b.doneView.visibility = View.VISIBLE
        b.tvDone.text = if (total == 0) "Không còn từ nào cần học lúc này 👏\nQuay lại sau nhé!"
        else "Hoàn thành $total từ!\n🔥 Chuỗi ${srs.streak} ngày liên tiếp\n\nKiểm tra ngay giúp nhớ lâu hơn gấp nhiều lần."
        b.btnDoneQuiz.visibility = if (total == 0) View.GONE else View.VISIBLE
    }

    companion object {
        const val MODE_MIXED = 0
        const val MODE_REVIEW = 1
        const val MODE_NEW = 2
        private const val EXTRA_CAT = "cat"
        private const val EXTRA_MODE = "mode"
        fun intent(c: Context, cat: Category?, mode: Int) =
            Intent(c, StudyActivity::class.java).putExtra(EXTRA_CAT, cat?.key).putExtra(EXTRA_MODE, mode)
    }
}
