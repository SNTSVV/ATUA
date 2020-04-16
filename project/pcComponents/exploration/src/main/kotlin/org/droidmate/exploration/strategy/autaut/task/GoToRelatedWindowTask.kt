package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoToRelatedWindowTask protected constructor(
         regressionWatcher: RegressionTestingMF,
        regressionTestingStrategy: RegressionTestingStrategy,
       delay: Long, useCoordinateClicks: Boolean) : GoToAnotherWindow(regressionWatcher, regressionTestingStrategy, delay, useCoordinateClicks) {

    override fun chooseRandomOption(currentState: State<*>) {
        log.debug("Change options")
        currentPath = possiblePaths.random()
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root.data
        log.debug("Try to reach ${currentPath!!.getFinalDestination()}")
        currentEdge = null
        mainTaskFinished = false
    }

    override fun increaseExecutedCount() {
        executedCount++
    }
    override fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.addAll(regressionTestingStrategy.phaseStrategy.getPathsToTargetWindows(currentState))
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: GoToRelatedWindowTask? = null
        fun getInstance(regressionWatcher: RegressionTestingMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): GoToRelatedWindowTask {
            if (instance == null) {
                instance = GoToRelatedWindowTask(regressionWatcher, regressionTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}