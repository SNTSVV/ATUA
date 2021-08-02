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

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Treats the receiver [String] as a system environment variable pointing to an existing directory
 * in the default [FileSystem].
 *
 * Returns [Path] pointing to this directory.
 *
 * Throws an [IllegalStateException] if any of the assumptions is violated.
 */
val String.asEnvDir: Path
    get() {
        val value = System.getenv(this)

        checkNotNull(value) { "System.getenv($this) should denote a directory. It is instead null." }
        check(value.isNotEmpty()) { "System.getenv($this) should denote a directory. It is instead an empty string." }

        val dir = Paths.get(value)

        check(dir.isDirectory) {
            "System.getenv($this) should be a path pointing to an existing directory. " +
                    "The faulty path: $dir"
        }

        return dir
    }

fun String.removeColumn(column: Int): String {
    return this.lines().map { line ->
        val columns = Regex("\\s*\\S+").findAll(line).map { it.value }

        columns.filterIndexed { index, _ -> index + 1 != column }.joinToString(separator = "")
    }.joinToString(separator = "\n")
}
