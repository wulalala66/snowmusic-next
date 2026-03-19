package com.mocharealm.accompanist.sample.data.utils

import android.icu.text.Transliterator
import com.github.promeg.pinyinhelper.Pinyin
import com.mocharealm.accompanist.lyrics.core.model.karaoke.PhoneticLevel
import com.mocharealm.accompanist.lyrics.core.utils.PhoneticProvider
import com.mocharealm.accompanist.lyrics.ui.utils.containsJapanese
import com.mocharealm.accompanist.lyrics.ui.utils.containsKorean
import com.mocharealm.accompanist.lyrics.ui.utils.isPunctuation
import com.mocharealm.accompanist.lyrics.ui.utils.isPureCjk

object AndroidPhoneticProvider : PhoneticProvider {

    private val koTransliterator by lazy {
        Transliterator.getInstance("Hangul-Latin; Latin-ASCII")
    }

    private val jpTransliterator by lazy {
        Transliterator.getInstance("Hiragana-Latin; Katakana-Latin; Latin-ASCII")
    }

    private val genericTransliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII")
    }

    override val phoneticLevel: PhoneticLevel = PhoneticLevel.SYLLABLE

    override fun getPhonetic(string: String): String {
        if (string.isBlank() || string.isPunctuation()) return string

        return when {
            string.containsKorean() -> {
                koTransliterator.transliterate(string).lowercase()
            }

            string.containsJapanese() -> {
                jpTransliterator.transliterate(string).lowercase()
            }

            string.isPureCjk() -> {
                Pinyin.toPinyin(string, " ").lowercase()
            }

            else -> {
                genericTransliterator.transliterate(string).lowercase()
            }
        }
    }
}