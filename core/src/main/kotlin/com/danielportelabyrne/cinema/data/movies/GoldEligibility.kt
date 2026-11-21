package com.danielportelabyrne.cinema.data.movies

/** Where a rating comes from. Never relabelled: a TMDB average is [TMDB], not IMDb. */
enum class RatingSource(val label: String, val scale: Double) {
    IMDB("IMDb", 10.0), LETTERBOXD("Letterboxd", 5.0), TMDB("TMDB", 10.0)
}

/** One rating as it was read: its source, score on the source's own scale, how many ratings it rests on, and when it was read. */
data class RatingEvidence(val source: RatingSource, val score: Double, val count: Long?, val updatedAtMs: Long?)

/** A second opinion from a different, independent source (a critics' aggregate), already normalised to 0..1 and checked by the caller. */
data class CriticalConsensus(val sourceName: String, val score: Double, val verified: Boolean)

enum class GoldKind { MOVIE, SERIES }

/** The outcome: eligible or not, by which source, and why in words a person can read. */
data class GoldDecision(val eligible: Boolean, val source: RatingSource?, val reason: String)

/**
 * How sure a source must be. [threshold] is the score on the source's scale; [minCount] ratings are needed at all and
 * [fullCount] make the score count at face value. Between the two the score must clear the threshold by a margin that
 * shrinks from [maxMargin] to nothing (so a small crowd needs a clearly higher score than a big one).
 * The exception ("hidden gem") route accepts [exceptionThreshold] up to [threshold] with [exceptionMinCount] ratings,
 * but only with an independent verified consensus of at least [GoldPolicy.consensusMin].
 */
data class GoldThresholds(
    val threshold: Double, val minCount: Long, val fullCount: Long, val maxMargin: Double,
    val exceptionThreshold: Double, val exceptionMinCount: Long
)

/**
 * Whether a title qualifies for Gold: acclaimed with real confidence, not a small-sample average. Pure and deterministic;
 * thresholds are configurable and the defaults are the decision recorded in docs/gold-eligibility-2026-11-21.md.
 * Fails closed: missing, undated, stale, tiny-sample, contradictory or unsupported evidence is never eligible.
 */
class GoldPolicy(
    private val movie: Map<RatingSource, GoldThresholds> = DEFAULT_MOVIE,
    private val series: Map<RatingSource, GoldThresholds> = DEFAULT_SERIES,
    /** Ratings older than this are ignored. */
    private val maxAgeMs: Long = 365L * DAY_MS,
    /** Two sources, normalised to 0..10, further apart than this contradict each other. */
    private val maxDisagreement: Double = 1.5,
    val consensusMin: Double = 0.85
) {
    fun decide(kind: GoldKind, ratings: List<RatingEvidence>, nowMs: Long, consensus: CriticalConsensus? = null): GoldDecision {
        val table = if (kind == GoldKind.MOVIE) movie else series
        if (ratings.isEmpty()) return no("No rating available")

        // Evidence that is usable: dated, fresh, with a count, on a supported source. Each source counts once (the latest read wins).
        val usable = ratings.filter { it.score.isFinite() && it.score > 0 }
            .groupBy { it.source }.mapValues { (_, all) -> all.maxByOrNull { it.updatedAtMs ?: Long.MIN_VALUE }!! }
        val problems = mutableListOf<String>()
        val fresh = usable.values.filter { e ->
            when {
                table[e.source] == null -> { problems += "${e.source.label} is not supported for ${kind.name.lowercase()}s"; false }
                e.updatedAtMs == null -> { problems += "${e.source.label} rating is undated"; false }
                nowMs - e.updatedAtMs > maxAgeMs -> { problems += "${e.source.label} rating is stale"; false }
                e.count == null -> { problems += "${e.source.label} has no rating count"; false }
                e.score > e.source.scale -> { problems += "${e.source.label} score is out of range"; false }
                else -> true
            }
        }
        if (fresh.isEmpty()) return no(problems.firstOrNull() ?: "No usable rating")

        // Headline sources first; TMDB is only the fallback when neither headline source is present at all.
        val headline = fresh.filter { it.source != RatingSource.TMDB }
        val candidates = (if (headline.isNotEmpty()) headline else fresh).sortedBy { it.source.ordinal }

        val normalised = candidates.map { it.score / it.source.scale * 10.0 }
        if (normalised.max() - normalised.min() > maxDisagreement) return no("Sources contradict each other")

        for (e in candidates) {
            val t = table.getValue(e.source)
            val count = e.count!!
            val enough = count >= t.minCount
            val required = t.threshold + margin(count, t)
            if (enough && e.score >= required) {
                return GoldDecision(true, e.source, "${e.source.label} ${fmt(e.score)} from $count ratings clears ${fmt(required)}")
            }
            // The bounded exception: a slightly lower score, from fewer ratings, with a verified independent consensus;
            // never a looser universal bar.
            if (e.score >= t.exceptionThreshold && count >= t.exceptionMinCount && consensus != null && consensus.verified && consensus.score >= consensusMin) {
                return GoldDecision(true, e.source, "${e.source.label} ${fmt(e.score)} from $count ratings, backed by ${consensus.sourceName} consensus")
            }
            problems += if (!enough) "${e.source.label}: only $count ratings (needs ${t.minCount})" else "${e.source.label} ${fmt(e.score)} is below ${fmt(required)}"
        }
        return no(problems.firstOrNull() ?: "Below the Gold standard")
    }

    private fun margin(count: Long, t: GoldThresholds): Double {
        if (count >= t.fullCount) return 0.0
        val span = (t.fullCount - t.minCount).coerceAtLeast(1)
        return t.maxMargin * (1.0 - (count - t.minCount).toDouble() / span)
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.ROOT, "%.2f", v)
    private fun no(reason: String) = GoldDecision(false, null, reason)

    companion object {
        private const val DAY_MS = 86_400_000L
        val DEFAULT_MOVIE = mapOf(
            RatingSource.IMDB to GoldThresholds(8.0, 25_000, 100_000, 0.5, 7.5, 5_000),
            RatingSource.LETTERBOXD to GoldThresholds(4.0, 10_000, 50_000, 0.25, 3.75, 2_000),
            RatingSource.TMDB to GoldThresholds(8.0, 5_000, 20_000, 0.5, 7.5, 2_000)
        )
        val DEFAULT_SERIES = mapOf(
            RatingSource.IMDB to GoldThresholds(8.0, 10_000, 50_000, 0.5, 7.5, 2_500),
            RatingSource.LETTERBOXD to GoldThresholds(4.0, 5_000, 25_000, 0.25, 3.75, 1_000),
            RatingSource.TMDB to GoldThresholds(8.0, 1_500, 8_000, 0.5, 7.5, 800)
        )
    }
}
