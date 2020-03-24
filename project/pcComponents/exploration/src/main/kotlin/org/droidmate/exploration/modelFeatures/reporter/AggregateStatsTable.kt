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

import com.google.common.collect.Table
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.misc.*

class AggregateStatsTable private constructor(val table: Table<Int, String, String>) : Table<Int, String, String> by table {

	constructor(data: List<ExplorationContext<*,*,*>>) : this(build(data))

	companion object {
		const val headerApkName = "file_name"
		const val headerPackageName = "package_name"
		const val headerExplorationTimeInSeconds = "exploration_seconds"
		const val headerActionsCount = "explorationTrace"
		const val headerResetActionsCount = "in_this_reset_actions"
		const val headerViewsSeenCount = "actionable_unique_views_seen_at_least_once"
		const val headerViewsClickedCount = "actionable_unique_views_clicked_or_long_clicked_at_least_once"
		const val headerApisSeenCount = "unique_apis"
		const val headerEventApiPairsSeenCount = "unique_event_api_pairs"
		const val headerException = "exception"

		fun build(data: List<ExplorationContext<*,*,*>>): Table<Int, String, String> {

			return buildTable(
					headers = listOf(
						headerApkName,
						headerPackageName,
						headerExplorationTimeInSeconds,
						headerActionsCount,
						headerResetActionsCount,
						headerViewsSeenCount,
						headerViewsClickedCount,
						headerApisSeenCount,
						headerEventApiPairsSeenCount,
						headerException
					),
					rowCount = data.size,
					computeRow = { rowIndex ->
						val apkData = data[rowIndex]
						listOf(
								apkData.apk.fileName,
								apkData.apk.packageName,
								apkData.getExplorationDuration().seconds.toString(),
								apkData.explorationTrace.size.toString(),
								apkData.resetActionsCount.toString(),
								apkData.uniqueActionableWidgets.size.toString(),
								apkData.uniqueClickedWidgets.size.toString(),
								apkData.uniqueApis.size.toString(),
								apkData.uniqueEventApiPairs.size.toString(),
								apkData.exceptions.toString()
						)
					}
			)
		}

	}
}