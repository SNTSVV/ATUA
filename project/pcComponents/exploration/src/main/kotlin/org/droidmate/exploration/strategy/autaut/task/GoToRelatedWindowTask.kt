package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GoToRelatedWindowTask protected constructor(
        regressionWatcher: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : GoToAnotherWindow(regressionWatcher, autAutTestingStrategy, delay, useCoordinateClicks) {

    override fun chooseRandomOption(currentState: State<*>) {
        //log.debug("Change options")
        currentPath = possiblePaths.random()
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root.data
        log.debug("Try reaching ${currentPath!!.getFinalDestination()}")
        currentEdge = null
        mainTaskFinished = false
    }

    override fun increaseExecutedCount() {
        executedCount++
    }
    override fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToWindow(currentState,destWindow!!,true,false))
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: GoToRelatedWindowTask? = null
        fun getInstance(regressionWatcher: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): GoToRelatedWindowTask {
            if (instance == null) {
                instance = GoToRelatedWindowTask(regressionWatcher, autAutTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}