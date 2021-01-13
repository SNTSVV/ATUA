package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
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
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
            PathFindingHelper.PathType.INCLUDE_INFERED
        else
            computeNextPathType(currentPath!!.pathType,includeReset)
        while (possiblePaths.isEmpty()) {
            possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToTargetWindows(currentState,pathType = nextPathType))
            if (nextPathType == PathFindingHelper.PathType.WTG ||
                    (!includeReset && nextPathType == PathFindingHelper.PathType.FOLLOW_TRACE)) {
                break
            }else {
                nextPathType = computeNextPathType(nextPathType,includeReset)
            }
        }
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