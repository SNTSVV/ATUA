// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.legacy

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.LinkedListMultimap

/**
 * @return
 *   Map of counts of how many times given elements appears in this receiver [Iterable].
 */
val <T> Iterable<T>.frequencies: Map<T, Int> get() {
    val grouped: Map<T, List<T>> = this.groupBy { it }
    val frequencies: Map<T, Int> = grouped.mapValues { it.value.size }
    return frequencies
}

/**
 * @return
 *   A map from unique items to the index of first element in the receiver [Iterable] from which given unique item was
 *   obtained. The indexing starts at 0.
 *
 * @param extractItems
 *   A function that is applied to each element of the receiver iterable, converting it to an iterable of items.
 *
 * @param extractUniqueString
 *   A function used to remove duplicates from all the items extracted from receiver iterable using [extractItems].
 *
 */
fun <T, TItem> Iterable<T>.uniqueItemsWithFirstOccurrenceIndex(
    extractItems: (T) -> Iterable<TItem>,
    extractUniqueString: (TItem) -> String
): Map<TItem, Int> {

    return this.foldIndexed(mapOf<String, Pair<TItem, Int>>()) { index, accumulatedMap, elem ->

        val uniqueStringsToItemsWithIndexes: Map<String, Pair<TItem, Int>> =
            extractItems(elem).associate {
                Pair(
                    extractUniqueString(it),
                    Pair(it, index + 1)
                )
            }

        val newUniqueStrings = uniqueStringsToItemsWithIndexes.keys.subtract(accumulatedMap.keys)

        val uniqueStringsToNewItemsWithIndexes = uniqueStringsToItemsWithIndexes.filterKeys { it in newUniqueStrings }

        accumulatedMap.plus(uniqueStringsToNewItemsWithIndexes)
    }.map { it.value }.toMap()
}

inline fun <T, K, V> Iterable<T>.associateMany(transform: (T) -> Pair<K, V>): Map<K, Iterable<V>> {
    val multimap = ArrayListMultimap.create<K, V>()
    this.forEach { val pair = transform(it); multimap.put(pair.first, pair.second) }
    return multimap.asMap()
}

fun <K, V> Iterable<Map<K, Iterable<V>>>.flatten(): Map<K, Iterable<V>> {
    val multimap = LinkedListMultimap.create<K, V>()
    this.forEach { map ->
        map.forEach { multimap.putAll(it.key, it.value) }
    }
    return multimap.asMap()
}

fun <T> Iterable<T>.truncateAndPrint(max: Int): String {
    var out = this.take(max).joinToString("\n") { it.toString() }
    val count = this.count()
    if (count > max) {
        val remainder = count - max
        assert(remainder > 0)
        val msg = "...(left out $remainder items)"
        out += "\n" + msg
    }

    return out
}

fun <T> Iterable<T>.findSingle(closure: (T) -> Boolean): T {
    return single { closure.invoke(it) }
}

fun <T> Iterable<T>.findSingle(): T {
    assert(this.count() == 1)
    return this.first()
}

fun <T> Iterable<T>.findSingleOrDefault(defaultVal: T): T {
    assert(this.count() <= 1)

    return if (this.count() == 1)
        this.first()
    else
        defaultVal
}

fun <T> Iterable<T>.findSingleOrDefault(defaultVal: T, closure: (T) -> Boolean): T {
    val result = this.filter { closure(it) }
    return result.findSingleOrDefault(defaultVal)
}

fun <T> Iterable<T>.noDuplicates(): Boolean {
    return this.count() == this.distinct().count()
}

fun <T> Iterable<T>.allUnique(): Boolean {
    return this.noDuplicates()
}

fun <T> Iterable<T>.isSubset(right: Iterable<T>): Boolean {
    assert(this.noDuplicates())
    assert(right.noDuplicates())

    val intersectionSet = this.intersect(right)

    return this as Set == intersectionSet
}

fun <T> Iterable<Iterable<T>>.shallowFlatten(): List<T> {
    return this.flatten()
}