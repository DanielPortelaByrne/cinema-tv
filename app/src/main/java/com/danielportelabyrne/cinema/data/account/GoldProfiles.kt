package com.danielportelabyrne.cinema.data.account

/**
 * Whether a person has found Gold Mode, and whether it is on for them. This is the seam the persistence and sync
 * story plugs into; until then [session] keeps it in memory for as long as Cinema is open.
 */
interface GoldProfiles {
    fun isUnlocked(userId: String): Boolean
    fun isEnabled(userId: String): Boolean
    fun unlock(userId: String)
    fun setEnabled(userId: String, on: Boolean)

    /** What a completed 3-second hold did. */
    enum class Held { UNLOCKED, ENABLED, DISABLED }

    /**
     * A completed hold on [userId]: the first one unlocks and turns Gold Mode on (the ceremony); later ones only
     * flip it on or off.
     */
    fun held(userId: String): Held {
        if (!isUnlocked(userId)) { unlock(userId); setEnabled(userId, true); return Held.UNLOCKED }
        val on = !isEnabled(userId)
        setEnabled(userId, on)
        return if (on) Held.ENABLED else Held.DISABLED
    }

    companion object {
        /** In memory, shared by every "Who's watching?" this run of Cinema shows. */
        val session: GoldProfiles = InMemoryGoldProfiles()

        /** Saved on this device, per person, so Gold Mode is still found after Cinema is closed and reopened. */
        fun saved(context: android.content.Context): GoldProfiles {
            val prefs = context.applicationContext.getSharedPreferences("gold_profiles", android.content.Context.MODE_PRIVATE)
            return StoredGoldProfiles(object : GoldFlagStore {
                override fun get(key: String) = prefs.getBoolean(key, false)
                override fun put(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
            })
        }
    }
}

/** Where [StoredGoldProfiles] keeps its flags: a string-keyed set of booleans (SharedPreferences on the device). */
interface GoldFlagStore {
    fun get(key: String): Boolean
    fun put(key: String, value: Boolean)
}

/**
 * Gold Mode flags saved per person (by Jellyfin user id) so they survive closing Cinema. Two separate flags per person,
 * `goldUnlocked` and `goldEnabled`; nothing stored reads as locked and off, and "on" without "unlocked" is treated as off.
 */
class StoredGoldProfiles(private val store: GoldFlagStore) : GoldProfiles {
    private fun unlockedKey(userId: String) = "gold.unlocked.$userId"
    private fun enabledKey(userId: String) = "gold.enabled.$userId"

    @Synchronized override fun isUnlocked(userId: String) = store.get(unlockedKey(userId))
    @Synchronized override fun isEnabled(userId: String) = isUnlocked(userId) && store.get(enabledKey(userId))
    @Synchronized override fun unlock(userId: String) = store.put(unlockedKey(userId), true)
    @Synchronized override fun setEnabled(userId: String, on: Boolean) = store.put(enabledKey(userId), on && isUnlocked(userId))
}

class InMemoryGoldProfiles : GoldProfiles {
    private val unlocked = mutableSetOf<String>()
    private val enabled = mutableSetOf<String>()

    @Synchronized override fun isUnlocked(userId: String) = userId in unlocked
    @Synchronized override fun isEnabled(userId: String) = userId in enabled
    @Synchronized override fun unlock(userId: String) { unlocked += userId }
    @Synchronized override fun setEnabled(userId: String, on: Boolean) { if (on && userId in unlocked) enabled += userId else enabled -= userId }
}
