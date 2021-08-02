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

package org.droidmate.device.android_sdk

import org.droidmate.logging.Markers
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Apk constructor(
    internalPath: Path,
    override val packageName: String,
    override val applicationLabel: String
) : IApk {

    companion object {
        private const val serialVersionUID: Long = 1
        private val log by lazy { LoggerFactory.getLogger(Apk::class.java) }

        private const val dummyVal = "DUMMY"
        private val dummyApk = Apk(
            Paths.get("./dummy.apk"),
            dummyVal,
            dummyVal
        )

        @JvmStatic
        fun fromFile(path: Path): Apk {
            assert(Files.isRegularFile(path))

            val packageName: String
            val applicationLabel: String
            try {
                val data = AaptWrapper().getMetadata(path)
                packageName = data[0]
                applicationLabel = data[1]
            } catch (e: LaunchableActivityNameProblemException) {
                log.warn(Markers.appHealth, "! While getting metadata for $path, got an: $e Returning null apk.")
                assert(e.isFatal)
                return dummyApk
            }

            return Apk(path, packageName, applicationLabel)
        }
    }

    override val path: Path = internalPath.toAbsolutePath()
    override val fileName = path.fileName.toString()
    override val fileNameWithoutExtension = path.toFile().nameWithoutExtension
    override var launchableMainActivityName: String = ""

    init {
        assert(fileName.isNotEmpty(), fileName::toString)
        assert(fileName.endsWith(".apk"), fileName::toString)
        assert(packageName.isNotEmpty(), packageName::toString)

        if (this.applicationLabel.isEmpty()) {
            log.warn("Unable to determine label label for apk $packageName ($fileName)")
        }
    }

    override val inlined: Boolean = this.fileName.endsWith("-inlined.apk")

    override val instrumented: Boolean = this.fileName.endsWith("-instrumented.apk")

    override val isDummy: Boolean = this.packageName == dummyVal

    override fun toString(): String = this.fileName
}
