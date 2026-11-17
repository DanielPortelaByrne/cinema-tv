package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelOrderTest {
    // Alpha and Beta ran after one another for ten seasons in the 80s; Gamma was on the same night; Delta was the 50s.
    private val data = ScheduleKnowledge.parse("""{"v":1,"seasons":10,
        "shows":[["Alpha Show",1985,1990,6],["Beta Show",1985,1990,6],["Gamma Show",1985,1990,6],["Delta Show",1950,1955,6]],
        "adjacent":[[0,1,10]],"sameNight":[[0,1,10],[0,2,4]]}""")!!

    private fun title(name: String, kind: Kind = Kind.SHOW, year: Int? = null) =
        Title(name, kind, name, year, null, null, emptyList(), null, null, null, null, null, null)

    private fun slot(name: String, kind: Kind = Kind.SHOW, year: Int? = null) = PlaylistSlot(title(name, kind, year))

    @Test fun a_pair_that_ran_together_weighs_more_and_a_stranger_pair_weighs_the_base() {
        val linked = ChannelOrder.weight(data, title("Alpha Show"), title("Beta Show"))
        val sameNight = ChannelOrder.weight(data, title("Alpha Show"), title("Gamma Show"))
        val strangers = ChannelOrder.weight(data, title("Nothing Known"), title("Also Unknown"))
        assertEquals(ChannelOrder.BASE, strangers, 0.0)
        assertTrue(linked > sameNight && sameNight > ChannelOrder.BASE)
        // Damped: ten seasons together is a lot more likely, not certain.
        assertTrue(linked < 25)
        // Either way round.
        assertEquals(linked, ChannelOrder.weight(data, title("Beta Show"), title("Alpha Show")), 1e-9)
    }

    @Test fun a_film_from_the_years_a_series_was_on_air_is_favoured_over_one_from_another_era() {
        val in80s = ChannelOrder.weight(data, title("Alpha Show"), title("Some Film", Kind.MOVIE, 1988))
        val in50s = ChannelOrder.weight(data, title("Alpha Show"), title("Another Film", Kind.MOVIE, 1957))
        assertEquals(ChannelOrder.BASE + ChannelOrder.ERA, in80s, 1e-9)
        assertEquals(ChannelOrder.BASE, in50s, 1e-9)
        // Two titles the data does not know say nothing about an era.
        assertEquals(ChannelOrder.BASE, ChannelOrder.weight(data, title("Film A", Kind.MOVIE, 1988), title("Film B", Kind.MOVIE, 1988)), 0.0)
    }

    @Test fun every_programme_is_in_a_round_exactly_once_and_the_same_seed_gives_the_same_round() {
        val slots = listOf("Alpha Show", "Beta Show", "Gamma Show", "Delta Show", "Unknown One", "Unknown Two").map { slot(it) }
        val round = ChannelOrder.sequence(slots, Random(5), data)
        assertEquals(slots.map { it.id }.toSet(), round.map { it.id }.toSet())
        assertEquals(slots.size, round.size)
        assertEquals(round.map { it.id }, ChannelOrder.sequence(slots, Random(5), data).map { it.id })
        assertEquals(slots.take(1), ChannelOrder.sequence(slots.take(1), Random(1), data))
        assertTrue(ChannelOrder.sequence(emptyList(), Random(1), data).isEmpty())
    }

    @Test fun shows_that_ran_together_follow_each_other_far_more_often_than_chance() {
        val slots = listOf("Alpha Show", "Beta Show", "Filler One", "Filler Two", "Filler Three", "Filler Four").map { slot(it) }
        val alpha = slots[0].id; val beta = slots[1].id
        fun follows(order: List<PlaylistSlot>) = order.zipWithNext().any { (a, b) -> a.id == alpha && b.id == beta }
        val rounds = 3000
        val classic = (0 until rounds).count { follows(ChannelOrder.sequence(slots, Random(it), data)) } / rounds.toDouble()
        val plain = (0 until rounds).count { follows(slots.shuffled(Random(it))) } / rounds.toDouble()
        // A plain shuffle puts Beta straight after Alpha about one time in six.
        assertTrue("plain $plain", plain in 0.10..0.24)
        assertTrue("classic $classic", classic > 0.35)
    }

    @Test fun a_title_the_data_does_not_know_can_still_come_next_and_nothing_is_ever_left_out() {
        val slots = listOf("Alpha Show", "Beta Show", "Filler One", "Filler Two").map { slot(it) }
        val fillers = slots.drop(2).map { it.id }.toSet()
        val afterAlpha = (0 until 2000).mapNotNull { ChannelOrder.sequence(slots, Random(it), data).zipWithNext().firstOrNull { (a, _) -> a.id == slots[0].id }?.second?.id }
        assertTrue(afterAlpha.any { it in fillers })
    }

    @Test fun a_round_does_not_open_with_the_title_that_played_last_when_there_is_a_choice() {
        val slots = listOf("Alpha Show", "Beta Show", "Gamma Show").map { slot(it) }
        repeat(300) { seed ->
            assertFalse(ChannelOrder.sequence(slots, Random(seed), data, lastPlayedTitleKey = "Alpha Show").first().title.key == "Alpha Show")
        }
        // One programme only: nothing else to open with.
        val only = listOf(slot("Alpha Show"))
        assertEquals(only, ChannelOrder.sequence(only, Random(1), data, lastPlayedTitleKey = "Alpha Show"))
    }

    @Test fun the_data_shapes_a_channel_only_when_some_of_its_titles_ran_together() {
        assertTrue(ChannelOrder.shapes(data, listOf("Alpha Show", "Beta Show", "Unknown").map { slot(it) }))
        assertFalse(ChannelOrder.shapes(data, listOf("Unknown One", "Unknown Two", "Alpha Show").map { slot(it) }))
        assertFalse(ChannelOrder.shapes(data, listOf(slot("Alpha Show"))))
    }

    @Test fun a_channel_draws_a_new_order_every_round_and_each_round_has_every_programme() {
        val slots = (1..6).map { slot("Filler $it") } + listOf(slot("Alpha Show"), slot("Beta Show"))
        val queue = BroadcastQueue(Random(11), data)
        assertTrue(queue.classic)
        val rounds = (1..25).map {
            val round = slots.map { _ ->
                val next = queue.next(slots)!!
                queue.completed(PlaylistPlayback("p", next.id, null))
                next.id
            }
            assertEquals("round $it", slots.map { s -> s.id }.toSet(), round.toSet())
            round
        }
        assertTrue("the same order came every time", rounds.toSet().size > 10)
        assertFalse(BroadcastQueue(Random(1)).classic)
    }

    @Test fun the_preview_of_a_classic_channel_is_the_order_that_plays() {
        val slots = listOf("Alpha Show", "Beta Show", "Gamma Show", "Filler").map { slot(it) }
        val queue = BroadcastQueue(Random(3), data)
        val preview = queue.upcoming(slots).map { it.id }
        assertEquals(preview, (1..slots.size).map { queue.next(slots)!!.id })
    }

    @Test fun the_classic_order_setting_is_on_until_switched_off_and_goes_with_the_channel() {
        val platform = MemoryPlatform()
        val store = PlaylistStore(platform)
        val a = store.create("A"); val b = store.create("B")
        assertTrue(store.classicOrder(a.id))
        store.setClassicOrder(a.id, false)
        assertFalse(PlaylistStore(platform).classicOrder(a.id))
        assertTrue(PlaylistStore(platform).classicOrder(b.id))
        store.setClassicOrder(a.id, true)
        assertTrue(store.classicOrder(a.id))
        store.setClassicOrder(b.id, false)
        store.delete(b.id)
        assertTrue(store.classicOrder(b.id))
    }
}
