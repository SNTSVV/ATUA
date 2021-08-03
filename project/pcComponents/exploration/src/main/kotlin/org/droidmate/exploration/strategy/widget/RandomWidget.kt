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

package org.droidmate.exploration.strategy.widget

import com.natpryce.konfig.Configuration
import org.droidmate.configuration.ConfigProperties
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.exploration.modelFeatures.ActionCounterMF
import org.droidmate.exploration.modelFeatures.explorationWatchers.BlackListMF
import org.droidmate.exploration.modelFeatures.listOfSmallest
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.explorationModel.debugOutput
import org.droidmate.explorationModel.debugT
import org.droidmate.explorationModel.emptyId
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.GlobalAction
import java.util.Random
import kotlin.streams.asSequence

/**
 * Exploration strategy that select a (pseudo-)random widget from the screen.
 */
@Suppress("MemberVisibilityCanBePrivate")
open class RandomWidget constructor(
	private val priority: Int,
	protected val dictionary: List<String> = emptyList(),
	protected val useCoordinateClicks: Boolean = false
) : AExplorationStrategy() {
	/** Random exploration seed */
	protected var randomSeed: Long = -1L
	/** Prioritise UI elements which have not yet been interacted with, instead of plain random **/
	protected var biased: Boolean = true
	/** Trigger not only clicks and long clicks, but also scroll actions when the UI element supports it */
	protected var randomScroll: Boolean = true
	protected var delay : Long = 0


	@Deprecated("use different constructor", ReplaceWith("RandomWidget(priority,dictionary,useCoordinateClicks)"))
	@JvmOverloads constructor(
		priority: Int,
	              randomSeed: Long,
	              biased: Boolean = true,
	              randomScroll: Boolean = true,
	              dictionary: List<String> = emptyList(),
	              delay : Long = 0,
	              useCoordinateClicks: Boolean = false
	) : this(priority, dictionary, useCoordinateClicks) {
		this.randomSeed = randomSeed
		this.biased = biased
		this.randomScroll = randomScroll
		this.delay = delay
	}

	override fun getPriority(): Int = priority

	protected var random = Random(randomSeed)
		private set

	override fun initialize(cfg: Configuration) {
		delay = cfg[ConfigProperties.Exploration.widgetActionDelay]
		biased = cfg[ConfigProperties.Strategies.Parameters.biasedRandom]
		randomScroll = cfg[ConfigProperties.Strategies.Parameters.randomScroll]
		randomSeed = cfg[ConfigProperties.Selectors.randomSeed].let{
			if (it == -1L) Random().nextLong()
			else it
		}
		random = Random(randomSeed)
	}

	@Suppress("MemberVisibilityCanBePrivate")
	protected lateinit var counter: ActionCounterMF
	@Suppress("MemberVisibilityCanBePrivate")
	protected lateinit var blackList: BlackListMF
	override fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> initialize(initialContext: ExplorationContext<M, S, W>) {
		super.initialize(initialContext)
		counter = initialContext.getOrCreateWatcher()
		blackList = initialContext.getOrCreateWatcher()
	}

	 suspend fun mustRepeatLastAction(eContext: ExplorationContext<*,*,*>): Boolean {
		return if (!eContext.isEmpty()) {
			eContext.getCurrentState().let { currentState ->
				// Last state was runtime permission
				(currentState.isRequestRuntimePermissionDialogBox) &&
						// Has last action
						eContext.lastTarget != null &&
						// Has a state that is not a runtime permission
						eContext.model.getStates()
							.filterNot { it.stateId == emptyId && it.isRequestRuntimePermissionDialogBox }
							.isNotEmpty() &&
						// Can re-execute the same action
						currentState.actionableWidgets
							.any { p -> eContext.lastTarget?.let { p.id == it.id } ?: false }
			}
		} else false
	}

	/** if we trigger any functionality which requires (not yet granted) Android permissions an PermissionDialogue
	 * will appear, but the functionality may not be triggered yet.
	 * Now we do not want to penalize this target just because it required a permission and the functionality was not yet triggered
	 */
	protected fun repeatLastAction(): ExplorationAction {
//		val lastActionBeforePermission = currentState.let {
//			!(it.isRequestRuntimePermissionDialogBox || it.stateId == emptyId)
//		}

//        return lastActionBeforePermission.action
		TODO("instead PermissionStrategy should decrease the widgetCounter again, if the prev state is the same like after handling permission " +
				"to avoid penalizing the target")
	}

	protected open fun ExplorationContext<*, *, *>.getAvailableWidgets(): List<Widget> {
		return getCurrentState().visibleTargets.filter { w ->	!w.isKeyboard
				&& (!w.isInputField || !explorationTrace.insertedTextValues().contains(w.text)) }  // ignore input fields we already filled
	}

	/** use this function to filter potential candidates against previously blacklisted widgets
	 * @param block your function determining the ExplorationAction based on the filtered candidates
	 * @param tInState the threshold to consider the widget blacklisted within the current state eContext
	 * @param tOverall the threshold to consider the widget blacklisted over all states
	 */
	protected open suspend fun<S: State<*>> excludeBlacklisted(
		currentState: S,
		candidates: List<Widget>,
		tInState: Int = 1,
		tOverall: Int = 2,
		block: (listedInsState: List<Widget>, blacklisted: List<Widget>) -> List<Widget>
	): List<Widget> =
		candidates.filterNot { blackList.isBlacklistedInState(it.uid, currentState.uid, tInState) }.let { noBlacklistedInState ->
			block(noBlacklistedInState, noBlacklistedInState.filterNot { blackList.isBlacklisted(it.uid, tOverall) })
		}


	private fun List<Widget>.chooseRandomly(eContext: ExplorationContext<*, *, *>): ExplorationAction {
		if (this.isEmpty())
			return eContext.launchApp()
		return chooseActionForWidget( this[random.nextInt(this.size)], eContext )
	}

	open suspend fun ExplorationContext<*, *, *>.computeCandidates(): Collection<Widget> = debugT("blacklist computation", {
		val nonCrashing = getAvailableWidgets().nonCrashingWidgets()
		excludeBlacklisted(getCurrentState(), nonCrashing) { noBlacklistedInState, noBlacklisted ->
			when {
				noBlacklisted.isNotEmpty() -> noBlacklisted
				noBlacklistedInState.isNotEmpty() -> noBlacklistedInState
				else -> nonCrashing
			}
		}
	}, inMillis = true)
			.filter { it.clickable || it.longClickable || it.checked != null } // the other actions are currently not supported

	@Suppress("MemberVisibilityCanBePrivate")
	protected suspend fun getCandidates(eContext : ExplorationContext<*, *, *>): List<Widget> {
		return eContext.computeCandidates()
				.let { filteredCandidates ->
					// for each widget in this state the number of interactions
					counter.numExplored(eContext.getCurrentState(), filteredCandidates).entries
							.groupBy { it.key.packageName }.flatMap { (pkgName, countEntry) ->
								if (pkgName != eContext.apk.packageName) {
									val pkgActions = counter.pkgCount(pkgName)
									countEntry.map { Pair(it.key, pkgActions) }
								} else
									countEntry.map { Pair(it.key, it.value) }
							}// we sum up all counters of widgets which do not belong to the app package to prioritize app targets
							.groupBy { (_, countVal) -> countVal }.let { map ->
								map.listOfSmallest()?.map { (w, _) -> w }?.let { leastInState: List<Widget> ->
									// determine the subset of widgets which were least interacted with
									// if multiple widgets clicked with same frequency, choose the one least clicked over all states
									if (leastInState.size > 1) {
										leastInState.groupBy { counter.widgetCnt(it.uid) }.listOfSmallest()
									} else leastInState
								}
							}
							?: emptyList()
				}
	}

	private suspend fun chooseBiased(eContext: ExplorationContext<*, *, *>): ExplorationAction {
		val candidates = getCandidates(eContext)
		// no valid candidates -> go back to previous state
		return if (candidates.isEmpty()) {
			println("RANDOM: Back, reason - nothing (non-blacklisted) interactable to click")
			ExplorationAction.pressBack()
		}
		else candidates.chooseRandomly(eContext)
	}

	private fun chooseRandomly(eContext: ExplorationContext<*, *, *>): ExplorationAction {
		return eContext.getCurrentState().actionableWidgets.chooseRandomly(eContext)
	}

	protected open suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> chooseRandomWidget(
		eContext: ExplorationContext<M,S,W>
	): ExplorationAction {
		return if (biased)
			chooseBiased(eContext)
		else
			chooseRandomly(eContext)
	}

	protected open fun randomString(): String{
		if(dictionary.isNotEmpty()) return dictionary[random.nextInt(dictionary.size-1)]

		@Suppress("SpellCheckingInspection") val source = "abcdefghijklmnopqrstuvwxyz"
		return random.ints( random.nextInt(20).toLong()+3, 0, source.length)
				.asSequence()
				.map(source::get)
				.joinToString("")
	}

	protected open fun chooseActionForWidget(chosenWidget: Widget, eContext: ExplorationContext<*, *, *>): ExplorationAction {
		var widget = chosenWidget

		while (!chosenWidget.isInteractive) {
			widget = eContext.getCurrentState().widgets.first { it.id == chosenWidget.parentId }
		}

		val actionList = when{
			widget.isInputField ->	listOf(widget.setText(randomString(),delay = delay, sendEnter = true))
			randomScroll -> widget.availableActions(delay,useCoordinateClicks)
			else -> widget.availableActions(delay,useCoordinateClicks).filterNot { it is Swipe }
		}

		val maxVal = actionList.size
	//FIXME this may give trouble with swipe-able only elements
		assert(maxVal > 0) { "No actions can be performed on the widget $widget" }

		val randomIdx = random.nextInt(maxVal)
		return actionList[randomIdx].also {
			if(debugOutput) log.debug("[A${it.id}] Chosen widget info: $widget: keyboard=${widget.isKeyboard}\t" +
					"clickable=${widget.clickable}\tcheckable=${widget.checked}\tlong-clickable=${widget.longClickable}\t" +
					"scrollable=${widget.scrollable}")
		}
	}

	override suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> computeNextAction(
		eContext: ExplorationContext<M,S,W>
	): ExplorationAction {
		if (eContext.isEmpty()) {
			return GlobalAction(ActionType.FetchGUI)
		}

		val autAutMF = eContext.findWatcher { it is org.atua.modelFeatures.ATUAMF }
		if (autAutMF!=null) {
			if ((autAutMF as org.atua.modelFeatures.ATUAMF).portraitScreenSurface.isEmpty()) {
				if (eContext.getCurrentState().isHomeScreen)
					return GlobalAction(ActionType.FetchGUI)
				else
					return GlobalAction(ActionType.PressHome)
			}
		}
			//return eContext.launchApp() // very first action -> start the app via reset
		// Repeat previous action is last action was to click on a runtime permission dialog
//		if (mustRepeatLastAction(eContext))
//			return repeatLastAction()

		return chooseRandomWidget(eContext)
	}
}
