package com.danielportelabyrne.cinema.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun release(tag: String, asset: String = "cinema-tv.apk", draft: Boolean = false) = """{
        "tag_name": "$tag", "name": "Cinema build 312", "draft": $draft, "prerelease": false,
        "body": "Lighter drawing work for low-end TVs\n\nMeasured on a Chromecast HD.",
        "assets": [
            {"name": "notes.txt", "size": 10, "browser_download_url": "https://github.com/x/notes.txt"},
            {"name": "$asset", "size": 14103198, "browser_download_url": "https://github.com/x/releases/download/$tag/$asset"}
        ]}"""

    @Test fun readsTheBuildItsAppFileAndNotes() {
        val u = UpdateCheck.parse(release("build-312"))!!
        assertEquals(312, u.build)
        assertEquals("Cinema build 312", u.title)
        assertEquals("https://github.com/x/releases/download/build-312/cinema-tv.apk", u.apkUrl)
        assertEquals(14103198L, u.sizeBytes)
        assertEquals("Lighter drawing work for low-end TVs", UpdateCheck.headline(u))
    }

    private fun withNotes(notes: String?) = AvailableUpdate(7, "Cinema build 7", "https://x/cinema-tv.apk", 1L, notes)

    @Test fun changesAreTheNotesAfterTheHeadlineWithoutMarkdownOrTrailers() {
        val u = withNotes("Faster Home\n\n## New\n- **Find services** finds `Tiny Pop`\n* Channels keep their order\n1. Numbered\nhttps://github.com/x/pull/1\nCo-Authored-By: Someone <a@b.c>\n🤖 Generated with a tool\nChannels keep their order")
        assertEquals(listOf("New", "Find services finds Tiny Pop", "Channels keep their order", "Numbered"), UpdateCheck.changes(u))
    }

    @Test fun changesAreBoundedAndEmptyForAHeadlineOnlyOrMissingNotes() {
        assertEquals(2, UpdateCheck.changes(withNotes("H\na\nb\nc\nd"), max = 2).size)
        assertEquals(emptyList<String>(), UpdateCheck.changes(withNotes("Only a headline")))
        assertEquals(emptyList<String>(), UpdateCheck.changes(withNotes(null)))
    }

    @Test fun ignoresAnythingThatIsNotACinemaBuild() {
        assertNull(UpdateCheck.parse(release("v1.0")))
        assertNull(UpdateCheck.parse(release("build-312", asset = "other.apk")))
        assertNull(UpdateCheck.parse(release("build-312", draft = true)))
        assertNull(UpdateCheck.parse("not json"))
    }

    @Test fun onlyAHigherBuildIsNewer() {
        val u = UpdateCheck.parse(release("build-312"))
        assertTrue(UpdateCheck.isNewer(u, 1))
        assertFalse(UpdateCheck.isNewer(u, 312))
        assertFalse(UpdateCheck.isNewer(u, 400))
        assertFalse(UpdateCheck.isNewer(null, 1))
        assertEquals(7, UpdateCheck.buildOf(" build-7 "))
    }
}
