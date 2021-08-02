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

import java.io.IOException
import java.lang.IllegalStateException
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class Resource @JvmOverloads constructor(private val name: String, private val allowAmbiguity: Boolean = false) {

    private val url: URL by lazy {
        val urls = ClassLoader.getSystemResources(name).toList()

        if (urls.isEmpty())
            throw IOException("No resource URLs found for path \"$name\"")

        if (!allowAmbiguity && urls.size > 1)
            throw IOException(
                "More than one resource URL found for path $name. " +
                        "The found URLs:\n${urls.joinToString(separator = System.lineSeparator())}"
            )

        urls.first()
    }

    val text: String by lazy {
        url.text
    }

    private fun URL.isJar() = this.protocol == "jar"

    private fun URL.isFile() = this.protocol == "file"

    val path: Path by lazy {
        when {
            url.isJar() -> {
                val connection = url.openConnection() as JarURLConnection
                Paths.get(connection.jarFileURL.toURI())
            }
            url.isFile() -> Paths.get(url.toURI())
            else -> throw IllegalStateException("Cannot get path on a resource whose protocol is not 'file'. " +
                    "The protocol is instead '${url.protocol}'"
            )
        }
    }

    val file: Path by lazy {
        check(!allowAmbiguity) { "check failed: !allowAmbiguity" }
        Paths.get(url.toURI())
    }

    fun <T> withExtractedPath(block: (Path) -> T): T {
        return if (url.isFile()) {
            block(Paths.get(url.toURI()))
        } else {
            val tmpDir = Files.createTempDirectory(name.removeSuffix("/"))
            val extractedPath = extractTo(tmpDir)
            try {
                check(extractedPath.isRegularFile) {
                    ("Failure: extracted path $extractedPath has been deleted while being processed in the " +
                            "'withExtractedPath' block.")
                }
                block(extractedPath)
            } finally {
                Files.delete(extractedPath)
            }
        }
    }

    private fun extractFolderFromJar(): Path {
        val tmpDir = Files.createTempDirectory(name.removeSuffix("/"))
        val jarUrlConnection = url.openConnection() as JarURLConnection
        val jar = jarUrlConnection.jarFile
        jar.entries()
            .asSequence()
            .filter { it.name.startsWith(name) }
            .forEach { entry ->
                val file = tmpDir.resolve(entry.name)
                if (entry.isDirectory) { // if its a directory, create it
                    Files.createDirectories(file)
                } else {
                    val inputStream = jar.getInputStream(entry) // get the input stream
                    Files.write(file, inputStream.readBytes())
                    if (!OS.isWindows) {
                        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr--r--"))
                    }
                }
            }
        jar.close()

        return tmpDir.resolve(name)
    }

    fun extractTo(targetDir: Path): Path {
        val targetFile = targetDir.resolve(name)

        if (url.isFile()) {
            if (Files.isDirectory(path)) {
                Files.createDirectories(targetFile)
                FileSystemsOperations.copyDirContentsRecursivelyToDirInSameFileSystem(path, targetFile)
            } else {
                Files.createDirectories(targetFile.parent)
                Files.deleteIfExists(targetFile)
                Files.copy(url.openStream(), targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            val jarUrlConnection = url.openConnection() as JarURLConnection
            val jarEntry = jarUrlConnection.jarEntry

            if (jarEntry.isDirectory) {
                val tmpDir = extractFolderFromJar()
                Files.createDirectories(targetFile)
                FileSystemsOperations.copyDirContentsRecursivelyToDirInSameFileSystem(tmpDir, targetFile)
                tmpDir.deleteDirectoryRecursively()
            } else {
                Files.createDirectories(targetFile.parent)
                Files.deleteIfExists(targetFile)
                Files.copy(url.openStream(), targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        return targetFile
    }
}