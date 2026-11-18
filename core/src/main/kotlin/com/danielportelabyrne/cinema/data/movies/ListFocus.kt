package com.danielportelabyrne.cinema.data.movies

/**
 * Where D-pad focus goes when a list of rows is rebuilt. Rows are named by stable keys (null for a child that is not a row,
 * such as a note), so focus follows the row it was on; when that row is gone it moves to the nearest surviving row below,
 * else the nearest above. Null means nothing survives and the caller picks its own safe default.
 */
object ListFocus {
    fun after(oldKeys: List<String?>, focusedIndex: Int, newKeys: List<String?>): Int? {
        if (focusedIndex !in oldKeys.indices) return null
        fun survivor(i: Int): Int? = oldKeys[i]?.let { k -> newKeys.indexOf(k).takeIf { it >= 0 } }
        survivor(focusedIndex)?.let { return it }
        for (i in focusedIndex + 1 until oldKeys.size) survivor(i)?.let { return it }
        for (i in focusedIndex - 1 downTo 0) survivor(i)?.let { return it }
        return null
    }
}
