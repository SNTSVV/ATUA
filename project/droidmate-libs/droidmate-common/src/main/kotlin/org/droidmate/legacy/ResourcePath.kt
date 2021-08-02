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

import com.google.common.base.MoreObjects
import java.io.IOException
import java.net.URL

import java.nio.file.Path
import java.nio.file.Paths

/**
 * <p>
 * A {@link Path} representing an existing system resource.
 *
 * </p><p>
 * Will throw an {@link IOException} if no URL of the resource can be found or if all of the found URLs are within jar files,
 * not directly in the file system.
 *
 * </p><p>
 *
 * In gradle-based projects, the resources dirs that are searched are:
 * <pre><code>
 *   build/resources/main
 *   build/resources/test
 * </code></pre>
 *
 * These directories are populated during gradle {@code process (Test)Resources} task from following dirs:
 * <pre><code>
 *   [proj_root_dir]/src/main/resources
 *   [proj_root_dir]/src/test/resources
 * </code></pre>
 * so if you want to ensure a resource is available, put it in one of the dirs listed directly above and then rerun
 * {@code process (Test)resources} gradle task.
 *
 * <br/>{@code ==========}
 * </p><p>
 *
 * Usage example: when this class is constructed with a constructor param of:
 *
 * <pre><code>
 *   generated/Monitor.java
 * </code></pre>
 *
 * and the classpath is for the project located in:
 *
 * <pre><code>
 *   [repo_root]\dev\droidmate\projects\monitor-generator
 * </code></pre>
 *
 * then {@code Path} represented by this object will be:
 *
 * <pre><code>
 *   [repo_root]\dev\droidmate\projects\monitor-generator\build\resources\test\generated\Monitor.java
 * </code></pre>
 *
 * </p>
 */
class ResourcePath(pathString: String) {
    val url: URL
    val alternativeUrls: List<URL>

    /**
     * Please see the class doc.
     */
    init {
        val urlsEnum = ClassLoader.getSystemResources(pathString)
        if (!urlsEnum.hasMoreElements())
            throw IOException("No resource URL found for path $pathString")

        val urls = urlsEnum.toList()
        val nonJarUrl = urls.find { it.toString().startsWith("file:") }
        if (nonJarUrl == null) {
            val urlsString = urls.joinToString("\n") { it.toString() }
            throw IOException("No URL found for path $pathString that starts with 'file' protocol. The found URLs:\n$urlsString")
        }

        val path = Paths.get(nonJarUrl.toURI())

        if (!(path.isRegularFile || path.isDirectory))
            throw IOException(
                "Resource named '$path' is not a regular file nor a directory.\n" +
                        "Path to the resource: ${path.toAbsolutePath()}"
            )

        this.url = nonJarUrl
        this.alternativeUrls = urls - nonJarUrl
    }

    val path: Path
        get() = Paths.get(this.url.toURI())

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("path", path)
            .add("url", url)
            .add("alternativeUrls", alternativeUrls)
            .toString()
    }
}