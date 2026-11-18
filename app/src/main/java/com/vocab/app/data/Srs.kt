package com.vocab.app.data

import android.content.Context
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Spaced repetition (simplified SM-2).
 * Words you forget come back soon; words you know are pushed further and further away,
 * so review time is spent on exactly the words you are about to forget.
 */
data class CardState(
    val reps: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val due: Long = 0L,
    val lapses: Int = 0,
) {
    val mastered get() = intervalDays >= MASTERED_DAYS

    companion object {
        const val MASTERED_DAYS = 21
        fun decode(s: String): CardState? = runCatching {
            val p = s.split(',')
            CardState(p[0].toInt(), p[1].toInt(), p[2].toDouble(), p[3].toLong(), p[4].toInt())
        }.getOrNull()
    }

    fun encode() = "$reps,$intervalDays,$ease,$due,$lapses"
}

enum class Grade(val label: String) { AGAIN("Quên"), HARD("Khó"), GOOD("Nhớ"), EASY("Dễ") }

class Srs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("srs", Context.MODE_PRIVATE)
    private val stats = context.applicationContext.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun state(id: String): CardState? = prefs.getString(id, null)?.let(CardState::decode)

    fun isNew(id: String) = !prefs.contains(id)

    fun isDue(id: String, now: Long = System.currentTimeMillis()) =
        state(id)?.let { it.due <= now } ?: false

    /** Returns the state that would result from answering with [g] (used for button previews too). */
    fun preview(id: String, g: Grade, now: Long = System.currentTimeMillis()): CardState {
        val s = state(id) ?: CardState()
        return when (g) {
            Grade.AGAIN -> s.copy(reps = 0, intervalDays = 0, ease = max(1.3, s.ease - 0.2),
                due = now + MINUTE, lapses = s.lapses + 1)
            Grade.HARD -> {
                val iv = if (s.reps == 0) 1 else max(1, (s.intervalDays * 1.2).roundToInt())
                s.copy(reps = s.reps + 1, intervalDays = iv, ease = max(1.3, s.ease - 0.15), due = now + iv * DAY)
            }
            Grade.GOOD -> {
                val iv = when (s.reps) { 0 -> 1; 1 -> 3; else -> max(s.intervalDays + 1, (s.intervalDays * s.ease).roundToInt()) }
                s.copy(reps = s.reps + 1, intervalDays = iv, due = now + iv * DAY)
            }
            Grade.EASY -> {
                val iv = if (s.reps == 0) 4 else max(s.intervalDays + 2, (s.intervalDays * s.ease * 1.3).roundToInt())
                s.copy(reps = s.reps + 1, intervalDays = iv, ease = s.ease + 0.15, due = now + iv * DAY)
            }
        }
    }

    fun answer(id: String, g: Grade) {
        val wasNew = isNew(id)
        prefs.edit().putString(id, preview(id, g).encode()).apply()
        recordActivity(wasNew)
    }

    /** Quiz mistake: bring the word back for review right now. */
    fun markForgotten(id: String) {
        val s = state(id) ?: CardState()
        prefs.edit().putString(id, s.copy(reps = 0, intervalDays = 0, due = System.currentTimeMillis(),
            lapses = s.lapses + 1).encode()).apply()
    }

    fun intervalText(s: CardState, now: Long = System.currentTimeMillis()): String {
        val ms = s.due - now
        return when {
            ms < HOUR -> "<${max(1, (ms / MINUTE).toInt())}p"
            ms < DAY -> "${ms / HOUR}g"
            s.intervalDays < 30 -> "${s.intervalDays} ngày"
            else -> "${s.intervalDays / 30} th"
        }
    }

    // ---- statistics ----
    fun counts(words: List<Word>): Triple<Int, Int, Int> { // learned, mastered, due
        val now = System.currentTimeMillis()
        var learned = 0; var mastered = 0; var due = 0
        words.forEach { w ->
            state(w.id)?.let {
                learned++
                if (it.mastered) mastered++
                if (it.due <= now) due++
            }
        }
        return Triple(learned, mastered, due)
    }

    val newToday: Int get() = if (stats.getString("day", "") == today()) stats.getInt("newToday", 0) else 0
    val reviewedToday: Int get() = if (stats.getString("day", "") == today()) stats.getInt("reviewedToday", 0) else 0
    var dailyNewGoal: Int
        get() = stats.getInt("dailyNewGoal", 10)
        set(v) = stats.edit().putInt("dailyNewGoal", v).apply()

    val streak: Int get() {
        val last = stats.getString("lastActive", null) ?: return 0
        val lastDay = LocalDate.parse(last)
        val todayD = LocalDate.now()
        return if (lastDay == todayD || lastDay == todayD.minusDays(1)) stats.getInt("streak", 0) else 0
    }

    private fun recordActivity(wasNew: Boolean) {
        val t = today()
        val e = stats.edit()
        if (stats.getString("day", "") != t) {
            e.putString("day", t).putInt("newToday", 0).putInt("reviewedToday", 0)
        }
        val sameDay = stats.getString("day", "") == t
        e.putInt("reviewedToday", (if (sameDay) stats.getInt("reviewedToday", 0) else 0) + 1)
        if (wasNew) e.putInt("newToday", (if (sameDay) stats.getInt("newToday", 0) else 0) + 1)

        val last = stats.getString("lastActive", null)
        if (last != t) {
            val cont = last != null && LocalDate.parse(last) == LocalDate.now().minusDays(1)
            e.putInt("streak", if (cont) stats.getInt("streak", 0) + 1 else 1)
            e.putString("lastActive", t)
        }
        e.apply()
    }

    private fun today() = LocalDate.now().toString()

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

/** Picks a "word of the moment" — favours words you are currently learning so the widget doubles as review. */
object WordPicker {
    fun pick(context: Context, exclude: String? = null): Word? {
        val all = WordRepository.all(context).filter { it.id != exclude }
        if (all.isEmpty()) return null
        val srs = Srs(context)
        val learning = all.filter { w -> srs.state(w.id)?.let { !it.mastered } ?: false }
        val r = Math.random()
        return when {
            learning.isNotEmpty() && r < 0.6 -> learning.random()
            else -> all.random()
        }
    }
}
