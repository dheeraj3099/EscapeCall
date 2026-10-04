package com.example.escapecall.domain

import kotlin.math.min

object CodewordMatcher {
    fun matches(transcript: String, codeword: String): Boolean {
        val normalizedTranscript = normalize(transcript)
        val normalizedCodeword = normalize(codeword)
        if (normalizedTranscript.isBlank() || normalizedCodeword.isBlank()) return false
        if (normalizedTranscript.contains(normalizedCodeword)) return true

        val phraseWords = normalizedCodeword.split(" ")
        val transcriptWords = normalizedTranscript.split(" ")
        if (phraseWords.isEmpty() || transcriptWords.size < phraseWords.size) return false

        val phrase = phraseWords.joinToString(" ")
        return transcriptWords
            .windowed(size = phraseWords.size, step = 1)
            .map { it.joinToString(" ") }
            .any { candidate ->
                levenshtein(candidate, phrase) <= allowedDistance(phrase.length)
            }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun allowedDistance(length: Int): Int {
        return when {
            length < 6 -> 0
            length < 12 -> 1
            else -> 2
        }
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
