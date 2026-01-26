package com.hippo.ehviewer.ui.main

import kotlin.math.min

fun <T> reorderDense(
    items: List<T>,
    columns: Int,
    spanProvider: (T) -> Int,
): List<Int> {
    val count = items.size
    val taken = BooleanArray(count)
    val result = ArrayList<Int>(count)
    var currentColumn = 0

    var nextFree = 0
    while (result.size < count) {
        while (nextFree < count && taken[nextFree]) {
            nextFree++
        }
        if (nextFree >= count) break

        val item = items[nextFree]
        val itemSpan = spanProvider(item)
        val remaining = columns - currentColumn

        if (itemSpan <= remaining) {
            result.add(nextFree)
            taken[nextFree] = true
            currentColumn = (currentColumn + itemSpan) % columns
        } else {
            var filled = false
            val lookAheadLimit = 100
            val end = min(count, nextFree + lookAheadLimit)

            for (candidate in (nextFree + 1) until end) {
                if (!taken[candidate]) {
                    val candSpan = spanProvider(items[candidate])
                    if (candSpan <= remaining) {
                        result.add(candidate)
                        taken[candidate] = true
                        currentColumn = (currentColumn + candSpan) % columns
                        filled = true
                        break
                    }
                }
            }

            if (!filled) {
                result.add(nextFree)
                taken[nextFree] = true
                currentColumn = itemSpan % columns
            }
        }
    }
    return result
}
