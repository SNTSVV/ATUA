package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OpenNavigationBarTask constructor(
        regressionTestingMF: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean): AbstractStrategyTask(autAutTestingStrategy, regressionTestingMF,delay, useCoordinateClicks){

    override fun isTaskEnd(currentState: State<*>): Boolean {
        return true
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun isAvailable(currentState: State<*>): Boolean {
        if (currentState.widgets.filter { it.isVisible }.find { it.contentDesc.contains("Open navigation") } != null)
        {
            return true
        }
        return false
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        return listOf(currentState.widgets.filter { it.isVisible }.find { it.contentDesc.contains("Open navigation") }!!)
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        val currentNode = autautMF.getAbstractState(currentState)!!
        if (!autautMF.openNavigationCheck.contains(currentNode))
            autautMF.openNavigationCheck.add(currentNode)
        val chosenWidget = chooseWidgets(currentState).firstOrNull()
        if (chosenWidget == null)
            return ExplorationAction.pressBack()
        return autautStrategy.eContext.navigateTo(chosenWidget,{chosenWidget.click()})
                .also { executedCount++ }?:ExplorationAction.pressBack()
    }

    override fun reset() {

    }

    override fun initialize(currentState: State<*>) {
        reset()
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
       return false
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass) }
        var executedCount:Int = 0
        var instance: OpenNavigationBarTask? = null
        fun getInstance(regressionTestingMF: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): OpenNavigationBarTask {
            if (instance == null) {
                instance = OpenNavigationBarTask(regressionTestingMF,autAutTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }

}