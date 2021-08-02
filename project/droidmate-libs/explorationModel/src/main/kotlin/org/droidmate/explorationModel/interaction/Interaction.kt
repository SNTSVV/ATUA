/*
 * Copyright (c) 2019.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.explorationModel.interaction

import org.droidmate.deviceInterface.communication.TimeFormattedLogMessageI
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.emptyId
import org.droidmate.explorationModel.retention.StringCreator
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

typealias DeviceLog = TimeFormattedLogMessageI
typealias DeviceLogs = List<DeviceLog>

@Suppress("DataClassPrivateConstructor")
/**
 * @actionId used to map this interaction to the coresponding result screenshot, which is named according to this value
 * @meta do not rely on this parameter as it may be removed or the content changed at any time, it is intended for debugging purposes only
 */
open class Interaction<out W: Widget> (
		@property:Persistent("Action", 1) val actionType: String,
		@property:Persistent("Interacted Widget", 2, PType.ConcreteId) val targetWidget: W?,
		@property:Persistent("StartTime", 4, PType.DateTime) val startTimestamp: LocalDateTime,
		@property:Persistent("EndTime", 5, PType.DateTime) val endTimestamp: LocalDateTime,
		@property:Persistent("SuccessFul", 6, PType.Boolean) val successful: Boolean,
		@property:Persistent("Exception", 7) val exception: String,
		@property:Persistent("Source State", 0, PType.ConcreteId) val prevState: ConcreteId,
		@property:Persistent("Resulting State", 3, PType.ConcreteId) val resState: ConcreteId,
		@property:Persistent("Action-Id", 9, PType.Int) val actionId: Int,
		@property:Persistent("Data", 8) val data: String = "",
		val deviceLogs: DeviceLogs = emptyList(),
		@Suppress("unused") val meta: String = "") {

	constructor(res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, target: W?)
			: this(actionType = res.action.name, targetWidget = target,
			startTimestamp = res.startTimestamp, endTimestamp = res.endTimestamp, successful = res.successful,
			exception = res.exception, prevState = prevStateId, resState = resStateId, data = computeData(res.action),
			deviceLogs = res.deviceLogs,	meta = res.action.id.toString(), actionId = res.action.id)

	/** used for ActionQueue entries */
	constructor(action: ExplorationAction, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId, target: W?)
			: this(action.name, target, res.startTimestamp,
			res.endTimestamp, successful = res.successful, exception = res.exception, prevState = prevStateId,
			resState = resStateId, data = computeData(action), deviceLogs = res.deviceLogs, actionId = action.id)

	/** used for ActionQueue start/end Interaction */
	internal constructor(actionName:String, res: ActionResult, prevStateId: ConcreteId, resStateId: ConcreteId)
			: this(actionName, null, res.startTimestamp,
			res.endTimestamp, successful = res.successful, exception = res.exception, prevState = prevStateId,
			resState = resStateId, deviceLogs = res.deviceLogs, actionId = res.action.id)

	/** used for parsing from string */
	constructor(actionType: String, target: W?, startTimestamp: LocalDateTime, endTimestamp: LocalDateTime,
	            successful: Boolean, exception: String, resState: ConcreteId, prevState: ConcreteId, data: String = "", actionId: Int)
			: this(actionType = actionType, targetWidget = target, startTimestamp = startTimestamp, endTimestamp = endTimestamp,
			successful = successful, exception = exception, prevState = prevState, resState = resState, data = data, actionId = actionId)


	/**
	 * (used to measure overhead for new exploration strategies)
	 */
	val decisionTime: Long by lazy { ChronoUnit.MILLIS.between(startTimestamp, endTimestamp) }

	companion object {

		@JvmStatic val actionTypeIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction<*>::actionType }
		@JvmStatic val widgetIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction<*>::targetWidget }
		@JvmStatic val resStateIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction<*>::resState }
		@JvmStatic val srcStateIdx = StringCreator.actionProperties.indexOfFirst { it.property == Interaction<*>::prevState }

		@JvmStatic
		fun computeData(e: ExplorationAction):String = when(e){
			is TextInsert -> e.text
			is Swipe -> "${e.start.first},${e.start.second} TO ${e.end.first},${e.end.second}"
			is RotateUI -> e.rotation.toString()
			is CallIntent -> "action = ${e.action};category = ${e.category};activity = ${e.activityName};uriString = ${e.uriString}"
			else -> ""
		}

		@JvmStatic
		fun<T: Widget> empty() =
			Interaction<T>("EMPTY", null, LocalDateTime.MIN, LocalDateTime.MIN, true,
					"root action", emptyId, prevState = emptyId, actionId = -1)

	}

	override fun toString(): String {
		@Suppress("ReplaceSingleLineLet")
		return "$actionType: widget[${targetWidget?.let { it.toString() }}]:\n$prevState->$resState"
	}

	@Suppress("unused")
	fun copy(prevState: ConcreteId, resState: ConcreteId): Interaction<W>
		= Interaction(actionType = actionType, targetWidget = targetWidget, startTimestamp = startTimestamp,
			endTimestamp = endTimestamp, successful = successful, exception = exception,
			prevState = prevState, resState = resState, data = data, deviceLogs = deviceLogs, meta = meta, actionId = actionId)
}
