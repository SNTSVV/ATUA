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

import org.droidmate.logging.Markers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

class TimeDiffWithTolerance(private val tolerance: Duration) {

    private val log: Logger by lazy { LoggerFactory.getLogger(TimeDiffWithTolerance::class.java) }

    fun warnIfBeyond(
        start: LocalDateTime,
        end: LocalDateTime,
        startName: String,
        endName: String,
        apkFileName: String
    ): Boolean {
        val startAfterEnd = Duration.between(end, start)
        return if (startAfterEnd > tolerance) {

            val (startNamePadded, endNamePadded) = Pad(startName, endName)
            log.warn(
                Markers.appHealth,
                "For $apkFileName, the expected start time '$startName' is after the expected end time '$endName' by more than the tolerance.\n" +
                        "$startNamePadded : $start\n" +
                        "$endNamePadded : $end\n" +
                        "Tolerance  : ${tolerance.toMillis()} ms\n" +
                        "Difference : ${startAfterEnd.toMillis()} ms"
            )
            true
        } else
            false
    }
}