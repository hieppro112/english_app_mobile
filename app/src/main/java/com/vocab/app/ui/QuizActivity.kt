package com.vocab.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.vocab.app.R
import com.vocab.app.data.Category
import com.vocab.app.data.Speaker
import com.vocab.app.data.Srs
import com.vocab.app.data.Word
import com.vocab.app.data.WordRepository
import com.vocab.app.databinding.ActivityQuizBinding

/**
 * Mixed quiz (10 questions): EN→VI, VI→EN, listening and fill-in-the-blank.
 * Testing yourself (retrieval practice) is one of the strongest ways to remember;
 * wrong answers are sent straight back into the review queue.
 */
class QuizActivity : AppCompatActivity() {
    private enum class Type { EN_VI, VI_EN, LISTEN, BLANK }
    private data class Question(val word: Word, val type: Type, val options: List<Word>, val blank: String? = null)

    private lateinit var b: ActivityQuizBinding
    private lateinit var srs: Srs
    private lateinit var speaker: Speaker
    private var category: Category? = null
    private var questions = listOf<Question>()
    private var index = 0
    private var score = 0
    private val wrongWords = mutableListOf<Word>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)
        srs = Srs(this)
        speaker = Speaker(this)
        category = Category.of(intent.getStringExtra(EXTRA_CAT))
        b.tvTitle.text = "🎯 " + (category?.title ?: "Kiểm tra tổng hợp")
        b.btnBack.setOnClickListener { finish() }
        b.btnListen.setOnClickListener { questions.getOrNull(index)?.let { speaker.speak(it.word.word) } }
        b.btnNext.setOnClickListener { index++; show() }
        b.btnHome.setOnClickListener { finish() }
        b.btnAgain.setOnClickListener { start() }
        start()
    }

    override fun onDestroy() { speaker.shutdown(); super.onDestroy() }

    private fun start() {
        val pool = category?.let { WordRepository.byCategory(this, it) } ?: WordRepository.all(this)
        // Prefer words already studied (that's what we want to consolidate), top up with others.
        val studied = pool.filter { !srs.isNew(it.id) }.shuffled()
        val picked = (studied.take(10) + pool.shuffled()).distinctBy { it.id }.take(10)
        questions = picked.map { w ->
            val distractors = pool.filter { it.id != w.id && it.meaning != w.meaning }.shuffled().take(3)
            val opts = (distractors + w).shuffled()
            val blank = blankOut(w)
            var type = Type.entries.random()
            if (type == Type.BLANK && blank == null) type = Type.EN_VI
            Question(w, type, opts, blank)
        }
        index = 0; score = 0; wrongWords.clear()
        b.resultView.visibility = View.GONE
        b.quizView.visibility = View.VISIBLE
        show()
    }

    /** Replaces the word inside its example sentence with "_____" (handles simple -s/-ed/-ing forms). */
    private fun blankOut(w: Word): String? {
        val regex = Regex("\\b${Regex.escape(w.word)}(s|es|d|ed|ing)?\\b", RegexOption.IGNORE_CASE)
        return if (regex.containsMatchIn(w.exampleEn)) w.exampleEn.replace(regex, "_____") else null
    }

    private fun show() {
        if (index >= questions.size) { showResult(); return }
        val q = questions[index]
        b.progress.max = questions.size
        b.progress.setProgressCompat(index, true)
        b.tvScore.text = "✔ $score"
        b.tvFeedback.visibility = View.GONE
        b.btnNext.visibility = View.GONE
        b.btnListen.visibility = View.GONE
        b.tvQEmoji.visibility = View.VISIBLE

        when (q.type) {
            Type.EN_VI -> {
                b.tvType.text = "Câu ${index + 1}/${questions.size} · Chọn nghĩa đúng"
                b.tvQEmoji.text = q.word.emoji
                b.tvQuestion.text = "${q.word.word}\n${q.word.ipa}"
                speaker.speak(q.word.word)
            }
            Type.VI_EN -> {
                b.tvType.text = "Câu ${index + 1}/${questions.size} · Từ tiếng Anh nào?"
                b.tvQEmoji.text = q.word.emoji
                b.tvQuestion.text = q.word.meaning
            }
            Type.LISTEN -> {
                b.tvType.text = "Câu ${index + 1}/${questions.size} · Nghe và chọn từ"
                b.tvQEmoji.text = "🎧"
                b.tvQuestion.text = "Bạn nghe thấy từ gì?"
                b.btnListen.visibility = View.VISIBLE
                speaker.speak(q.word.word)
            }
            Type.BLANK -> {
                b.tvType.text = "Câu ${index + 1}/${questions.size} · Điền vào chỗ trống"
                b.tvQEmoji.visibility = View.GONE
                val hint = "(${q.word.exampleVi})"
                b.tvQuestion.text = android.text.SpannableString("${q.blank}\n\n$hint").apply {
                    val start = length - hint.length
                    setSpan(android.text.style.RelativeSizeSpan(0.65f), start, length, 0)
                    setSpan(android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), start, length, 0)
                }
            }
        }

        b.options.removeAllViews()
        q.options.forEach { opt ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = if (q.type == Type.EN_VI) opt.meaning else opt.word
                isAllCaps = false
                textSize = 17f
                cornerRadius = dp(14)
                minHeight = dp(58)
                tag = opt.id
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setOnClickListener { answer(q, opt, this) }
            }
            b.options.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }

    private fun answer(q: Question, chosen: Word, btn: MaterialButton) {
        val ok = chosen.id == q.word.id
        for (i in 0 until b.options.childCount) {
            val v = b.options.getChildAt(i) as MaterialButton
            v.isEnabled = false
            if (v.tag == q.word.id) paint(v, R.color.good)
        }
        if (ok) {
            score++
        } else {
            paint(btn, R.color.bad)
            wrongWords += q.word
            srs.markForgotten(q.word.id)
        }
        speaker.speak(q.word.word)
        b.tvScore.text = "✔ $score"
        val w = q.word
        b.tvFeedback.text = (if (ok) "✅ Chính xác!\n" else "❌ Chưa đúng — đáp án: ${w.word} = ${w.meaning}\n") +
            "${w.emoji} ${w.word} ${w.ipa}: ${w.meaning}\n💡 ${w.tip}"
        b.tvFeedback.visibility = View.VISIBLE
        b.btnNext.visibility = View.VISIBLE
        b.btnNext.text = if (index == questions.size - 1) "Xem kết quả" else "Tiếp tục →"
        b.quizView.post { b.quizView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun paint(v: MaterialButton, color: Int) {
        val c = ContextCompat.getColor(this, color)
        v.strokeColor = ColorStateList.valueOf(c)
        v.strokeWidth = dp(3)
        v.setTextColor(c)
    }

    private fun showResult() {
        b.quizView.visibility = View.GONE
        b.btnNext.visibility = View.GONE
        b.resultView.visibility = View.VISIBLE
        b.progress.setProgressCompat(questions.size, true)
        val pct = score * 100 / questions.size.coerceAtLeast(1)
        b.tvResultEmoji.text = when { pct >= 90 -> "🏆"; pct >= 70 -> "🎉"; pct >= 50 -> "💪"; else -> "📖" }
        val wrong = if (wrongWords.isEmpty()) "Không sai câu nào, tuyệt vời!"
        else "Từ cần ôn lại (đã thêm vào lượt ôn tập):\n" + wrongWords.joinToString("\n") { "• ${it.word} – ${it.meaning}" }
        b.tvResult.text = "Đúng $score/${questions.size} câu ($pct%)\n\n$wrong"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_CAT = "cat"
        fun intent(c: Context, cat: Category?) = Intent(c, QuizActivity::class.java).putExtra(EXTRA_CAT, cat?.key)
    }
}
