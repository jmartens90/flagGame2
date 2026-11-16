package com.flaggame.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatchingTest {
    @Test fun normalizeStripsAccentsCaseAndPunctuation() {
        assertEquals("cote d ivoire", Place.normalize("Côte d'Ivoire"))
        assertEquals("netherlands", Place.normalize("The Netherlands"))
        assertEquals("st lucia", Place.normalize("Saint Lucia"))
        assertEquals("trinidad and tobago", Place.normalize("Trinidad & Tobago"))
    }

    @Test fun levenshtein() {
        assertEquals(0, Place.levenshtein("peru", "peru"))
        assertEquals(1, Place.levenshtein("germny", "germany"))
        assertEquals(1, Place.levenshtein("argentnia", "argentina"))
    }

    @Test fun windowContainsPoint() {
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(GeoMap.contains(square, 5f, 5f))
        assertFalse(GeoMap.contains(square, 15f, 5f))
    }
}

class FuzzyCountryTest {
    private fun c(code: String, name: String, capital: String) = Place(
        FlagSet.COUNTRIES, code, name, emptyMap(), emptyList(), "Africa", 0f, 0f, false, true,
        capital, emptyMap(), emptyList(), 0f, 0f,
    )
    private val gambia = c("gm", "Gambia", "Banjul")
    private val zambia = c("zm", "Zambia", "Lusaka")
    private val all = listOf(gambia, zambia)

    @Test fun typoAccepted() = assertTrue(gambia.matches("gambai", all) && gambia.matches("Gambia ", all) && gambia.matches("the gambia", all))
    @Test fun otherCountryRejected() = assertFalse(gambia.matches("Zambia", all))
    @Test fun capitalTypoAccepted() = assertTrue(gambia.matchesCapital("banjull", all))
    @Test fun otherCapitalRejected() = assertFalse(gambia.matchesCapital("Lusaka", all))
}

class LearningWeightTest {
    @Test fun weights() {
        assertEquals(1.0, Progress.weightFor(0), 0.0)
        assertEquals(1.0, Progress.weightFor(4), 0.0)
        assertEquals(0.5, Progress.weightFor(5), 0.0)
        assertEquals(0.1, Progress.weightFor(10), 0.0)
    }
}
