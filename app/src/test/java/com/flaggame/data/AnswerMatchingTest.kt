package com.flaggame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatchingTest {
    @Test fun normalizeStripsAccentsCaseAndPunctuation() {
        assertEquals("cote d ivoire", Country.normalize("Côte d'Ivoire"))
        assertEquals("netherlands", Country.normalize("The Netherlands"))
        assertEquals("st lucia", Country.normalize("Saint Lucia"))
        assertEquals("trinidad and tobago", Country.normalize("Trinidad & Tobago"))
    }

    @Test fun levenshtein() {
        assertEquals(0, Country.levenshtein("peru", "peru"))
        assertEquals(1, Country.levenshtein("germny", "germany"))
        assertEquals(1, Country.levenshtein("argentnia", "argentina"))
    }

    @Test fun windowContainsPoint() {
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(WorldMap.contains(square, 5f, 5f))
        assertFalse(WorldMap.contains(square, 15f, 5f))
    }
}

class FuzzyCountryTest {
    private fun c(code: String, name: String) = Country(code, name, emptyList(), "Africa", 0.0, 0.0, 1.0, true)
    private val gambia = c("gm", "Gambia")
    private val zambia = c("zm", "Zambia")
    private val all = listOf(gambia, zambia)

    @Test fun typoAccepted() = assertTrue(gambia.matches("gambai", all) && gambia.matches("Gambia ", all) && gambia.matches("the gambia", all))
    @Test fun otherCountryRejected() = assertFalse(gambia.matches("Zambia", all))
}
