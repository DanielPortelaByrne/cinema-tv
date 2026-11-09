package com.danielportelabyrne.cinema.data.live

/** Official service home pages, not guessed channel deep links. Android launches the installed TV app if present. */
object LiveProviders {
    private val rte = LiveProvider("RTÉ Player", "https://www.rte.ie/player/", listOf("air.RTE.OSMF.Minimal"), note = "Most live programming is restricted to Ireland; international rights vary")
    private val virgin = LiveProvider("Virgin Media Play", "https://www.virginmediatelevision.ie/player/", note = "Irish territorial rights apply")
    private val tg4 = LiveProvider("TG4 Player", "https://www.tg4.ie/en/player/watch-live/", note = "Worldwide programming where rights permit; some programmes restricted")
    private val bbc = LiveProvider("BBC iPlayer", "https://www.bbc.co.uk/iplayer", listOf("bbc.iplayer.android", "com.bbc.iplayer"), note = "UK account and TV Licence required")
    private val itv = LiveProvider("ITVX", "https://www.itv.com/", listOf("air.ITVMobilePlayer"), note = "UK account; device and regional availability vary")
    private val four = LiveProvider("Channel 4", "https://www.channel4.com/", listOf("com.channel4.ondemand"), note = "UK account; live channels depend on TV device support")
    private val five = LiveProvider("5", "https://www.channel5.com/", listOf("com.mobileiq.demand5"))
    private val u = LiveProvider("U", "https://u.co.uk/", listOf("com.uktv.android"), note = "UK service; live channels depend on device support")
    private val globe = LiveProvider("Globoplay", "https://globoplay.globo.com/", listOf("com.globo.globoplay"), true, "International packages and live Globo rights vary by territory")
    private val sbt = LiveProvider("+SBT", "https://mais.sbt.com.br/", note = "Official live service; regional rights apply")
    private val record = LiveProvider("Record / PlayPlus", "https://www.playplus.com/", subscription = true, note = "Account/package and territorial rights apply")
    private val band = LiveProvider("Bandplay", "https://bandplay.com/", note = "Official live service; regional rights apply")
    private val brasil = LiveProvider("TV Brasil", "https://tvbrasil.ebc.com.br/webtv/")
    private val brasilInt = LiveProvider("TV Brasil Internacional", "https://tvbrasilinternacional.ebc.com.br/", note = "Official global 24-hour linear service")
    private val swim = LiveProvider("Adult Swim Marathons", "https://www.adultswim.com/streams", note = "Official marathons; UK playback unverified. These are not the cable-channel simulcast")
    private val cbc = LiveProvider("CBC Gem", "https://gem.cbc.ca/", listOf("ca.cbc.android.cbctv"), note = "Live CBC is restricted to Canada; account may be required")
    private val ctv = LiveProvider("CTV", "https://www.ctv.ca/", listOf("ca.bellmedia.ctv"), true, "Canadian TV provider sign-in and territory restrictions apply")
    private val global = LiveProvider("Global TV", "https://www.globaltv.com/", listOf("com.shawmedia.smglobal"), true, "Canadian TV provider sign-in and territory restrictions apply")
    private data class Entry(val id: String, val name: String, val country: String, val category: String, val provider: LiveProvider)
    private val entries = buildList {
        fun add(id: String, name: String, country: String, p: LiveProvider, cat: String = "entertainment") { add(Entry(id, name, country, cat, p)) }
        add("RTEOne.ie", "RTÉ One", "IE", rte); add("RTE2.ie", "RTÉ2", "IE", rte)
        add("VirginMediaOne.ie", "Virgin Media One", "IE", virgin); add("VirginMediaTwo.ie", "Virgin Media Two", "IE", virgin); add("VirginMediaThree.ie", "Virgin Media Three", "IE", virgin)
        add("TG4.ie", "TG4", "IE", tg4)
        add("RTEjr.ie", "RTÉjr", "IE", rte, "animation"); add("RTENews.ie", "RTÉ News", "IE", rte, "news")
        add("VirginMediaFour.ie", "Virgin Media Four", "IE", virgin)
        add("BBCOne.uk", "BBC One", "UK", bbc); add("BBCTwo.uk", "BBC Two", "UK", bbc)
        add("ITV1.uk", "ITV1", "UK", itv); add("ITV2.uk", "ITV2", "UK", itv)
        add("Channel4.uk", "Channel 4", "UK", four); add("E4.uk", "E4", "UK", four, "comedy"); add("Film4.uk", "Film4", "UK", four, "movies")
        add("Channel5.uk", "5", "UK", five); add("UDave.uk", "U&Dave", "UK", u, "comedy")
        add("RedeGlobo.br", "TV Globo", "BR", globe); add("SBT.br", "SBT", "BR", sbt); add("Record.br", "Record", "BR", record); add("Band.br", "Band", "BR", band)
        add("TVBrasil.br", "TV Brasil", "BR", brasil); add("TVBrasilInternacional.br", "TV Brasil Internacional", "BR", brasilInt)
        add("TVCultura.br", "TV Cultura", "BR", LiveProvider("TV Cultura", "https://cultura.uol.com.br/aovivo/", note = "Official player; external-player permission has not been established"))
        add("AdultSwim.us", "Adult Swim marathons", "US", swim, "animation")
        listOf("ComedyCentral" to "Comedy Central", "CartoonNetwork" to "Cartoon Network", "FX" to "FX", "FXX" to "FXX", "AMC" to "AMC", "TCM" to "TCM").forEach { (id, name) ->
            val website = when (id) { "ComedyCentral" -> "https://www.cc.com/"; "CartoonNetwork" -> "https://www.cartoonnetwork.com/"; "FX", "FXX" -> "https://www.fxnetworks.com/"; "AMC" -> "https://www.amc.com/"; else -> "https://www.tcm.com/" }
            add("$id.us", name, "US", LiveProvider(name, website, subscription = true, note = "No authorised UK simulcast found. US account/provider and territory eligibility required"), if (id in listOf("AMC", "TCM")) "movies" else if (id == "CartoonNetwork") "animation" else "comedy")
        }
        add("CBCTelevision.ca", "CBC", "CA", cbc); add("CTV.ca", "CTV", "CA", ctv); add("GlobalTelevisionNetwork.ca", "Global", "CA", global)
        add("cinema.pluto", "Pluto TV · free live channels", "US", LiveProvider("Pluto TV", "https://pluto.tv/", listOf("tv.pluto.android"), note = "UK live comedy, films and other channels; different from the premium target networks"), "movies")
        add("cinema.plex", "Plex · free live channels", "US", LiveProvider("Plex", "https://watch.plex.tv/live-tv", listOf("com.plexapp.android"), note = "Provider-programmed live channels; no personal library needed"), "movies")
        add("DW.de", "DW · English", "DE", LiveProvider("DW", "https://www.dw.com/en/live-tv/channel-english", note = "Official English-language international live broadcast"), "news")
        add("France24.fr", "France 24 · English", "FR", LiveProvider("France 24", "https://www.france24.com/en/live", note = "Official English-language international live broadcast"), "news")
        add("NHKWorldJapan.jp", "NHK WORLD-JAPAN", "JP", LiveProvider("NHK WORLD-JAPAN", "https://www3.nhk.or.jp/nhkworld/en/live_tv/", note = "International English-language news, culture and documentaries"), "documentary")
    }
    fun forChannel(id: String): LiveProvider? = entries.firstOrNull { it.id == id }?.provider
    fun seeds(): List<LiveChannel> = entries.map { LiveChannel(it.id, it.name, it.country, if (it.id.startsWith("cinema.")) listOf("movies", "comedy", "animation", "entertainment") else listOf(it.category),
        aliases = when (it.id) { "UDave.uk" -> listOf("Dave"); "RedeGlobo.br" -> listOf("Globo", "Rede Globo"); "RTEOne.ie" -> listOf("RTE One"); "RTE2.ie" -> listOf("RTE2"); else -> emptyList() },
        provider = it.provider, alternative = it.id.startsWith("cinema.")) }
}
