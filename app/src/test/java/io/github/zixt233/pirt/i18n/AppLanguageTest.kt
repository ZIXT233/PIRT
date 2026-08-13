package io.github.zixt233.pirt.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test
    fun chineseLocalesDefaultToSimplifiedChinese() {
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, defaultLanguageFor(Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, defaultLanguageFor(Locale.TRADITIONAL_CHINESE))
    }

    @Test
    fun nonChineseLocalesDefaultToEnglish() {
        assertEquals(AppLanguage.ENGLISH, defaultLanguageFor(Locale.ENGLISH))
        assertEquals(AppLanguage.ENGLISH, defaultLanguageFor(Locale.JAPANESE))
        assertEquals(AppLanguage.ENGLISH, defaultLanguageFor(Locale.FRENCH))
    }
}
