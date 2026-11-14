package com.danielportelabyrne.cinema.design.cinema

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Regenerates `app/src/main/res/raw/splash_jingle.wav` (16-bit stereo) from [SplashJingleSynth]. Skipped unless asked for:
 * `RENDER_SPLASH_JINGLE=1 ./gradlew :app:testDebugUnitTest --tests '*SplashJingleRender'`.
 */
class SplashJingleRender {
    @Test fun render() {
        assumeTrue(System.getenv("RENDER_SPLASH_JINGLE") == "1")
        File(SHIPPED).apply { parentFile.mkdirs() }.writeBytes(wav(SplashJingleSynth.samples()))
    }

    companion object {
        const val SHIPPED = "src/main/res/raw/splash_jingle.wav"

        fun wav(pcm: ShortArray): ByteArray {
            val data = pcm.size * 2
            val out = ByteArrayOutputStream(44 + data)
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.write("RIFF".toByteArray()); le32(36 + data); out.write("WAVEfmt ".toByteArray()); le32(16); le16(1); le16(2)
            le32(SplashJingleSynth.RATE); le32(SplashJingleSynth.RATE * 4); le16(4); le16(16)
            out.write("data".toByteArray()); le32(data)
            pcm.forEach { le16(it.toInt()) }
            return out.toByteArray()
        }
    }
}
