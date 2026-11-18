package com.vocab.app.ui

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vocab.app.data.Speaker
import com.vocab.app.data.Srs
import com.vocab.app.data.Word
import com.vocab.app.databinding.SheetWordDetailBinding

object WordDetailSheet {
    fun show(context: Context, w: Word, speaker: Speaker) {
        val b = SheetWordDetailBinding.inflate(LayoutInflater.from(context))
        b.tvEmoji.text = w.emoji
        b.tvWord.text = w.word
        b.tvIpa.text = "${w.ipa}  ·  ${w.pos}"
        b.tvMeaning.text = w.meaning
        b.tvExampleEn.text = "“${w.exampleEn}”"
        b.tvExampleVi.text = w.exampleVi
        b.tvTip.text = "💡 ${w.tip}"
        b.tvStatus.text = statusText(Srs(context), w)
        b.btnSpeak.setOnClickListener { speaker.speak(w.word) }
        BottomSheetDialog(context).apply { setContentView(b.root) }.show()
        speaker.speak(w.word)
    }

    fun statusText(srs: Srs, w: Word): String {
        val s = srs.state(w.id) ?: return "⚪ Chưa học"
        return when {
            s.mastered -> "🟢 Đã thuộc · ôn lại sau ${srs.intervalText(s)}"
            s.due <= System.currentTimeMillis() -> "🔴 Cần ôn ngay"
            else -> "🟡 Đang học · ôn lại sau ${srs.intervalText(s)}"
        }
    }
}
