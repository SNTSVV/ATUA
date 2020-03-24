package org.droidmate.exploration.strategy.regression.task

import org.droidmate.deviceInterface.exploration.ActionQueue
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.regression.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.regression.textInput.DataField
import org.droidmate.exploration.modelFeatures.regression.staticModel.Helper
import org.droidmate.exploration.strategy.regression.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList

class FillTextInputTask private constructor(
        regressionWatcher: RegressionTestingMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean): AbstractStrategyTask(regressionTestingStrategy, regressionWatcher,delay,useCoordinateClicks){
    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (fillDataMode == FillDataMode.SIMPLE)
            return true
        if (fillDataMode == FillDataMode.FULL)
            return true
        return true
    }

    var fillDataMode: FillDataMode = FillDataMode.SIMPLE
    protected val filledData = HashMap<DataField, Boolean>()
    override fun initialize(currentState: State<*>) {
        reset()
        currentState.actionableWidgets.filter { it.isInputField}.forEach {
            val dataField = TextInput.getInputWidgetAssociatedDataField(widget = it, state = currentState)
            if (dataField!=null)
            {
                filledData.put(dataField,false)
            }
        }
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        if (fillDataMode == FillDataMode.SIMPLE)
        {
            val fillTextActions = fillTextBoxSimply(currentState)
            if (fillTextActions.size > 0)
            {
                return ActionQueue(fillTextActions,delay)
            }
            else
            {
                return ExplorationAction.closeAndReturn()
            }
        }
        else
        {
            return ExplorationAction.closeAndReturn()
        }
    }

    override fun reset() {
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        return hasTextInput(currentState) && shouldInsertText(currentState)
    }
    val fillTextDecision = HashMap<Widget,Boolean>()

    private fun hasTextInput(currentState: State<*>): Boolean {
        return  Helper.getInputFields(currentState).isNotEmpty()
}

    internal fun fillTextBoxSimply(currentState: State<*>): List<ExplorationAction> {
        val allInputWidgets = Helper.getInputFields(currentState)
        if (allInputWidgets.size == 0)
            return emptyList()
        else
        {
            val actionList = ArrayList<ExplorationAction>()
            allInputWidgets.forEach {
                if(fillTextDecision.containsKey(it) && fillTextDecision[it]!! == true)
                {
                    val inputValue = TextInput.getSetTextInputValue(it,currentState)
                    val inputAction = it.setText(inputValue,sendEnter = false,enableValidation = false)
                    TextInput.historyTextInput.add(inputValue)
                    actionList.add(inputAction)
                }
            }
            return actionList
        }
    }

    private fun shouldInsertText(currentState: State<*>): Boolean {
        fillTextDecision.clear()
        filledData.clear()
        val allInputWidgets = Helper.getInputFields(currentState)
        allInputWidgets.forEach {
            if (!it.isPassword)
            {
                if (TextInput.historyTextInput.contains(it.text))
                {
                    if (random.nextInt(100)<25)
                        fillTextDecision.put(it,true)
                    else
                        fillTextDecision.put(it,false)
                }
                else
                {
                    fillTextDecision.put(it,true)
                }
            }
            else
            {
                if (random.nextBoolean())
                {
                    fillTextDecision.put(it,true)
                }
                else
                {
                    fillTextDecision.put(it,false)
                }
            }
        }
        return fillTextDecision.isNotEmpty() && fillTextDecision.filter { it.value == true }.isNotEmpty()
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: FillTextInputTask? = null
        fun getInstance(regressionWatcher: RegressionTestingMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): FillTextInputTask {
            if (instance == null) {
                instance = FillTextInputTask(regressionWatcher, regressionTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}

enum class FillDataMode {
    SIMPLE,
    FULL
}
