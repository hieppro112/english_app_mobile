package com.vocab.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vocab.app.data.Category
import com.vocab.app.data.Speaker
import com.vocab.app.data.Srs
import com.vocab.app.data.Word
import com.vocab.app.data.WordRepository
import com.vocab.app.databinding.ActivityWordListBinding
import com.vocab.app.databinding.ItemWordBinding

class WordListActivity : AppCompatActivity() {
    private lateinit var b: ActivityWordListBinding
    private lateinit var speaker: Speaker
    private lateinit var srs: Srs
    private lateinit var all: List<Word>
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWordListBinding.inflate(layoutInflater)
        setContentView(b.root)
        speaker = Speaker(this)
        srs = Srs(this)
        val cat = Category.of(intent.getStringExtra(EXTRA_CAT))
        all = cat?.let { WordRepository.byCategory(this, it) } ?: WordRepository.all(this)
        b.tvTitle.text = "${cat?.emoji ?: "📚"} ${cat?.title ?: "Tất cả"} (${all.size})"
        b.btnBack.setOnClickListener { finish() }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.etSearch.doAfterTextChanged { filter(it?.toString().orEmpty()) }
        filter("")
    }

    override fun onResume() { super.onResume(); adapter.notifyDataSetChanged() }
    override fun onDestroy() { speaker.shutdown(); super.onDestroy() }

    private fun filter(q: String) {
        val s = q.trim().lowercase()
        adapter.items = if (s.isEmpty()) all else all.filter { s in it.word.lowercase() || s in it.meaning.lowercase() }
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var items = listOf<Word>()
        inner class VH(val v: ItemWordBinding) : RecyclerView.ViewHolder(v.root)

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(ItemWordBinding.inflate(LayoutInflater.from(p.context), p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, i: Int) {
            val w = items[i]
            h.v.tvEmoji.text = w.emoji
            h.v.tvWord.text = "${w.word}  ${w.ipa}"
            h.v.tvMeaning.text = "${w.pos} ${w.meaning}"
            val st = srs.state(w.id)
            h.v.tvStatus.text = when {
                st == null -> "⚪"
                st.mastered -> "🟢"
                st.due <= System.currentTimeMillis() -> "🔴"
                else -> "🟡"
            }
            h.v.root.setOnClickListener { WordDetailSheet.show(this@WordListActivity, w, speaker) }
        }
    }

    companion object {
        private const val EXTRA_CAT = "cat"
        fun intent(c: Context, cat: Category?) = Intent(c, WordListActivity::class.java).putExtra(EXTRA_CAT, cat?.key)
    }
}
