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

package org.droidmate.exploration.modelFeatures.reporter

import kotlinx.coroutines.CoroutineName
import org.droidmate.device.logcat.ApiLogcatMessage
import org.droidmate.deviceInterface.exploration.isLaunchApp
import org.droidmate.deviceInterface.exploration.isPressBack
import org.droidmate.exploration.ExplorationContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

class ActivitySeenSummaryMF(reportDir: Path,
                            resourceDir: Path,
                            val fileName: String = "activitiesSeen.txt") : ApkReporterMF(reportDir, resourceDir) {

    override val coroutineContext: CoroutineContext = CoroutineName("ActivitySeenSummaryMF")

    override suspend fun safeWriteApkReport(context: ExplorationContext<*, *, *>, apkReportDir: Path, resourceDir: Path) {
        val sb = StringBuilder()
        val header = "activity\tcount\n"
        sb.append(header)

        val activitySeenMap = HashMap<String, Int>()
        var lastActivity = ""
        var currActivity = context.apk.launchableMainActivityName

        // Always see the main activity
        activitySeenMap.put(currActivity, 1)

        context.explorationTrace.getActions().forEach { record ->

            if (record.actionType.isPressBack())
                currActivity = lastActivity
            else if (record.actionType.isLaunchApp())
                currActivity = context.apk.launchableMainActivityName

            if (currActivity == "")
                currActivity = "<DEVICE HOME>"

            val logs = record.deviceLogs.map { ApiLogcatMessage.from(it) }

            logs.filter { it.methodName.toLowerCase().startsWith("startactivit") }
                    .forEach { log ->
                        val intent = log.getIntents()
                        // format is: [ '[data=, component=<HERE>]', 'package ]
                        if (intent.isNotEmpty()) {
                            lastActivity = currActivity
                            currActivity = intent[0].substring(intent[0].indexOf("component=") + 10).replace("]", "")
                        }

                        val count = if (activitySeenMap.containsKey(currActivity))
                            activitySeenMap[currActivity]!!
                        else
                            0

                        activitySeenMap[currActivity] = count + 1
                    }
        }

        activitySeenMap.forEach { activity, count ->
            sb.appendln("$activity\t$count")
        }

        val reportFile = apkReportDir.resolve(fileName)
        Files.write(reportFile, sb.toString().toByteArray())
    }

    override fun reset() {
        // Do nothing
        // Nothing to reset here
    }
}