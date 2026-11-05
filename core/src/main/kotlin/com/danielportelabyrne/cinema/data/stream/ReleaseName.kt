package com.danielportelabyrne.cinema.data.stream

/**
 * Reads quality from a release name or an add-on's stream text ("Sharp.Objects.S01E03.1080p.AMZN.WEB-DL.DDP5.1.H.264").
 * Only what the text says is filled in; anything it does not mention stays UNKNOWN.
 */
object ReleaseName {
    fun parse(text: String, sizeBytes: Long? = null, seeders: Int? = null): StreamMetadata {
        val t = " " + text.replace(Regex("[._\\[\\]()]"), " ") + " "
        fun has(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(t)
        val resolution = when {
            has("\\b(2160p|4k|uhd)\\b") -> Resolution.P2160
            has("\\b1080[pi]\\b") -> Resolution.P1080
            has("\\b720p\\b") -> Resolution.P720
            has("\\b(480p|576p|360p|sd|dvdrip)\\b") -> Resolution.SD
            else -> Resolution.UNKNOWN
        }
        val source = when {
            has("\\b(cam|camrip|hdcam|telesync|hdts|ts)\\b") && !has("\\bweb") -> SourceType.CAM
            has("\\bremux\\b") -> SourceType.REMUX
            has("\\b(blu-?ray|bdrip|brrip|bdremux)\\b") -> SourceType.BLURAY
            has("\\bweb-?dl\\b") -> SourceType.WEB_DL
            has("\\bweb-?rip\\b|\\bweb\\b") -> SourceType.WEBRIP
            has("\\bhdtv\\b") -> SourceType.HDTV
            else -> SourceType.UNKNOWN
        }
        val codec = when {
            has("\\bav1\\b") -> VideoCodec.AV1
            has("\\b(hevc|[hx] ?265)\\b") -> VideoCodec.HEVC
            has("\\b(avc|[hx] ?264)\\b") -> VideoCodec.H264
            else -> VideoCodec.UNKNOWN
        }
        val dv = has("\\b(dv|dovi|dolby ?vision)\\b")
        val range = when {
            dv -> DynamicRange.DOLBY_VISION
            has("\\bhdr10(\\+|plus)") -> DynamicRange.HDR10_PLUS
            has("\\bhdr(10)?\\b") -> DynamicRange.HDR10
            resolution != Resolution.UNKNOWN -> DynamicRange.SDR
            else -> DynamicRange.UNKNOWN
        }
        val audio = when {
            has("\\batmos\\b") -> AudioFormat.ATMOS
            has("\\btrue-?hd\\b") -> AudioFormat.TRUEHD
            has("\\bdts-?(hd|ma|x)\\b") -> AudioFormat.DTS_HD
            has("\\bdts\\b") -> AudioFormat.DTS
            has("\\b(ddp|dd\\+|e-?ac-?3)") -> AudioFormat.EAC3
            has("\\b(ac-?3|dd ?5 ?1|dd)\\b") -> AudioFormat.AC3
            has("\\baac") -> AudioFormat.AAC
            else -> AudioFormat.UNKNOWN
        }
        return StreamMetadata(resolution, source, codec, range, dv && has("\\bhdr"), audio, sizeBytes, null, null, seeders)
    }
}
