package org.droidmate.legacy

class FirstMatchFirstGroup(val target: String, vararg regexps: String) {
    val value: String

    init {
        require(regexps.isNotEmpty())

        val firstGroups = regexps.map(::Regex)
            .filter { it.containsMatchIn(target) }
            .map { it.find(target)!! }
            .filter { it.groupValues.size >= 2 }
            .map { it.groupValues[1] }
            .filter(String::isNotEmpty)

        if (firstGroups.isEmpty())
            throw Exception("None of the regexps matches the target with a non-empty first group. " +
                    "Target: $target Regexps: ${regexps.joinToString()}")

        value = firstGroups.first()
        check(value.isNotEmpty())
    }
}
