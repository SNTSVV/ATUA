package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClickNavigationUpTask constructor(
        regressionTestingMF: AutAutMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean): AbstractStrategyTask(regressionTestingStrategy, regressionTestingMF,delay, useCoordinateClicks){

    override fun isTaskEnd(currentState: State<*>): Boolean {
        return true
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun isAvailable(currentState: State<*>): Boolean {
        if (currentState.widgets.filter { it.isVisible }.find { it.contentDesc.toLowerCase()=="navigate up" } != null)
        {
            return true
        }
        return false
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        return listOf(currentState.widgets.filter { it.isVisible }.find { it.contentDesc.toLowerCase()=="navigate up" }!!)
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
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
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: ClickNavigationUpTask? = null
        fun getInstance(regressionTestingMF: AutAutMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): ClickNavigationUpTask {
            if (instance == null) {
                instance = ClickNavigationUpTask(regressionTestingMF,regressionTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }

}