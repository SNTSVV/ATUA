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

package org.droidmate.exploration.strategy.playback

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.exploration.modelFeatures.explorationWatchers.ActionPlaybackFeature
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.explorationModel.retention.loading.ModelParser
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.DefaultModel
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.lang.Integer.max
import java.nio.file.Path

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class Playback constructor(private val modelDir: Path, private val priority: Int = 41) : AExplorationStrategy() {
	override fun getPriority(): Int = priority

	private var traceIdx = 0
	private var actionIdx = 0
	protected lateinit var model : DefaultModel<State<Widget>,Widget>
	private var lastSkipped: Interaction<Widget> = Interaction.empty()
	protected var toExecute: Interaction<Widget> = Interaction.empty()
	lateinit var eContext: ExplorationContext<*,*,*>


	private val watcher: ActionPlaybackFeature by lazy {
		(eContext.findWatcher { it is ActionPlaybackFeature }
				?: ActionPlaybackFeature(model)
						.also { eContext.addWatcher(it) }) as ActionPlaybackFeature
	}

	override fun<M: AbstractModel<S, W>,S: State<W>,W:Widget> initialize(initialContext: ExplorationContext<M,S,W>) {
		super.initialize(initialContext)
		this.eContext = initialContext
		model = runBlocking{ ModelParser.loadModel(ModelConfig(modelDir, eContext.apk.packageName, true)) }
	}

	private fun isComplete(): Boolean {
		return model.getPaths().let { traceIdx+1 == it.size && actionIdx+1 == it[traceIdx].size }
	}

	private suspend fun supposedToBeCrash():Boolean{
		val lastAction = model.getPaths()[traceIdx].getActions()[max(0,actionIdx)]
		return model.getState(lastAction.resState)!!.isAppHasStoppedDialogBox
	}

	private suspend fun getNextTraceAction(peek: Boolean = false): Interaction<Widget> {
		model.let { m ->
			m.getPaths()[traceIdx].let { currentTrace ->
				if (currentTrace.size - 1 == actionIdx) { // check if all actions of this trace were handled
					if(m.getPaths().size == traceIdx + 1) return Interaction.empty()  // this may happen on a peek for next action on the end of the trace
					return m.getPaths()[traceIdx + 1].first().also {
						if (!peek) {
							traceIdx += 1
							actionIdx = 0
						}
					}
				}
				return currentTrace.getActions()[actionIdx].also {
					if (!peek)
						actionIdx += 1
				}
			}
		}
	}


	/** determine if the state is similar enough to execute a back action by computing how many relevant widgets are similar */
	private fun State<Widget>.similarity(other: State<Widget>): Double {
		val otherWidgets = other.widgets
		val candidates = this.visibleTargets
		val mappedWidgets = candidates.map { w ->
			if (otherWidgets.any { it.uid == w.uid || it.configId == w.configId })
				1
			else
				0
		}
		return mappedWidgets.sum() / candidates.size.toDouble()
	}

	/** checking if we can actually trigger the widget of our recorded trace */
	private fun Widget?.canExecute(state: State<Widget>): Pair<Double, Widget?> {
		return when{
			this == null -> Pair(0.0, null) // no match possible
			state.widgets.any { it.id == this.id } -> Pair(1.0, this) // we have a perfect match
			else -> // possibly it is a match but we can't be 100% sure
				state.widgets.find { it.isInteractive && it.uid == this.uid }	?.let { Pair(0.6, it) } // prefer uid match over property equivalence
						?: state.widgets.find { it.isInteractive && it.configId == this.configId }?.let{ Pair(0.5, it) }
						?:	Pair(0.0, null) // no match found
		}
	}

	private suspend fun getNextAction(): ExplorationAction {

		// All traces completed. Finish
		if (isComplete())
			return ExplorationAction.terminateApp()

		val currTraceData = getNextTraceAction()
		toExecute = currTraceData
		val action = currTraceData.actionType
		return when {
			action.isClick() || action.isLongClick()-> {
				val verifyExecutability = currTraceData.targetWidget.canExecute(eContext.getCurrentState())
				if(verifyExecutability.first>0.0) {
					if(action.isClick())
						verifyExecutability.second!!.click()
					else
						verifyExecutability.second!!.longClick()
				}

				// not found, go to the next or try to repeat previous action depending on what is matching better
				else {
					watcher.addNonReplayableActions(traceIdx, actionIdx)
					val prevEquiv = lastSkipped.targetWidget.canExecute(eContext.getCurrentState())  // check if the last skipped action may be appyable now
					val peekAction = getNextTraceAction(peek = true)
					val nextEquiv = peekAction.targetWidget.canExecute(eContext.getCurrentState())
					val explorationAction = if (prevEquiv.first > nextEquiv.first  // try to execute the last previously skipped action only if the next action is executable afterwards
							&& model.getState(lastSkipped.resState)?.run {
									actionableWidgets.any {
										peekAction.targetWidget == null || it.uid == peekAction.targetWidget!!.uid
												|| it.configId == peekAction.targetWidget!!.uid
									}
								}
							 == true) {
						lastSkipped = Interaction.empty()  // we execute it now so do not try to do so again
						if(action.isClick()){
							log.info("trigger previously skipped action")
							prevEquiv.second!!.click()
						}else{
							log.info("trigger previously skipped action")
							prevEquiv.second!!.longClick()
						}
					} else {
						lastSkipped = currTraceData
						println("[skip action ($traceIdx,$actionIdx)] (${eContext.getCurrentState().stateId}) $lastSkipped")
						getNextAction()
					}
					explorationAction
				}
			}
			action.isFetch() -> GlobalAction(ActionType.FetchGUI)
			action.isTerminate() -> ExplorationAction.terminateApp()
			action.isQueueStart() -> {
				// Currently it supports only the launch app queue
				var nextTrace = getNextTraceAction()
				while(!nextTrace.actionType.isQueueEnd()){
					nextTrace = getNextTraceAction()
				}

				eContext.launchApp()
			}
			action.isPressBack() -> {
				// If already in home screen, ignore
				if (eContext.getCurrentState().isHomeScreen) {
					watcher.addNonReplayableActions(traceIdx, actionIdx)
					return getNextAction()
				}

				val similarity = eContext.getCurrentState().similarity( model.getState(currTraceData.resState)!!)

				// Rule:
				// 0 - Doesn't belong to app, skip
				// 1 - Same screen, press back
				// 2 - Not same screen and can execute next widget action, stay
				// 3 - Not same screen and can't execute next widget action, press back
				// Known issues: multiple press back / reset in a row

				if (similarity >= 0.95) {
					ExplorationAction.pressBack()
				} else {
					val nextTraceData = getNextTraceAction(peek = true)

						val nextWidget = nextTraceData.targetWidget

						if (nextWidget.canExecute(eContext.getCurrentState()).first>0.0) {
							watcher.addNonReplayableActions(traceIdx, actionIdx)
							getNextAction()
						}

					ExplorationAction.pressBack()
				}
			}
			else -> {
				currTraceData.targetWidget!!.click()
			}
		}
	}

	/*fun getExplorationRatio(widget: Widget? = null): Double {
		TODO()
//		val totalSize = traces.map { it.getSize(widget) }.sum()
//
//		return traces
//				.map { trace -> trace.getExploredRatio(widget) * (trace.getSize(widget) / totalSize.toDouble()) }
//				.sum()
	}*/


	/** reset the []actionTypeIdx] to the position of the last reset or 0 if none exists
	 * (action 0 should always be a reset to start the app) */
	private fun handleReplayCrash(){
		log.info("handle app crash on replay")
		model.getPaths()[traceIdx].let { trace ->
			var isReset = false
			var i = actionIdx
			while (!isReset && i >= 0) {
				i -= 1
				isReset = trace.getActions()[i].actionType.isLaunchApp()
			}
		}
	}

	override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> computeNextAction(
		eContext: ExplorationContext<M, S, W>
	): ExplorationAction {
		if( !eContext.isEmpty() && eContext.getCurrentState().isAppHasStoppedDialogBox && ! supposedToBeCrash()
			&& !getNextTraceAction(peek = true).actionType.isLaunchApp())
			handleReplayCrash()

		return getNextAction().also{ println("PLAYBACK: $it")}
	}
}