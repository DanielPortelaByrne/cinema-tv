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
    }
}

class InMemoryGoldProfiles : GoldProfiles {
    private val unlocked = mutableSetOf<String>()
    private val enabled = mutableSetOf<String>()

    @Synchronized override fun isUnlocked(userId: String) = userId in unlocked
    @Synchronized override fun isEnabled(userId: String) = userId in enabled
    @Synchronized override fun unlock(userId: String) { unlocked += userId }
    @Synchronized override fun setEnabled(userId: String, on: Boolean) { if (on && userId in unlocked) enabled += userId else enabled -= userId }
}
