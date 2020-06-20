package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class ReplayInteractionTask constructor(
        regressionTestingMF: AutAutMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long,
        useCoordinateClicks: Boolean): AbstractStrategyTask(regressionTestingStrategy,regressionTestingMF, delay, useCoordinateClicks){

    var currentInteraction: Interaction<*>? = null
    var replayInteractionList = ArrayList<Pair<State<*>, Interaction<*>>>()
    fun setReplayActionList(actionList: List<Pair<State<*>, Interaction<*>>>){
        replayInteractionList.clear()
        replayInteractionList.addAll(actionList)
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (replayInteractionList.isEmpty())
            return true
        val expectedState = replayInteractionList.first().first
        if (currentState.uid != expectedState.uid)
            return true
        return false
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        return false
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        val pair = replayInteractionList.first()
        replayInteractionList.remove(pair)
        currentInteraction = pair.second
        if (currentInteraction!!.targetWidget != null)
        {
            return listOf(currentInteraction!!.targetWidget!!)
        }
        return currentState.visibleTargets
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        val chosenWidget = chooseWidgets(currentState).firstOrNull()
        if (chosenWidget==null)
        {
            return ExplorationAction.pressBack()
        }
        return chooseActionWithName(currentInteraction!!.actionType,currentInteraction!!.data, chosenWidget, currentState)?:ExplorationAction.pressBack()
    }

    override fun reset() {
        replayInteractionList.clear()
    }

    override fun initialize(currentState: State<*>) {
        reset()
    }


    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass) }

        var instance: ReplayInteractionTask? = null
        fun getInstance(regressionTestingMF: AutAutMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): ReplayInteractionTask {
            if (instance == null) {
                instance = ReplayInteractionTask(regressionTestingMF,regressionTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }

}