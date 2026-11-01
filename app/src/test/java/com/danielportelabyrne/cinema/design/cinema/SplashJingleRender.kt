package com.danielportelabyrne.cinema.design.cinema

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Regenerates `app/src/main/res/raw/splash_jingle.wav` from [SplashJingleSynth]. Skipped unless asked for:
 * `RENDER_SPLASH_JINGLE=1 ./gradlew :app:testDebugUnitTest --tests '*SplashJingleRender'`.
 */
class SplashJingleRender {
    @Test fun render() {
        assumeTrue(System.getenv("RENDER_SPLASH_JINGLE") == "1")
        val pcm = SplashJingleSynth.samples()
        val bytes = pcm.size * 2
        val out = File("src/main/res/raw/splash_jingle.wav").apply { parentFile.mkdirs() }
        DataOutputStream(FileOutputStream(out)).use { o ->
            fun le32(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            o.writeBytes("RIFF"); le32(36 + bytes); o.writeBytes("WAVEfmt "); le32(16); le16(1); le16(1)
            le32(SplashJingleSynth.RATE); le32(SplashJingleSynth.RATE * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(bytes)
            pcm.forEach { le16(it.toInt()) }
        }
    }
}
