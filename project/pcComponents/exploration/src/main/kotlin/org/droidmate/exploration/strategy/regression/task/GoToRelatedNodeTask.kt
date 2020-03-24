package org.droidmate.exploration.strategy.regression.task

import org.droidmate.exploration.modelFeatures.regression.RegressionTestingMF
import org.droidmate.exploration.strategy.regression.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoToRelatedNodeTask protected constructor(
         regressionWatcher: RegressionTestingMF,
        regressionTestingStrategy: RegressionTestingStrategy,
       delay: Long, useCoordinateClicks: Boolean) : GoToAnotherNode(regressionWatcher, regressionTestingStrategy, delay, useCoordinateClicks) {

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
        var instance: GoToRelatedNodeTask? = null
        fun getInstance(regressionWatcher: RegressionTestingMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): GoToRelatedNodeTask {
            if (instance == null) {
                instance = GoToRelatedNodeTask(regressionWatcher, regressionTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}