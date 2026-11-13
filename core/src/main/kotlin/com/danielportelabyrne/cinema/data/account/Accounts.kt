package com.danielportelabyrne.cinema.data.account

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Whose personal files are in use. Each personal store (watchlist, layouts, favourites, history, picks…) is a key-value
 * file; with a person chosen, the platform keeps a copy of each one per person (see [fileName]). Set once a person is
 * chosen, before any personal store is opened.
 */
object ProfileScope {
    /** The Jellyfin user whose files are in use, or null before accounts exist (everything stays as it always was). */
    @Volatile var userId: String? = null
        private set

    /** The person whose data was on this TV before accounts: their files start as the unscoped ones. */
    @Volatile var legacyOwner: Boolean = true
        private set

    @Volatile var loaded: Boolean = false
        private set

    /** The key-value files that belong to a person rather than to the TV. */
    val PERSONAL: Set<String> = setOf(
        "cinema_personal", "favourites", "play_history", "picks", "continue_dismissed", "letterboxd_follow",
        "continue_watching", "resume_positions", "watchlist_view"
    )

    /** Which of [PERSONAL] travel to the person's other TVs. Continue watching holds this TV's own artwork paths. */
    val SYNCED: List<String> = PERSONAL.filter { it != "continue_watching" }.sorted()

    fun set(userId: String?, legacyOwner: Boolean) { this.userId = userId; this.legacyOwner = legacyOwner; loaded = true }

    /** The file [name] is for the person [userId], whoever is in use. */
    fun fileNameFor(name: String, userId: String): String = if (name in PERSONAL) "$name~${userId.filter { it.isLetterOrDigit() }.take(40)}" else name

    /** The file [name] really is for the person in use. */
    fun fileName(name: String): String {
        val id = userId ?: return name
        return if (name in PERSONAL) "$name~${id.filter { it.isLetterOrDigit() }.take(40)}" else name
    }
}

/** A person on Cinema: a Jellyfin user, and the TV launcher's accounts that mean them (kept as hashes, never addresses). */
data class CinemaAccount(val userId: String, val name: String, val launcherIds: Set<String> = emptySet(), val avatar: String? = null) {
    val initial: String get() = name.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
}

/** The launcher's chosen person, as it told us: an id (its account's address) and a display name. */
data class LauncherPerson(val id: String, val name: String?)

/**
 * The people on this TV, which one is in use, and each one's Jellyfin sign-in (the token, in the secret store). Kept in
 * a file of its own that belongs to the TV, never to a person.
 */
class AccountStore(platform: Platform) {
    private val prefs = platform.preferences("accounts")
    private val secrets = platform.secretStore()

    fun all(): List<CinemaAccount> = parse(prefs.getString("list", null))

    fun active(): CinemaAccount? = prefs.getString("active", null)?.let { id -> all().firstOrNull { it.userId == id } }

    /** The first person, whose data predates accounts. */
    val originalUserId: String? get() = prefs.getString("original", null)

    fun token(userId: String): String? = secrets.get("account_token_$userId")

    /** Adds or updates [account]; the first one ever added owns what was on the TV before. */
    fun upsert(account: CinemaAccount, token: String?) {
        val list = all().filterNot { it.userId == account.userId } + account
        val e = prefs.edit().putString("list", encode(list))
        if (originalUserId == null) e.putString("original", account.userId)
        e.commit()
        if (token != null) secrets.put("account_token_${account.userId}", token)
    }

    /** The character [userId] picked to be on Cinema (see Avatars), or null for their initial. */
    fun setAvatar(userId: String, avatar: String?) { all().firstOrNull { it.userId == userId }?.let { upsert(it.copy(avatar = avatar), null) } }

    fun rename(userId: String, name: String) { all().firstOrNull { it.userId == userId }?.let { upsert(it.copy(name = name), null) } }

    fun remove(userId: String) {
        prefs.edit().putString("list", encode(all().filterNot { it.userId == userId })).apply()
        secrets.put("account_token_$userId", null)
        if (prefs.getString("active", null) == userId) prefs.edit().remove("active").commit()
    }

    /** Makes [userId] the person in use, from the next personal file opened. */
    fun activate(userId: String) {
        prefs.edit().putString("active", userId).commit()
        applyScope()
    }

    /** Points [ProfileScope] at the person in use. */
    fun applyScope() {
        val id = active()?.userId
        ProfileScope.set(id, id == null || id == originalUserId)
    }

    /** Who the launcher's [person] is on Cinema: the account linked to it, else the one with the same first name. */
    fun forLauncher(person: LauncherPerson): CinemaAccount? {
        val all = all()
        val hash = hash(person.id)
        val first = firstName(person.name)
        // A link stands only while the names agree: someone who picked Daniel once to get past "Who is Eva?" did not
        // make Eva into Daniel for good.
        all.firstOrNull { hash in it.launcherIds && (first == null || firstName(it.name) == first || generic(it.name)) }?.let { return it }
        first ?: return null
        return all.filter { firstName(it.name) == first }.singleOrNull()
    }

