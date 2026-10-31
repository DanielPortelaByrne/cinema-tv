package com.danielportelabyrne.cinema.ui.setup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.util.Base64
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinDiscovery
import com.danielportelabyrne.cinema.data.jellyfin.QuickConnectTicket
import com.danielportelabyrne.cinema.data.movies.ClientConfig
import com.danielportelabyrne.cinema.data.movies.QueueClient
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import com.danielportelabyrne.cinema.data.pairing.Pairing
import com.danielportelabyrne.cinema.data.pairing.PairingPages
import com.danielportelabyrne.cinema.data.pairing.PairingRequest
import com.danielportelabyrne.cinema.data.pairing.PairingResponse
import com.danielportelabyrne.cinema.data.pairing.ClientNeed
import com.danielportelabyrne.cinema.data.pairing.PairingServer
import com.danielportelabyrne.cinema.data.pairing.RestoreBundle
import com.danielportelabyrne.cinema.data.pairing.RestoreException
import com.danielportelabyrne.cinema.data.pairing.RestoreFormat
import com.danielportelabyrne.cinema.data.pairing.ServerChoice
import com.danielportelabyrne.cinema.data.pairing.SetupForm
import com.danielportelabyrne.cinema.data.music.SignIn
import com.danielportelabyrne.cinema.data.music.SubsonicApi
import com.danielportelabyrne.cinema.data.music.SubsonicClient
import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.data.settings.ManualArr
import com.danielportelabyrne.cinema.data.stremio.StremioClient
import com.danielportelabyrne.cinema.platform.Log
import io.nayuki.qrcodegen.QrCode
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections

/**
 * The phone pages a pairing can open: setup, restore, and saving the sign-in. Each pairing serves exactly one, plus the
 * small requests that page makes (Quick Connect's code and its check), which is why [routes] lists more than [route].
 */
enum class PhoneKind(val route: String, val routes: Set<String>) {
    SETUP("setup", setOf("setup", "qc-start", "qc-check", "stremio-start", "stremio-check")),
    RESTORE("restore", setOf("restore")),
    SAVE("save", setOf("save")),
}

/** What the TV shows for one pairing: the address the phone opens (it holds the code), and the code on its own. */
class PhoneAddress(val url: String, val code: String)

/**
 * Runs phone pairings for the setup screens. [start] opens a [PairingServer] that answers only the pages for [kind]; the
 * phone submits there, and the TV applies it on the server's thread. [onDone] runs on the main thread once a submission
 * has succeeded, a moment later so the phone's confirmation page arrives before the TV leaves the QR screen.
 * [close] stops the server, and it stops by itself once the code is spent or expires.
 */
