package com.vocab.app.data

import android.content.Context

enum class Category(val key: String, val title: String, val emoji: String, val file: String) {
    IT("it", "Công nghệ thông tin", "💻", "words_it.txt"),
    DAILY("daily", "Đời sống hằng ngày", "🏠", "words_daily.txt"),
    MACHINE("machine", "Máy móc – Cơ khí", "⚙️", "words_machine.txt");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

data class Word(
    val id: String,
    val category: Category,
    val emoji: String,
    val word: String,
    val ipa: String,
    val pos: String,
    val meaning: String,
    val exampleEn: String,
    val exampleVi: String,
    val tip: String,
)

/** Loads vocabulary from assets once and caches it in memory. */
object WordRepository {
    @Volatile private var cache: List<Word>? = null

    fun all(context: Context): List<Word> = cache ?: synchronized(this) {
        cache ?: load(context.applicationContext).also { cache = it }
    }

    fun byCategory(context: Context, c: Category) = all(context).filter { it.category == c }

    fun byId(context: Context, id: String?) = all(context).firstOrNull { it.id == id }

    private fun load(context: Context): List<Word> = Category.entries.flatMap { cat ->
        context.assets.open(cat.file).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim().removePrefix("﻿") }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val f = line.split('|').map { it.trim() }
                    if (f.size < 8) null
                    else Word(
                        id = "${cat.key}:${f[1].lowercase()}",
                        category = cat,
                        emoji = f[0], word = f[1], ipa = f[2], pos = f[3],
                        meaning = f[4], exampleEn = f[5], exampleVi = f[6], tip = f[7],
                    )
                }.toList()
        }
    }
}
