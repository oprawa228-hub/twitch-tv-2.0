package com.oprawa.twitch20.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentNoticesTest {
    @Test fun bundledCreditsAndEverySourceAddressSurviveTvFormatting() {
        val source = File("src/main/assets/THIRD_PARTY_NOTICES.txt").readText()
        val rendered = formatComponentNotices(source).text
        val credits = source.lineSequence().filter { it.startsWith("| ") }.drop(1)
        for (row in credits) {
            val cells = row.removeSurrounding("|").split('|').map { it.trim() }
            for (field in cells.take(3)) {
                val plain = field.replace("`", "").replace("**", "")
                assertTrue("Missing credit: $plain", rendered.contains(plain))
            }
            for (link in Regex("""\[([^\]]+)\]\(([^)]+)\)""").findAll(cells[3])) {
                assertTrue(rendered.contains("${link.groupValues[1]} · ${link.groupValues[2]}"))
            }
        }
        assertFalse(rendered.contains("|---"))
        assertFalse(rendered.contains("](https://"))
        assertTrue(rendered.contains("Материалы приложения"))
    }
}
