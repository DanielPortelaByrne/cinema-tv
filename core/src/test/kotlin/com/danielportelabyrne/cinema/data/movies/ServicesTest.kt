package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServicesTest {
    @Before fun setUp() { Catalog.country = "GB"; Catalog.followed = Catalog.STREAMERS }
    @After fun tearDown() { Catalog.country = "US"; Catalog.followed = Catalog.STREAMERS }

    private fun pk(code: String, name: String, vararg kinds: String) =
        """{"shortName":"$code","clearName":"$name","icon":"/icon/1/{profile}/$code.{format}","monetizationTypes":[${kinds.joinToString(",") { "\"$it\"" }}]}"""
    private fun answer(vararg p: String) = """{"data":{"packages":[${p.joinToString(",")}]}}"""

    private val adultSwim = Service("ads", "Adult Swim", "/icon/1/s166/ads.png", "US")
    private val hulu = Service("hlu", "Hulu", null, "US")
    private val everything = Catalog.KNOWN + listOf(adultSwim, hulu, Service("skg", "Sky Go", null), Service("bba", "BBC America", null, "US"),
        Service("tbv", "Tubi TV", null), Service("sst", "SkyShowtime", null, "SE"), Service("cru", "Crunchyroll", null))

    // --- What is followed

    @Test fun theOriginalSixAreFollowedUntilTheOwnerChooses() {
        assertNull(FollowedServices.parse(null))
        assertEquals(listOf("Netflix", "HBO Max", "Paramount+", "Prime Video", "Disney+", "Apple TV+"), FollowedServices.DEFAULT.map { it.label })
    }

    @Test fun followingAndUnfollowingKeepOrderAndNeverDuplicate() {
        val one = FollowedServices.follow(FollowedServices.DEFAULT, adultSwim)
        assertEquals("ads", one.last().code)
        assertEquals(one, FollowedServices.follow(one, adultSwim))
        assertTrue(FollowedServices.isFollowing(one, "ads"))
        val two = FollowedServices.unfollow(one, "nfx")
        assertFalse(FollowedServices.isFollowing(two, "nfx")); assertEquals(6, two.size)
        assertEquals(emptyList<Service>(), FollowedServices.unfollow(listOf(hulu), "hlu"))
    }

    @Test fun whatIsFollowedSurvivesBeingKeptAndAKnownServiceComesBackAsTheAppsOwn() {
        val kept = FollowedServices.follow(FollowedServices.follow(FollowedServices.DEFAULT, Catalog.BBC), adultSwim)
        val back = FollowedServices.parse(FollowedServices.toJson(kept))!!
        assertEquals(kept, back)
        assertTrue(back.first { it.code == "bbc" }.madeIn.isNotEmpty())   // BBC iPlayer keeps its home-grown rows
        assertEquals(adultSwim, back.last())
        assertEquals(emptyList<Service>(), FollowedServices.parse("[]"))   // following nothing is a choice too
        assertNull(FollowedServices.parse("not json"))
    }

    // --- Finding services

    @Test fun readsOnlyThePlacesYouCanWatchWithoutRentingOrBuying() {
        val got = ServiceDirectory.parsePackages(answer(pk("nfx", "Netflix", "FLATRATE"), pk("amz", "Amazon Video", "BUY", "RENT"), pk("odc", "ODEON Cinemas", "CINEMA"),
            pk("tbv", "Tubi TV", "ADS", "FREE"), pk("rte", "RTE Player", "ADS")), "IE")
        assertEquals(listOf("Netflix", "Tubi TV", "RTÉ Player"), got.map { it.name })
        assertEquals("/icon/1/s166/nfx.png", got.first().icon)
        assertEquals("IE", got.first().country)
        assertTrue(ServiceDirectory.parsePackages("nope", "GB").isEmpty())
    }

    @Test fun aServiceThatRunsInSeveralCountriesIsListedOnceWhereTheViewerIsIfAnywhere() {
        val byCountry = mapOf(
            "GB" to listOf(ListedService("tbv", "Tubi TV", null, "GB"), ListedService("bbc", "BBC iPlayer", null, "GB")),
            "US" to listOf(ListedService("tbv", "Tubi TV", null, "US"), ListedService("ads", "Adult Swim", "/i.png", "US")),
            "JP" to listOf(ListedService("hlu", "Hulu", null, "JP")))
        val all = ServiceDirectory.merge(byCountry, "GB")
        assertEquals(1, all.count { it.code == "tbv" })
        assertNull(all.first { it.code == "tbv" }.country)                       // runs here
        assertEquals("US", all.first { it.code == "ads" }.country)               // only runs there: its pages are asked for there
        assertEquals("JP", all.first { it.code == "hlu" }.country)
        assertEquals(Catalog.BBC, all.first { it.code == "bbc" })                // the app's own definition
        // For a viewer in the US the same service runs in their own country, so its country is left unset.
        assertNull(ServiceDirectory.merge(byCountry, "US").first { it.code == "ads" }.country)
    }

    @Test fun searchFindsByNameWordPrefixAndWhatPeopleCallIt() {
        fun find(q: String) = ServiceDirectory.search(everything, q, "GB").map { it.label }
        assertEquals("Adult Swim", find("adult swim").first())
        assertEquals("Adult Swim", find("adult").first())
        assertEquals("Adult Swim", find("ADULT  SWIM ").first())
        assertEquals("BBC iPlayer", find("bbc").first())
        assertTrue(find("bbc").contains("BBC America"))
        assertEquals("ITVX", find("itv").first())
        assertFalse("Tubi TV is not an itv", find("itv").contains("Tubi TV"))
        assertEquals("Channel 4", find("e4").first())
        assertEquals("Channel 4", find("film4").first())
        assertEquals("RTÉ Player", find("rte").first())
        assertEquals("RTÉ Player", find("RTÉ").first())
        assertEquals(listOf("Sky Go", "SkyShowtime"), find("sky").filter { it.startsWith("Sky") })
        assertEquals("Crunchyroll", find("crunch").first())
        assertEquals("Disney+", find("disney").first())
        assertTrue(find("cctv").isEmpty())
        assertTrue(find("   ").isEmpty())
    }

    @Test fun searchPutsWhatRunsInTheViewersCountryFirstAmongEqualMatches() {
        val here = Service("a1", "Acme TV", null); val away = Service("a2", "Acme TV", null, "US")
        assertEquals(listOf(here, away), ServiceDirectory.search(listOf(away, here), "acme", "GB"))
    }

    @Test fun saysWhereAServiceRuns() {
        assertEquals("Streams in your country", ServiceDirectory.whereLine(Catalog.NETFLIX, "GB"))
        assertEquals("Runs in United States", ServiceDirectory.whereLine(adultSwim, "GB"))
        assertEquals("Streams in your country", ServiceDirectory.whereLine(adultSwim, "US"))
    }

    // --- What following does to the pages

    @Test fun anAddedServiceGetsAPageAskedForInItsOwnCountry() {
        val page = Catalog.servicePage(adultSwim)
        assertEquals("Top 10 on Adult Swim", page[0].heading)
        page.forEach { spec ->
            val v = JSONObject(Catalog.requestBody(spec)).getJSONObject("variables")
            assertEquals(spec.heading, "US", v.getString("c"))
            assertEquals("ads", v.getJSONObject("f").getJSONArray("packages").getString(0))
        }
        assertEquals("Popular on Adult Swim", Catalog.cardShelf(adultSwim).heading)
        assertEquals("US", JSONObject(Catalog.requestBody(Catalog.cardShelf(adultSwim))).getJSONObject("variables").getString("c"))
        // A service that runs here is asked for here, as before.
        assertEquals("GB", JSONObject(Catalog.requestBody(Catalog.servicePage(Service("skg", "Sky Go", null))[1])).getJSONObject("variables").getString("c"))
    }

    @Test fun homeShowsAPopularRowForEachFollowedServiceAndMixedRowsUseThoseThatRunHere() {
        Catalog.followed = listOf(Catalog.NETFLIX, Catalog.BBC, adultSwim, Service("skg", "Sky Go", null))
        val home = Catalog.homePage()
        assertEquals(listOf("Popular on Netflix", "Popular on BBC iPlayer", "Popular on Adult Swim", "Popular on Sky Go"), home.map { it.heading }.filter { it.startsWith("Popular on") })
        assertEquals(listOf("nfx", "skg"), home.first().packages)   // Top 10 today: only what runs in the viewer's country
        assertEquals(listOf("GB", "US"), home.filter { it.heading == "Popular on BBC iPlayer" || it.heading == "Popular on Adult Swim" }
            .map { JSONObject(Catalog.requestBody(it)).getJSONObject("variables").getString("c") })
    }

    @Test fun followingNothingStillLeavesTheMixedRowsSomethingToShow() {
        Catalog.followed = emptyList()
        assertEquals(4, Catalog.homePage().size)
        assertEquals(6, Catalog.homePage().first().packages.size)
    }

    @Test fun homeNeverGrowsEndlessWhateverIsFollowed() {
        Catalog.followed = (1..30).map { Service("s$it", "S$it", null) }
        assertEquals(4 + 8, Catalog.homePage().size)
    }

    @Test fun theSettingKeepsWhatIsFollowed() {
        val s = com.danielportelabyrne.cinema.data.settings.CinemaSettings(com.danielportelabyrne.cinema.platform.MemoryPlatform())
        assertEquals(FollowedServices.DEFAULT, s.followedServices)
        s.followedServices = listOf(Catalog.BBC, adultSwim)
        assertEquals(listOf(Catalog.BBC, adultSwim), s.followedServices)
        s.followedServices = emptyList()
        assertEquals(emptyList<Service>(), s.followedServices)
    }
}
