package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoToTargetWindowTask (
        regressionWatcher: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : GoToAnotherWindow(regressionWatcher, autAutTestingStrategy, delay, useCoordinateClicks) {

    override fun increaseExecutedCount() {
        executedCount++
    }

    override fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToTargetWindows(currentState,usingPressback))
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: GoToTargetWindowTask? = null
        fun getInstance(regressionWatcher: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): GoToTargetWindowTask {
            if (instance == null) {
                instance = GoToTargetWindowTask(regressionWatcher, autAutTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}