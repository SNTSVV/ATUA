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
package org.droidmate.misc

import java.io.File
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

val Path.text: String
    get() {
        return Files.readAllLines(this).joinToString(System.lineSeparator())
    }

fun Path.deleteDir(): Boolean {
    return try {
        if (Files.exists(this))
            Files.walk(this, FileVisitOption.FOLLOW_LINKS)
                .toList()
                .sorted()
                .reversed()
                .forEach { Files.delete(it) }
        true
    } catch (e: IOException) {
        false
    }
}

fun Path.withExtension(extension: String): Path {
    require(!Files.isDirectory(this))
    return this.resolveSibling(File(this.fileName.toString()).nameWithoutExtension + "." + extension)
}

fun FileSystem.dir(dirName: String): Path {
    val dir = this.getPath(dirName)
    Files.createDirectories(dir)
    return dir
}
