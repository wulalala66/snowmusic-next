package com.mocharealm.accompanist.sample.data.utils

import android.icu.text.Transliterator
import com.mocharealm.accompanist.lyrics.core.model.karaoke.PhoneticLevel
import com.mocharealm.accompanist.lyrics.core.utils.PhoneticProvider

object AndroidPhoneticProvider: PhoneticProvider {
    private val transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower")
    }

    override val phoneticLevel: PhoneticLevel
        get() = PhoneticLevel.SYLLABLE

    override fun getPhonetic(string: String): String =
        transliterator?.transliterate(string) ?: string.lowercase()
}