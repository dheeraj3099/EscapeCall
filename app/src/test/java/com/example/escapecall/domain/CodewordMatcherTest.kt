package com.example.escapecall.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodewordMatcherTest {
    @Test
    fun matchesExactPhraseIgnoringCaseAndPunctuation() {
        assertTrue(CodewordMatcher.matches("Can you bring the Blue Umbrella?", "blue umbrella"))
    }

    @Test
    fun matchesSmallTranscriptionTypo() {
        assertTrue(CodewordMatcher.matches("I left the blue umbrela there", "blue umbrella"))
    }

    @Test
    fun rejectsUnrelatedSpeech() {
        assertFalse(CodewordMatcher.matches("Everything is totally normal", "blue umbrella"))
    }
}
