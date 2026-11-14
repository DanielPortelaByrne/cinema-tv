package com.danielportelabyrne.cinema.data.remote

import org.json.JSONObject
import java.security.SecureRandom

/**
 * The phone remote for Cinema on a TV. Both ends share this file.
 *
 * - Finding TVs: the phone broadcasts [HELLO] on UDP [DISCOVERY_PORT]; each TV running Cinema answers with
 *   [announce] (its name and HTTP port).
 * - Pairing: `POST /pair` makes the TV show a four-digit code on screen; `POST /pair/confirm` with `code=` answers
 *   with a token the phone keeps. Codes last [CODE_MILLIS] and allow [MAX_TRIES] guesses.
 * - Commands: `POST /key` (`key=` one of [Key]), `POST /text` (`text=`), `GET /ping`, each with the token in the
 *   [TOKEN_HEADER] header. Without a known token the TV answers 401.
 *
 * Plain HTTP on the home network, as with typing from a phone: the code on the TV's screen is the gate.
 */
object RemoteProtocol {
    const val PORT = 47800
    const val DISCOVERY_PORT = 47801
    const val HELLO = "CINEMA_REMOTE_HELLO"
    const val TOKEN_HEADER = "X-Cinema-Token"
    const val CODE_MILLIS = 120_000L
    const val MAX_TRIES = 5

    /** What the phone can press. The TV maps each to an Android key code. */
    enum class Key { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, PLAY_PAUSE, REWIND, FORWARD, MENU, SEARCH, DEL }

    fun key(name: String?): Key? = Key.entries.firstOrNull { it.name == name?.trim()?.uppercase() }

    fun announce(name: String, port: Int = PORT): String = JSONObject().put("cinema", "remote").put("name", name).put("port", port).toString()

    data class Announced(val name: String, val port: Int)

    fun parseAnnounce(text: String): Announced? = try {
        val o = JSONObject(text)
        if (o.optString("cinema") != "remote") null else Announced(o.optString("name").ifBlank { "TV" }, o.optInt("port", PORT))
    } catch (_: Exception) { null }

    fun newCode(random: SecureRandom = SecureRandom()): String = (random.nextInt(9000) + 1000).toString()
    fun newToken(random: SecureRandom = SecureRandom()): String = ByteArray(24).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** One pairing attempt on the TV: a code on screen, a deadline and a few guesses. */
    class PairingWindow(val code: String, private val startedAt: Long) {
        private var tries = 0
        fun check(presented: String?, now: Long): Boolean {
            if (now - startedAt > CODE_MILLIS || tries >= MAX_TRIES) return false
            tries++
            return presented?.trim() == code
        }
        fun open(now: Long): Boolean = now - startedAt <= CODE_MILLIS && tries < MAX_TRIES
    }

    /** `a=1&b=two` → map, URL-decoded. */
    fun form(body: String): Map<String, String> = body.split('&').mapNotNull { p ->
        val k = p.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(p.substringAfter('='), "UTF-8")
    }.toMap()

    fun encode(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> java.net.URLEncoder.encode(k, "UTF-8") + "=" + java.net.URLEncoder.encode(v, "UTF-8") }
}