class PhonePairing(
    private val jellyfin: JellyfinClient,
    private val seerr: SeerrClient,
    private val settings: CinemaSettings,
    private val ui: Handler,
    private val onDone: (PhoneKind) -> Unit,
    /** The download-queue helper, which finds the torrent client Sonarr and Radarr use and signs in to it. */
    private val queue: () -> QueueClient,
) {
    private var server: PairingServer? = null
    /** The Quick Connect code the phone is waiting on, if it asked for one. */
    private var quickConnect: QuickConnectTicket? = null
    /** Set once the approved code has been traded for a sign-in, so repeat checks do not trade it again. */
    private var quickConnectSignedIn = false
    private val stremio = StremioClient()
    /** The Stremio code the phone is linking with, and whether that link has finished. */
    private var stremioCode: String? = null
    private var stremioLinked = false
    /** The servers the TV found for the setup page, filled in by a background search when the pairing starts. */
    @Volatile private var found: List<ServerChoice> = emptyList()

    /** Opens a pairing for [kind]. Null when the TV has no network address or the port cannot be opened. */
    fun start(kind: PhoneKind): PhoneAddress? {
        close()
        val host = lanAddress() ?: return null
        val pairing = Pairing()
        val running = try {
            PairingServer(pairing, routes = { request -> answer(kind, pairing.code, request) })
        } catch (e: IOException) {
            Log.w(TAG, "pairing could not open a port: ${e.javaClass.simpleName}")
            return null
        }
        server = running
        // Looking for servers takes about a second and a half, so it runs now, while the phone is scanning.
        found = emptyList()
        if (kind == PhoneKind.SETUP) Thread { found = discovered() }.apply { isDaemon = true }.start()
        return PhoneAddress(running.urlFor(host, kind.route), pairing.code)
    }

    fun close() {
        server?.close()
        server = null
        quickConnect = null
        quickConnectSignedIn = false
        stremioCode = null
        stremioLinked = false
    }

    private fun answer(kind: PhoneKind, code: String, request: PairingRequest): PairingResponse {
        if (request.route !in kind.routes) return PairingResponse.text(404, "Not found")
        val submitting = request.method == "POST"
        val helper = request.route.startsWith("qc-") || request.route.startsWith("stremio-")
        if (helper && submitting) return PairingResponse.text(405, "Method not allowed")
        val response = when (request.route) {
            "qc-start" -> quickConnectStart(request.query["address"].orEmpty())
            "qc-check" -> quickConnectCheck()
            "stremio-start" -> stremioStart()
            "stremio-check" -> stremioCheck()
            else -> when (kind) {
                PhoneKind.SETUP -> if (!submitting) PairingResponse.html(200, PairingPages.setup(code, initialForm())) else {
                    if (request.form["phase"] == "clients") clientsPhase(code, request.form) else setUpPhase(code, request.form)
                }
                PhoneKind.RESTORE -> if (!submitting) PairingResponse.html(200, PairingPages.restore(code)) else {
                    restore(request.form)?.let { PairingResponse.html(400, PairingPages.restore(code, it)) }
                        ?: PairingResponse.html(200, PairingPages.done("Restored", "Cinema has restored your settings and sign-in on the TV."))
                }
                PhoneKind.SAVE -> if (!submitting) PairingResponse.html(200, PairingPages.save(code)) else save(code, request.form)
            }
        }
        if (submitting && response.status in 200..299) ui.postDelayed({ onDone(kind) }, FINISH_DELAY_MS)
        return response
    }

    /** The first form: the servers the TV can see, and the Jellyfin address already in use if there is one. */
    private fun initialForm(): SetupForm {
        val found = this.found
        val known = jellyfin.server.takeIf { it.isNotBlank() } ?: found.firstOrNull()?.address
        return SetupForm(jellyfinUrl = jellyfin.server, found = found, seerrSuggestion = known?.let { seerrNear(it) },
            musicSuggestion = known?.let { navidromeNear(it) })
    }

    /** The form again after a problem, with what was typed kept so only the wrong part needs changing. */
    private fun formAfter(form: Map<String, String>, problem: SetupProblem): SetupForm {
        val initial = initialForm()
        return SetupForm(
            jellyfinUrl = form["jellyfinUrl"].orEmpty(),
            username = form["username"].orEmpty(),
            seerrUrl = form["seerrUrl"].orEmpty(),
            seerrUsername = form["seerrUsername"].orEmpty(),
            musicUrl = form["musicUrl"].orEmpty(),
            musicUsername = form["musicUsername"].orEmpty(),
            sonarrUrl = form["sonarrUrl"].orEmpty(),
            radarrUrl = form["radarrUrl"].orEmpty(),
            found = initial.found,
            seerrSuggestion = initial.seerrSuggestion,
            musicSuggestion = initial.musicSuggestion,
            error = problem.message,
            errorField = problem.field,
        )
    }

    private fun discovered(): List<ServerChoice> = try {
        JellyfinDiscovery.find().map { ServerChoice(it.name, it.address) }
    } catch (_: Exception) {
        emptyList()
    }

    /** Seerr usually runs on the same machine as Jellyfin, on port 5055. */
    private fun seerrNear(jellyfinAddress: String): String? =
        runCatching { "http://" + URL(jellyfinAddress).host + ":5055" }.getOrNull()

    /** Navidrome, the usual Subsonic server, runs on the same machine as Jellyfin, on port 4533. */
    private fun navidromeNear(jellyfinAddress: String): String? =
        runCatching { "http://" + URL(jellyfinAddress).host + ":4533" }.getOrNull()

    /**
     * Signs in to Jellyfin, and Seerr when an address is given. Each failure says what was wrong and which field to
     * change. Returns null on success.
     */
    private fun setUp(form: Map<String, String>): SetupProblem? {
        if (form["quickConnect"] == "1") {
            if (!jellyfin.isConnected) return SetupProblem(
                "Quick Connect hasn't been approved yet. In Jellyfin, open Quick Connect and enter the code shown on this page.", "jellyfinUrl")
            quickConnect = null
        } else {
            val address = CinemaSettings.cleanUrl(form["jellyfinUrl"])
                ?: return SetupProblem("Enter the Jellyfin address, such as http://server-address:8096, or tap a server above.", "jellyfinUrl")
            val user = form["username"].orEmpty().trim()
            if (user.isEmpty()) return SetupProblem("Enter your Jellyfin username.", "username")
            if (form["password"].isNullOrEmpty()) return SetupProblem(
                "Enter your Jellyfin password, or use Quick Connect instead. The password is needed for Sign in on the TV.", "password")
            if (!jellyfin.probe(address)) return SetupProblem(
                "Nothing answered at $address. Check the address and port (Jellyfin usually uses 8096), and that this phone is on the same Wi-Fi as the TV.",
                "jellyfinUrl")
            jellyfin.setServer(address)
            if (jellyfin.signInByName(user, form["password"].orEmpty()) != null) return SetupProblem(
                "Jellyfin at $address answered, but didn't accept that username or password. Check both. Passwords are case-sensitive.",
                "password")
        }

        return seerrPart(form) ?: musicPart(form) ?: arrPart(form)
    }

    /** Seerr is optional: a blank address skips it. */
    private fun seerrPart(form: Map<String, String>): SetupProblem? {
        val seerrAddress = CinemaSettings.cleanUrl(form["seerrUrl"]) ?: return null
        val seerrUser = form["seerrUsername"].orEmpty().trim()
        if (seerrUser.isEmpty()) return SetupProblem("Enter your Jellyfin username for Seerr, or clear the Seerr address to skip Seerr.", "seerrUsername")
        if (!seerr.probe(seerrAddress)) return SetupProblem(
            "Nothing answered at $seerrAddress. Check the Seerr address and port (Seerr usually uses 5055).", "seerrUrl")
        seerr.setServer(seerrAddress)
        if (seerr.signIn(seerrUser, form["seerrPassword"].orEmpty()) != null) return SetupProblem(
            "Seerr at $seerrAddress answered, but didn't accept that Jellyfin username or password. Use the same login as Jellyfin.",
            "seerrPassword")
        return null
    }

    /** Music is optional: a blank address skips it. The server is checked before its sign-in is kept. */
    private fun musicPart(form: Map<String, String>): SetupProblem? {
        val raw = form["musicUrl"].orEmpty().trim()
        if (raw.isEmpty()) return null
        val address = SubsonicApi.normalize(raw)
        val user = form["musicUsername"].orEmpty().trim()
        val password = form["musicPassword"].orEmpty()
        val key = form["musicApiKey"].orEmpty().trim()
        if (key.isEmpty() && (user.isEmpty() || password.isEmpty())) return SetupProblem(
            "Enter the music username and password, or an API key.", "musicPassword")
        val config = SubsonicConfig(address, user, password, key)
        return when (SubsonicClient(config).signIn()) {
            SignIn.OK -> {
                settings.subsonic = config
                settings.setOn(Integration.MUSIC, true)
                null
            }
            SignIn.WRONG_CREDENTIALS -> SetupProblem("The music server at $address turned down that username and password. Check both.", "musicPassword")
            SignIn.NEEDS_API_KEY -> SetupProblem("The music server at $address needs an API key. Make one in its settings and enter it here.", "musicApiKey")
            SignIn.NOT_SUBSONIC -> SetupProblem("$address isn't a Subsonic server. Check the address, such as Navidrome's.", "musicUrl")
            SignIn.UNREACHABLE -> SetupProblem("Nothing answered at $address. Check the address and port, and that this phone is on the same Wi-Fi as the TV.", "musicUrl")
        }
    }

    /** Sonarr and Radarr are optional, for when Seerr doesn't share them. An address needs its API key with it. */
    private fun arrPart(form: Map<String, String>): SetupProblem? {
        for ((arr, urlField, keyField) in listOf(
            Triple(Arr.SONARR, "sonarrUrl", "sonarrApiKey"),
            Triple(Arr.RADARR, "radarrUrl", "radarrApiKey"),
        )) {
            val url = CinemaSettings.cleanUrl(form[urlField]) ?: continue
            val key = form[keyField].orEmpty().trim()
            if (key.isEmpty()) return SetupProblem("Enter the ${arr.label} API key. It's in ${arr.label}'s settings, under General.", keyField)
            settings.setManualArr(arr, ManualArr(url, key))
        }
        return null
    }

    /**
     * The main sign-in. When everything it covers worked and the torrent client still needs its sign-in, the answer is
     * 409 so the code is not spent yet; the phone then shows the last step.
     */
    private fun setUpPhase(code: String, form: Map<String, String>): PairingResponse {
        setUp(form)?.let { return PairingResponse.html(400, PairingPages.setup(code, formAfter(form, it))) }
        val needs = clientNeeds()
        if (needs.isEmpty()) return signedInPage()
        return PairingResponse.html(409, PairingPages.clients(code, needs.map { it.first },
            error = "Almost done. Your torrent client needs its sign-in before Cinema can control its downloads."))
    }

    /** The torrent client step. Skipping it, or a good sign-in, finishes setup; a bad one asks again on the same page. */
    private fun clientsPhase(code: String, form: Map<String, String>): PairingResponse {
        val needs = clientNeeds()
        if (form["skipClients"] == "1" || needs.isEmpty()) return signedInPage()
        needs.forEachIndexed { i, (need, config) ->
            val user = form["clientUser$i"].orEmpty().trim()
            val password = form["clientPass$i"].orEmpty()
            if (user.isEmpty() || password.isEmpty()) return PairingResponse.html(400, PairingPages.clients(code, needs.map { it.first },
                error = "Enter the sign-in for ${need.name}, or tap Skip for now.", errorField = if (user.isEmpty()) "clientUser$i" else "clientPass$i"))
            if (!queue().signIn(config, user, password)) return PairingResponse.html(400, PairingPages.clients(code, needs.map { it.first },
                error = "${need.name} turned down that sign-in. Check the username and password, and try again.", errorField = "clientPass$i"))
        }
        return signedInPage()
    }

    private fun signedInPage() = PairingResponse.html(200, PairingPages.done("Signed in", "Cinema is set up on the TV. You can close this page."))

    /** Torrent clients that Sonarr and Radarr use and whose sign-in Cinema still needs (Sonarr hides the password). */
    private fun clientNeeds(): List<Pair<ClientNeed, ClientConfig>> = try {
        queue().detectClients().mapNotNull { found ->
            val config = found.config?.takeIf { queue().needsLogin(it) } ?: return@mapNotNull null
            ClientNeed(found.listed.name, found.listed.host) to config
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Starts linking Stremio: the phone opens Stremio's page, and the TV waits for the sign-in there. */
    private fun stremioStart(): PairingResponse {
        val link = stremio.createLink() ?: return json(JSONObject().put("error", "Couldn't reach Stremio. Check the TV's internet connection and try again."))
        stremioCode = link.code
        stremioLinked = false
        return json(JSONObject().put("link", link.link).put("code", link.code))
    }

    /** Whether the Stremio sign-in has finished. When it has, the key is kept on the TV. */
    private fun stremioCheck(): PairingResponse {
        if (stremioLinked) return json(JSONObject().put("status", "linked"))
        val code = stremioCode ?: return json(JSONObject().put("status", "none").put("message", "Tap Link Stremio first."))
        val key = stremio.readLink(code) ?: return json(JSONObject().put("status", "waiting"))
        settings.stremioKey = key
        stremioLinked = true
        stremioCode = null
        return json(JSONObject().put("status", "linked"))
    }

    /** Asks Jellyfin for a Quick Connect code for [address]. The phone shows it; the TV then waits for approval. */
    private fun quickConnectStart(address: String): PairingResponse {
        val clean = CinemaSettings.cleanUrl(address)
            ?: return json(JSONObject().put("error", "Enter the Jellyfin address first, or tap a server above."))
        var failure: String? = null
        val ticket = jellyfin.startQuickConnect(clean) { failure = it }
            ?: return json(JSONObject().put("error", failure ?: "Quick Connect didn't start."))
        quickConnect = ticket
        quickConnectSignedIn = false
        return json(JSONObject().put("code", ticket.code))
    }

    /** Whether the code has been approved on another device. When it has, the TV signs in before the phone submits. */
    private fun quickConnectCheck(): PairingResponse {
        // Once the TV has signed in, say so again without trading the code a second time: Jellyfin only accepts it once.
        if (quickConnectSignedIn) return json(JSONObject().put("status", "approved"))
        val ticket = quickConnect ?: return json(JSONObject().put("status", "none").put("message", "Get a Quick Connect code first."))
        if (!jellyfin.isApproved(ticket)) return json(JSONObject().put("status", "waiting"))
        if (!jellyfin.finishQuickConnect(ticket)) return json(JSONObject().put("status", "failed")
            .put("message", "Jellyfin approved the code, but the TV couldn't sign in. Get a new code and try again."))
        quickConnectSignedIn = true
        return json(JSONObject().put("status", "approved"))
    }

    /** Opens the uploaded file with its PIN and applies it. Returns the reason to show, or null on success. */
    private fun restore(form: Map<String, String>): String? {
        val bundle = form["bundle"].orEmpty()
        if (bundle.isEmpty()) return "Choose the cinema-restore.json file first."
        val sealed = try { Base64.decode(bundle, Base64.DEFAULT) } catch (_: IllegalArgumentException) {
            return "That isn't a Cinema restore file."
        }
        val pin = pinDigits(form)
        if (pin.length != PIN_LENGTH) return "Enter the 6-digit PIN."
        return try {
            val plain = RestoreBundle.open(sealed, pin)
            RestoreFormat.apply(String(plain, Charsets.UTF_8), settings, jellyfin, seerr)
            importStremioWatchlistIfEmpty()
            null
        } catch (e: RestoreException) {
            e.message ?: "That file could not be restored."
        }
    }

    /**
     * A restored TV that has Stremio linked but no watchlist yet fills it from the Stremio library, the same films the
     * Import button would add (the ones not watched yet), so nobody has to press Import again. Runs off the request.
     */
    private fun importStremioWatchlistIfEmpty() {
        val key = settings.stremioKey ?: return
        if (settings.watchlist.isNotEmpty()) return
        Thread {
            val films = runCatching { stremio.films(key) }.getOrNull() ?: return@Thread
            settings.addToWatchlist(films.filter { !it.watched }.map {
                com.danielportelabyrne.cinema.data.movies.WatchlistFilm(it.imdbId, it.name, it.year, it.poster)
            })
        }.apply { isDaemon = true }.start()
    }

    /** Seals the current settings and sign-ins with the PIN, and sends them as the download. */
    private fun save(code: String, form: Map<String, String>): PairingResponse {
        val pin = pinDigits(form)
        if (pin.length != PIN_LENGTH) {
            return PairingResponse.html(400, PairingPages.save(code, "The PIN is 6 digits. Type all six."))
        }
        val plain = RestoreFormat.build(settings, jellyfin, seerr).toByteArray(Charsets.UTF_8)
        return PairingResponse.file(FILE_NAME, RestoreBundle.seal(plain, pin))
    }

    private fun json(body: JSONObject) = PairingResponse(200, "application/json; charset=utf-8", body.toString().toByteArray(Charsets.UTF_8))

    /** The PIN as typed, with the spaces the page adds taken out. */
    private fun pinDigits(form: Map<String, String>): String = form["pin"].orEmpty().filter { it.isDigit() }

    /** A problem with a sign-in: the reason to show, and the form field it is about. */
    private class SetupProblem(val message: String, val field: String)

    companion object {
        private const val TAG = "PhonePairing"
        const val FILE_NAME = "cinema-restore.json"
        /** The restore PIN is always six digits. */
        private const val PIN_LENGTH = 6
        /** Long enough for the phone to receive its confirmation before the TV leaves the QR screen. */
        private const val FINISH_DELAY_MS = 1500L
    }
}

/** The TV's address on the home network, for the phone to open. Null when there is no such address (Wi-Fi off). */
fun lanAddress(): String? = try {
    NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses) }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
} catch (_: Exception) {
    null
}

/** A QR code drawn as a bitmap, with a white quiet zone, so the TV can show it without a web service. */
object PhoneQr {
    fun bitmap(text: String, modulePx: Int = 12): Bitmap {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val border = 4
        val side = (qr.size + border * 2) * modulePx
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK }
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (!qr.getModule(x, y)) continue
            val left = (x + border) * modulePx
            val top = (y + border) * modulePx
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + modulePx).toFloat(), (top + modulePx).toFloat(), paint)
        }
        return bitmap
    }
}
