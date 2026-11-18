package com.danielportelabyrne.cinema.data.movies

/**
 * Ready-made channels that recreate an era of television: a network's well-known programming block, as the series that
 * were in it. These are **era-inspired**, not official schedules: the blocks and years are common knowledge of what the
 * network was known for, with no claim to an exact night-by-night line-up. No logos or media ship with them; a lineup is
 * only ever filled from series the viewer's own library already holds.
 */
object ClassicLineups {
    /** One programme; [aliases] are other names a library may file it under. */
    data class Programme(val name: String, val aliases: List<String> = emptyList()) {
        val names: List<String> get() = listOf(name) + aliases
    }

    data class Lineup(
        val id: String,
        val name: String,
        /** Network and block, e.g. "ABC · Friday night". */
        val block: String,
        val era: String,
        val programmes: List<Programme>
    ) {
        /** The line shown under the name in a list: "ABC · Friday night · 1989–1997". */
        val summary: String get() = "$block · $era"
    }

    /** The viewer's series that belong to a lineup ([found], in the lineup's order) and the programmes they lack. */
    class Match<T>(val found: List<T>, val missing: List<String>)

    private fun p(name: String, vararg aliases: String) = Programme(name, aliases.toList())

    val all: List<Lineup> = listOf(
        Lineup("us-abc-tgif", "TGIF", "ABC · Friday night", "1989–1997", listOf(
            p("Full House"), p("Family Matters"), p("Step by Step"), p("Perfect Strangers"), p("Boy Meets World"),
            p("Hangin' with Mr. Cooper", "Hanging with Mr. Cooper"), p("Sabrina the Teenage Witch"), p("Dinosaurs"))),
        Lineup("us-nbc-must-see", "Must See TV", "NBC · Thursday night", "1990s", listOf(
            p("Cheers"), p("Seinfeld"), p("Friends"), p("Frasier"), p("ER"), p("Mad About You"), p("Wings"), p("Will & Grace"))),
        Lineup("us-saturday-morning", "Saturday morning cartoons", "US networks · Saturday morning", "1990s", listOf(
            p("Animaniacs"), p("Tiny Toon Adventures"), p("Pinky and the Brain"), p("Batman: The Animated Series", "Batman The Animated Series"),
            p("X-Men: The Animated Series", "X-Men"), p("Spider-Man: The Animated Series", "Spider-Man"), p("Gargoyles"))),
        Lineup("us-nickelodeon-90s", "Nickelodeon", "Nickelodeon · after school", "1990s", listOf(
            p("Doug"), p("Rugrats"), p("The Ren & Stimpy Show", "Ren & Stimpy"), p("Rocko's Modern Life"), p("Hey Arnold!"),
            p("Aaahh!!! Real Monsters", "Aaahh Real Monsters"), p("Clarissa Explains It All"), p("All That"), p("Kenan & Kel"))),
        Lineup("us-cartoon-network-cartoon-cartoons", "Cartoon Cartoons", "Cartoon Network · afternoons", "1996–2002", listOf(
            p("Dexter's Laboratory"), p("Johnny Bravo"), p("Cow and Chicken"), p("I Am Weasel"), p("The Powerpuff Girls", "Powerpuff Girls"),
            p("Ed, Edd n Eddy"), p("Courage the Cowardly Dog")))
    )

    fun find(id: String): Lineup? = all.firstOrNull { it.id == id }

    /**
     * The items of [library] (named by [nameOf]) that are in [lineup], each programme once and in the lineup's order,
     * and the programmes none of them is. A name matches only when it is the same after [ScheduleKnowledge.normalise], so
     * "Cosby Show" never matches by accident on a longer title.
     */
    fun <T> match(lineup: Lineup, library: List<T>, nameOf: (T) -> String): Match<T> {
        val byName = HashMap<String, T>()
        library.forEach { item -> byName.putIfAbsent(ScheduleKnowledge.normalise(nameOf(item)), item) }
        val found = ArrayList<T>()
        val missing = ArrayList<String>()
        for (programme in lineup.programmes) {
            val item = programme.names.firstNotNullOfOrNull { byName[ScheduleKnowledge.normalise(it)] }
            if (item == null) missing += programme.name
            else if (found.none { it === item }) found += item
        }
        return Match(found, missing)
    }
}
