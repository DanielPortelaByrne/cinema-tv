package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutsTest {
    @Test fun a_saved_layout_comes_back_as_it_was() {
        val home = listOf(Section(SectionType.MY_LIBRARY), Section(SectionType.TOP10, "svc:nfx"), Section(SectionType.GENRE, "hrr:films"))
        val back = PageLayouts.parse(PageLayouts.encode(mapOf(LayoutPage.HOME to home)))
        assertEquals(home, back[LayoutPage.HOME])
        assertEquals(PageLayouts.defaults(LayoutPage.FILMS), PageLayouts.of(LayoutPage.FILMS, back))
    }

    @Test fun unknown_rows_are_dropped_not_fatal() {
        val back = PageLayouts.parse("""{"home":["NOPE","TOP10|all","TOP10|all"]}""")
        assertEquals(listOf(Section(SectionType.TOP10, "all")), back[LayoutPage.HOME])
        assertTrue(PageLayouts.parse("not json").isEmpty())
    }

    @Test fun search_finds_a_top_10_of_a_service_and_skips_rows_already_there() {
        val found = PageLayouts.search("top 10 netflix", emptyList()).map { it.label }
        assertEquals(listOf("Top 10 on Netflix"), found)
        assertTrue(PageLayouts.search("my library", listOf(Section(SectionType.MY_LIBRARY))).none { it.section.type == SectionType.MY_LIBRARY })
        assertTrue(PageLayouts.search("horror films", emptyList()).any { it.label == "Horror films" })
    }

    @Test fun every_option_has_a_name_and_catalogue_options_ask_for_rows() {
        PageLayouts.options().forEach { o ->
            assertTrue(o.label.isNotBlank())
            if (o.section.type.catalogue && o.section.type != SectionType.DOCS_ON_FOLLOWED) assertTrue(o.label, PageLayouts.specs(o.section).isNotEmpty())
        }
    }
}
