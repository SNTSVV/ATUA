package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.callIntent
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State

class CallingIntentTask(regressionTestingMF: RegressionTestingMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean):AbstractStrategyTask(regressionTestingMF = regressionTestingMF,
                        regressionTestingStrategy = regressionTestingStrategy,
                        delay = delay,useCoordinateClicks = useCoordinateClicks) {
    val targetIntentTestInstances = ArrayList<IntentTestInstance>()
    override fun isAvailable(currentState: State<*>): Boolean {
        if (regressionTestingMF.getTargetIntentFilters_P1().isEmpty())
            return false
        return true
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        if (targetIntentTestInstances.isEmpty())
            return ExplorationAction.closeAndReturn()
        val intentFilter = targetIntentTestInstances.random()
        targetIntentTestInstances.remove(intentFilter)
        return regressionTestingStrategy.eContext.callIntent(
                action = intentFilter.action,
                category = intentFilter.category,
                activity = intentFilter.activity,
                uriString = intentFilter.data
        )
    }

    override fun reset() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun initialize(currentState: State<*>) {
        targetIntentTestInstances.clear()
        regressionTestingMF.getTargetIntentFilters_P1().forEach {
            val actions = it.getActions()
            actions.forEach {action ->
                val categories = it.getCategories()
                categories.forEach { category ->
                    val datas = it.getDatas()
                    datas.forEach {data->
                        val testData = data.testData.random()
                        targetIntentTestInstances.add(IntentTestInstance(action = action,
                                category = category, data = testData, activity = it.activity ))
                    }
                }
            }
        }
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (targetIntentTestInstances.isEmpty())
            return true
        return false
    }
}