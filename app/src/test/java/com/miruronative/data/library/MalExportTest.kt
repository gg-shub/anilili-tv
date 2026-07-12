package com.miruronative.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalExportTest {
    @Test
    fun writesMalCompatibleXmlAndEscapesTitles() {
        val file = MalExport.fromEntries(
            username = "A&B",
            entries = listOf(
                MalExportEntry(
                    malId = 1,
                    title = "Cowboy & Bebop",
                    format = "TV",
                    episodes = 26,
                    watchedEpisodes = 26,
                    score = 9,
                    status = "Completed",
                ),
            ),
            skippedCount = 2,
        )

        assertEquals(1, file.exportedCount)
        assertEquals(2, file.skippedCount)
        assertTrue(file.xml.contains("<user_name>A&amp;B</user_name>"))
        assertTrue(file.xml.contains("<series_animedb_id>1</series_animedb_id>"))
        assertTrue(file.xml.contains("<series_title>Cowboy &amp; Bebop</series_title>"))
        assertTrue(file.xml.contains("<my_status>Completed</my_status>"))
        assertTrue(file.fileName.endsWith(".xml"))
    }

    @Test
    fun mapsAniListStatusesToMalLabels() {
        assertEquals("Watching" to false, MalExport.statusFromAniList("CURRENT"))
        assertEquals("Watching" to true, MalExport.statusFromAniList("REPEATING"))
        assertEquals("On-Hold" to false, MalExport.statusFromAniList("PAUSED"))
        assertEquals("Plan to Watch" to false, MalExport.statusFromAniList("PLANNING"))
    }
}
