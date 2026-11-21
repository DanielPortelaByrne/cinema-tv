package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldEligibilityTest {
    private val policy = GoldPolicy()
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private fun imdb(score: Double, count: Long?, age: Long? = 10 * day) = RatingEvidence(RatingSource.IMDB, score, count, age?.let { now - it })
    private fun lb(score: Double, count: Long?) = RatingEvidence(RatingSource.LETTERBOXD, score, count, now - day)
    private fun tmdb(score: Double, count: Long?) = RatingEvidence(RatingSource.TMDB, score, count, now - day)
    private fun movie(vararg r: RatingEvidence, consensus: CriticalConsensus? = null) = policy.decide(GoldKind.MOVIE, r.toList(), now, consensus)

    @Test fun aSmallSampleNineAndAHalfIsNotGold() = assertFalse(movie(imdb(9.5, 300)).eligible)
    @Test fun aPopularEightPointTwoIsGold() {
        val d = movie(imdb(8.2, 200_000))
        assertTrue(d.eligible); assertEquals(RatingSource.IMDB, d.source)
    }
    @Test fun exactlyEightWithFullConfidenceIsGoldAndJustBelowIsNot() {
        assertTrue(movie(imdb(8.0, 100_000)).eligible)
        assertFalse(movie(imdb(7.99, 100_000)).eligible)
    }
    @Test fun aMidSizedCrowdNeedsAClearerScore() {
        assertFalse(movie(imdb(8.2, 30_000)).eligible)
        assertTrue(movie(imdb(8.6, 30_000)).eligible)
    }
    @Test fun tooFewRatingsIsNeverGoldWhateverTheScore() = assertFalse(movie(imdb(8.9, 24_999)).eligible)
    @Test fun letterboxdFourStarsIsTheBoundary() {
        assertTrue(movie(lb(4.0, 50_000)).eligible)
        assertFalse(movie(lb(3.99, 50_000)).eligible)
        assertEquals(RatingSource.LETTERBOXD, movie(lb(4.3, 60_000)).source)
    }
    @Test fun eitherHeadlineSourceCanQualify() {
        val d = movie(imdb(7.4, 500_000), lb(4.1, 80_000))
        assertTrue(d.eligible); assertEquals(RatingSource.LETTERBOXD, d.source)
    }
    @Test fun missingOrUnusableEvidenceFailsClosed() {
        assertFalse(movie().eligible)
        assertFalse(movie(imdb(8.5, null)).eligible)
        assertFalse(movie(imdb(8.5, 200_000, age = null)).eligible)
        assertFalse(movie(imdb(Double.NaN, 200_000)).eligible)
        assertFalse(movie(imdb(11.0, 200_000)).eligible)
    }
    @Test fun staleEvidenceIsIgnored() {
        assertFalse(movie(imdb(8.5, 200_000, age = 400 * day)).eligible)
        assertEquals("IMDb rating is stale", movie(imdb(8.5, 200_000, age = 400 * day)).reason)
    }
    @Test fun sourcesThatContradictEachOtherAreNotGold() = assertFalse(movie(imdb(8.8, 200_000), lb(2.9, 80_000)).eligible)
    @Test fun tmdbIsOnlyTheFallbackAndKeepsItsOwnName() {
        val d = movie(tmdb(8.4, 30_000))
        assertTrue(d.eligible); assertEquals(RatingSource.TMDB, d.source)
        // With a headline source present, a strong TMDB score cannot rescue a weak one.
        assertFalse(movie(imdb(7.0, 300_000), tmdb(8.6, 30_000)).eligible)
    }
    @Test fun aHiddenGemNeedsVerifiedIndependentConsensus() {
        val gem = imdb(7.7, 8_000)
        assertFalse(movie(gem).eligible)
        assertFalse(movie(gem, consensus = CriticalConsensus("Critics", 0.9, verified = false)).eligible)
        assertFalse(movie(gem, consensus = CriticalConsensus("Critics", 0.6, verified = true)).eligible)
        val d = movie(gem, consensus = CriticalConsensus("Critics", 0.9, verified = true))
        assertTrue(d.eligible); assertTrue(d.reason.contains("Critics"))
    }
    @Test fun theExceptionIsBoundedBelowAndByCount() {
        val ok = CriticalConsensus("Critics", 0.95, verified = true)
        assertFalse(movie(imdb(7.4, 8_000), consensus = ok).eligible)
        assertFalse(movie(imdb(7.7, 3_000), consensus = ok).eligible)
    }
    @Test fun seriesUseTheirOwnThresholds() {
        val r = listOf(imdb(8.5, 12_000))
        assertTrue(policy.decide(GoldKind.SERIES, r, now).eligible)
        assertFalse(policy.decide(GoldKind.MOVIE, r, now).eligible)
    }
    @Test fun thresholdsAreConfigurable() {
        val strict = GoldPolicy(movie = mapOf(RatingSource.IMDB to GoldThresholds(9.0, 1_000, 2_000, 0.0, 8.5, 500)))
        assertFalse(strict.decide(GoldKind.MOVIE, listOf(imdb(8.5, 5_000)), now).eligible)
        assertTrue(strict.decide(GoldKind.MOVIE, listOf(imdb(9.0, 5_000)), now).eligible)
        assertFalse(strict.decide(GoldKind.MOVIE, listOf(lb(4.5, 99_999)), now).eligible)   // unsupported source
    }
    @Test fun theLatestReadOfASourceWins() {
        val old = RatingEvidence(RatingSource.IMDB, 6.0, 200_000, now - 100 * day)
        assertTrue(movie(old, imdb(8.4, 200_000)).eligible)
    }
}
