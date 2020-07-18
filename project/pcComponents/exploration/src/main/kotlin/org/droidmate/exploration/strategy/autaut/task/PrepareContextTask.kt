package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.isClick
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.DataField
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.TextInput
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PrepareContextTask private constructor(
        regressionWatcher: AutAutMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean): AbstractStrategyTask(regressionTestingStrategy, regressionWatcher,delay,useCoordinateClicks){

    override fun isTaskEnd(currentState: State<*>): Boolean {
        val availableWidgets = fillActions.map { it.key }.filter { currentState.widgets.contains(it) }
        if (availableWidgets.isNotEmpty())
            return false
        var isEnd = true
/*        currentState.widgets.filter { filledTexts.containsKey(it)}.asSequence().forEach {
            if (it.text!=filledTexts[it])
            {
                isEnd = false
                fillTextDecision[it]=false
            }
        }*/
        return isEnd
    }

    var fillDataMode: FillDataMode = FillDataMode.SIMPLE
    protected val filledData = HashMap<DataField, Boolean>()
    val fillActions = HashMap<Widget,ExplorationAction>()
    override fun initialize(currentState: State<*>) {
        reset()
        prepareFillActions(currentState)
/*        Helper.getInputFields(currentState).forEach {
            val dataField = TextInput.getInputWidgetAssociatedDataField(widget = it, state = currentState)
            if (dataField!=null)
            {
                filledData.put(dataField,false)
            }
        }*/
    }

    private fun prepareFillActions(currentState: State<*>) {
        val allInputWidgets = Helper.getInputFields(currentState).filter { inputFillDecision.containsKey(it) }
        allInputWidgets.forEach {widget ->
            if (widget.checked!=null) {
                fillActions[widget] = widget.click()
            } else {
                val inputValue = TextInput.getSetTextInputValue(widget,currentState)
                val inputAction = if (allInputWidgets.size == 1)
                    widget.setText(inputValue,sendEnter = true ,enableValidation = false)
                else
                    widget.setText(inputValue,sendEnter = false ,enableValidation = false)
                fillActions[widget] = inputAction
            }
        }

    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        if (fillDataMode == FillDataMode.SIMPLE)
        {
            return randomlyFill(currentState)
        }
        else
        {
            return ExplorationAction.pressBack()
        }
    }

    override fun reset() {
        TextInput.resetInputData()
        fillActions.clear()
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        return hasInput(currentState) && shouldInsertText(currentState)
    }
    val inputFillDecision = HashMap<Widget,Boolean>()

    private fun hasInput(currentState: State<*>): Boolean {
        return  Helper.getInputFields(currentState).isNotEmpty()
}

    internal fun randomlyFill(currentState: State<*>): ExplorationAction {
        val availableWidgets = fillActions.map { it.key }.filter { currentState.widgets.contains(it) }
        if (availableWidgets.isEmpty())
            return ExplorationAction.pressBack()
        val toFillWidget = availableWidgets.random()
        val action = fillActions[toFillWidget]!!
        fillActions.remove(toFillWidget)
        return action
    }

    private fun shouldInsertText(currentState: State<*>): Boolean {
        inputFillDecision.clear()

        // we group widgets by its resourceId to easily deal with radio button
        val allInputWidgets = Helper.getInputFields(currentState).groupBy { it.resourceId }
        allInputWidgets.forEach { resourceId, widgets ->
            val processingWidgets = widgets.toMutableList()
            processingWidgets.forEach {
                if (!it.isPassword && it.isInputField) {
                    if (TextInput.historyTextInput.contains(it.text)) {
                        if (random.nextInt(100) < 25)
                            inputFillDecision.put(it, false)
                    } else {
                        inputFillDecision.put(it, false)
                    }
                } else {
                    var ignoreWidget = false
                    if (!it.isInputField) {
                        // check if a click on this widget will go to another window
                        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
                        val widgetGroup = abstractState.getWidgetGroup(widget = it, guiState = currentState)
                        if (widgetGroup != null) {
                            val isGoToAnotherWindow = regressionTestingMF.abstractTransitionGraph.edges(abstractState).any {
                                it.destination!!.data.window != it.source.data.window
                                        && it.label.abstractAction.actionName.isClick()
                            }
                            if (isGoToAnotherWindow) {
                                // any widget can lead to another window should be ignore.
                                ignoreWidget = true
                            }
                        }
                    }
                    if (!ignoreWidget) {
                        if (random.nextBoolean()) {
                            inputFillDecision.put(it, false)
                        }
                    }
                }
            }
        }
        return inputFillDecision.isNotEmpty()
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: PrepareContextTask? = null
        fun getInstance(regressionWatcher: AutAutMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean): PrepareContextTask {
            if (instance == null) {
                instance = PrepareContextTask(regressionWatcher, regressionTestingStrategy, delay,useCoordinateClicks)
            }
            return instance!!
        }
    }
}

enum class FillDataMode {
    SIMPLE,
    FULL
}