    /**
     * Remembers that the launcher's [person] is [userId] (and no one else). A person whose Jellyfin name says nothing about
     * them ("admin") takes the launcher's name for them instead ("Daniel (UK)" gives "Daniel").
     */
    fun link(userId: String, person: LauncherPerson) {
        val hash = hash(person.id)
        val better = person.name?.substringBefore('(')?.trim()?.takeIf { it.isNotEmpty() }
        val list = all().map { a ->
            if (a.userId == userId) a.copy(launcherIds = a.launcherIds + hash, name = if (better != null && generic(a.name)) better else a.name)
            else a.copy(launcherIds = a.launcherIds - hash)
        }
        prefs.edit().putString("list", encode(list)).commit()
    }

    companion object {
        /** Whether picking [account] for the launcher's [person] should be remembered: their names agree (or Cinema's is a server's). */
    fun sameName(person: LauncherPerson, account: CinemaAccount): Boolean =
        person.name == null || firstName(person.name) == firstName(account.name) || generic(account.name)

    /** A server's account name rather than a person's. */
        fun generic(name: String): Boolean = name.trim().lowercase() in setOf("admin", "administrator", "root", "user", "jellyfin", "me", "someone", "owner")

        fun firstName(name: String?): String? = name?.trim()?.split(Regex("[^\\p{L}]+"))?.firstOrNull { it.isNotEmpty() }?.lowercase()

        fun hash(id: String): String = MessageDigest.getInstance("SHA-256").digest(id.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)

        fun parse(json: String?): List<CinemaAccount> = try {
            val a = JSONArray(json ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val links = o.optJSONArray("launcher")?.let { l -> (0 until l.length()).map { l.optString(it) }.filter { it.isNotBlank() }.toSet() }.orEmpty()
                CinemaAccount(id, o.optString("name").ifBlank { "Someone" }, links, o.optString("avatar").ifBlank { null })
            }
        } catch (_: Exception) { emptyList() }

        fun encode(list: List<CinemaAccount>): String = JSONArray(list.map {
            JSONObject().put("id", it.userId).put("name", it.name).put("launcher", JSONArray(it.launcherIds.sorted())).put("avatar", it.avatar ?: "")
        }).toString()
    }
}

/**
 * A person's personal files as one snapshot, so they can be kept on their Jellyfin user and picked up on another TV.
 * Only strings, numbers and true/false are carried; the personal files hold nothing else.
 */
object ProfileSync {
    /** Jellyfin keeps each value as one text field: a snapshot is split into pieces this long. */
    const val CHUNK = 8000

    fun snapshot(platform: Platform): String {
        val root = JSONObject()
        ProfileScope.SYNCED.forEach { name ->
            val file = JSONObject()
            platform.preferences(name).all.forEach { (k, v) ->
                when (v) {
                    is String -> file.put(k, JSONArray().put("s").put(v))
                    is Boolean -> file.put(k, JSONArray().put("b").put(v))
                    is Long -> file.put(k, JSONArray().put("l").put(v))
                    is Int -> file.put(k, JSONArray().put("i").put(v))
                }
            }
            root.put(name, file)
        }
        return root.toString()
    }

    /** Replaces the personal files with [snapshot]'s. A file the snapshot does not mention is left alone. */
    fun apply(platform: Platform, snapshot: String): Boolean = try {
        val root = JSONObject(snapshot)
        ProfileScope.SYNCED.filter { root.has(it) }.forEach { name ->
            val file = root.getJSONObject(name)
            val e = platform.preferences(name).edit().clear()
            file.keys().forEach { k ->
                val pair = file.getJSONArray(k)
                when (pair.getString(0)) {
                    "s" -> e.putString(k, pair.getString(1))
                    "b" -> e.putBoolean(k, pair.getBoolean(1))
                    "l" -> e.putLong(k, pair.getLong(1))
                    "i" -> e.putInt(k, pair.getInt(1))
                }
            }
            e.commit()
        }
        true
    } catch (_: Exception) { false }

    /** [snapshot] as Jellyfin custom preferences: its pieces, how many, and when it was taken. */
    fun toPrefs(snapshot: String, at: Long): Map<String, String> {
        val pieces = snapshot.chunked(CHUNK)
        return pieces.mapIndexed { i, p -> "cinema.$i" to p }.toMap() + mapOf("cinema.n" to pieces.size.toString(), "cinema.at" to at.toString())
    }

    /** The snapshot and its time back from Jellyfin's custom preferences, or null when there is none or it is incomplete. */
    fun fromPrefs(prefs: Map<String, String>): Pair<String, Long>? {
        val n = prefs["cinema.n"]?.toIntOrNull() ?: return null
        val at = prefs["cinema.at"]?.toLongOrNull() ?: return null
        val pieces = (0 until n).map { prefs["cinema.$it"] ?: return null }
        return pieces.joinToString("") to at
    }
}
