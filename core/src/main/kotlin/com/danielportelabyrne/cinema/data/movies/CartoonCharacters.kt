package com.danielportelabyrne.cinema.data.movies

/**
 * The best-known characters of the classic cartoons and the animated series, for anyone to be on Cinema as, whatever
 * their favourites are. Each is (the character's name, their Wikipedia article); the picture is the one at the top of
 * that article (see CharacterArt.pictures), so a character whose article has no picture is simply left out.
 */
object CartoonCharacters {
    class Show(val name: String, val characters: List<Pair<String, String>>)
    class Found(val show: String, val characters: List<Pair<String, String>>)   // (name, picture address)

    val SHOWS = listOf(
        Show("Looney Tunes", listOf(
            "Bugs Bunny" to "Bugs Bunny", "Daffy Duck" to "Daffy Duck", "Porky Pig" to "Porky Pig", "Tweety" to "Tweety",
            "Sylvester" to "Sylvester the Cat", "Taz" to "Tasmanian Devil (Looney Tunes)", "Marvin the Martian" to "Marvin the Martian")),
        Show("Disney", listOf(
            "Mickey Mouse" to "Mickey Mouse", "Minnie Mouse" to "Minnie Mouse", "Donald Duck" to "Donald Duck",
            "Goofy" to "Goofy", "Pluto" to "Pluto (Disney)", "Winnie-the-Pooh" to "Winnie-the-Pooh")),
        Show("Tom and Jerry", listOf("Tom" to "Tom Cat", "Jerry" to "Jerry Mouse")),
        Show("Scooby-Doo", listOf(
            "Scooby-Doo" to "Scooby-Doo (character)", "Shaggy" to "Shaggy Rogers", "Velma" to "Velma Dinkley",
            "Daphne" to "Daphne Blake", "Fred" to "Fred Jones (Scooby-Doo)")),
        Show("Hanna-Barbera", listOf(
            "Fred Flintstone" to "Fred Flintstone", "Barney Rubble" to "Barney Rubble", "Yogi Bear" to "Yogi Bear",
            "George Jetson" to "George Jetson")),
        Show("Classics", listOf(
            "Popeye" to "Popeye", "The Pink Panther" to "Pink Panther (character)", "Betty Boop" to "Betty Boop",
            "Felix the Cat" to "Felix the Cat", "Woody Woodpecker" to "Woody Woodpecker", "Snoopy" to "Snoopy",
            "Charlie Brown" to "Charlie Brown", "Garfield" to "Garfield (character)")),
        Show("SpongeBob SquarePants", listOf(
            "SpongeBob" to "SpongeBob SquarePants (character)", "Patrick" to "Patrick Star", "Squidward" to "Squidward Tentacles",
            "Mr. Krabs" to "Mr. Krabs", "Sandy" to "Sandy Cheeks")),
        Show("Avatar: The Last Airbender", listOf(
            "Aang" to "Aang", "Katara" to "Katara (Avatar: The Last Airbender)", "Zuko" to "Zuko", "Toph" to "Toph Beifong",
            "Sokka" to "Sokka", "Iroh" to "Iroh")),
        Show("The Simpsons", listOf(
            "Homer" to "Homer Simpson", "Marge" to "Marge Simpson", "Bart" to "Bart Simpson", "Lisa" to "Lisa Simpson",
            "Maggie" to "Maggie Simpson", "Mr. Burns" to "Mr. Burns", "Krusty" to "Krusty the Clown")),
        Show("Family Guy", listOf(
            "Peter" to "Peter Griffin", "Stewie" to "Stewie Griffin", "Brian" to "Brian Griffin", "Lois" to "Lois Griffin",
            "Quagmire" to "Glenn Quagmire")),
        Show("Futurama", listOf(
            "Bender" to "Bender (Futurama)", "Fry" to "Philip J. Fry", "Leela" to "Leela (Futurama)",
            "Professor Farnsworth" to "Professor Farnsworth", "Zoidberg" to "Zoidberg")),
        Show("South Park", listOf(
            "Cartman" to "Eric Cartman", "Kenny" to "Kenny McCormick", "Stan" to "Stan Marsh", "Kyle" to "Kyle Broflovski")),
        Show("More series", listOf(
            "Rick" to "Rick Sanchez", "Morty" to "Morty Smith", "Finn" to "Finn the Human", "Jake" to "Jake the Dog",
            "Hank Hill" to "Hank Hill", "Bob Belcher" to "Bob Belcher", "Tommy Pickles" to "Tommy Pickles")),
        Show("Anime", listOf(
            "Pikachu" to "Pikachu", "Ash" to "Ash Ketchum", "Goku" to "Goku", "Sailor Moon" to "Sailor Moon (character)",
            "Naruto" to "Naruto Uzumaki", "Luffy" to "Monkey D. Luffy")),
    )

    @Volatile private var found: List<Found>? = null

    /** Blocking: every show with the characters that have a picture. Kept once found; empty when Wikipedia is out of reach. */
    fun find(): List<Found> {
        found?.let { return it }
        val pictures = CharacterArt.pictures(SHOWS.flatMap { s -> s.characters.map { it.second } })
        val out = SHOWS.mapNotNull { s ->
            s.characters.mapNotNull { (name, article) -> pictures[article]?.let { name to it } }.takeIf { it.isNotEmpty() }?.let { Found(s.name, it) }
        }
        if (out.isNotEmpty()) found = out
        return out
    }
}
