package org.droidmate.exploration.strategy.atua.task

import org.atua.modelFeatures.helper.PathFindingHelper
import org.droidmate.exploration.strategy.atua.ATUATestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoToTargetWindowTask (
    regressionWatcher: org.atua.modelFeatures.ATUAMF,
    atuaTestingStrategy: ATUATestingStrategy,
    delay: Long, useCoordinateClicks: Boolean) : GoToAnotherWindow(regressionWatcher, atuaTestingStrategy, delay, useCoordinateClicks) {

    override fun increaseExecutedCount() {
        executedCount++
    }

    override fun initPossiblePaths(currentState: State<*>, continueMode: Boolean) {
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
            PathFindingHelper.PathType.NORMAL
        /*else if (continueMode)
            PathFindingHelper.PathType.PARTIAL_TRACE*/
        else
            computeNextPathType(currentPath!!.pathType,includeResetAction)
        val currentPathType = nextPathType
        while (possiblePaths.isEmpty()) {
            possiblePaths.addAll(atuaStrategy.phaseStrategy.getPathsToTargetWindows(currentState,pathType = nextPathType))
            nextPathType = computeNextPathType(nextPathType,includeResetAction)
            if (nextPathType == currentPathType)
                break
        }
        if (possiblePaths.isEmpty() && destWindow!=null) {
            log.debug("Cannot identify path to $destWindow")
        }
    }


    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: GoToTargetWindowTask? = null
        fun getInstance(regressionWatcher: org.atua.modelFeatures.ATUAMF,
                        atuaTestingStrategy: ATUATestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): GoToTargetWindowTask {
            if (instance == null) {
                instance = GoToTargetWindowTask(regressionWatcher, atuaTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}