package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.strategy.autaut.ATUATestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoToTargetWindowTask (
        regressionWatcher: ATUAMF,
        atuaTestingStrategy: ATUATestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : GoToAnotherWindow(regressionWatcher, atuaTestingStrategy, delay, useCoordinateClicks) {

    override fun increaseExecutedCount() {
        executedCount++
    }

    override fun initPossiblePaths(currentState: State<*>, continueMode: Boolean) {
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
                PathFindingHelper.PathType.INCLUDE_INFERED
        else if (continueMode)
            PathFindingHelper.PathType.INCLUDE_INFERED
        else
            computeNextPathType(currentPath!!.pathType,includeResetAction)
        
        while (possiblePaths.isEmpty()) {
            possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToTargetWindows(currentState,pathType = nextPathType))
            if (nextPathType == PathFindingHelper.PathType.WTG)
                break
            if (continueMode)
                break
            if (!includeResetAction && nextPathType == PathFindingHelper.PathType.INCLUDE_INFERED) {
                break
            } else {
                nextPathType = computeNextPathType(nextPathType,includeResetAction)
            }
        }
        if (possiblePaths.isEmpty()) {
            log.debug("Cannot identify path to target Window.")
        }
    }


    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: GoToTargetWindowTask? = null
        fun getInstance(regressionWatcher: ATUAMF,
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