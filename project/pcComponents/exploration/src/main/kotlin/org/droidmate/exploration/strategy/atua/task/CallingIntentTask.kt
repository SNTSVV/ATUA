package org.droidmate.exploration.strategy.atua.task

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.callIntent
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.strategy.atua.ATUATestingStrategy
import org.droidmate.exploration.strategy.atua.model.IntentTestInstance
import org.droidmate.explorationModel.interaction.State

class CallingIntentTask(regressionTestingMF: org.atua.modelFeatures.ATUAMF,
                        atuaTestingStrategy: ATUATestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean):AbstractStrategyTask(atuaMF = regressionTestingMF,
                        atuaStrategy = atuaTestingStrategy,
                        delay = delay,useCoordinateClicks = useCoordinateClicks) {
    val targetIntentTestInstances = ArrayList<IntentTestInstance>()
    override fun isAvailable(currentState: State<*>): Boolean {
        if (atuaMF.getTargetIntentFilters().isEmpty())
            return false
        return true
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        if (targetIntentTestInstances.isEmpty())
            return ExplorationAction.pressBack()
        val intentFilter = targetIntentTestInstances.random()
        targetIntentTestInstances.remove(intentFilter)
        return atuaStrategy.eContext.callIntent(
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
        atuaMF.getTargetIntentFilters().forEach {
            val actions = it.getActions()
            actions.forEach {action ->
                val categories = it.getCategories()
                categories.forEach { category ->
                    val datas = it.getDatas()
                    datas.forEach {data->
                        val testData = data.testData.random()
                        targetIntentTestInstances.add(IntentTestInstance(action = action,
                                category = category, data = testData, activity = it.activity))
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