package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.strategy.autaut.ATUATestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ExerciseTargetComponentTask private constructor(
        regressionWatcher: ATUAMF,
        atuaTestingStrategy: ATUATestingStrategy,
        delay: Long, useCoordinateClicks: Boolean)
    : AbstractStrategyTask(atuaTestingStrategy, regressionWatcher, delay, useCoordinateClicks){

    private var recentChangedSystemConfiguration: Boolean = false
    var environmentChange: Boolean = false
    val eventList:  ArrayList<AbstractAction> = ArrayList()
    var chosenAbstractAction: AbstractAction? = null
    var fillingData = false
    var dataFilled = false
    var randomRefillingData = false
    var alwaysUseRandomInput = false
    private var prevAbstractState: AbstractState?=null
    var originalEventList: ArrayList<AbstractAction> = ArrayList()
    private val fillDataTask = PrepareContextTask.getInstance(atuaMF,atuaTestingStrategy, delay, useCoordinateClicks)
    val targetItemEvents = HashMap<AbstractAction, HashMap<String,Int>>()

    override fun chooseRandomOption(currentState: State<*>) {
        log.debug("Do nothing")
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        return false
    }
    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (isCameraOpening(currentState)) {
            return false
        }
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        if (currentAbstractState.window is Dialog || currentAbstractState.window is OptionsMenu || currentAbstractState.window is OutOfApp) {
            if (isDoingRandomExplorationTask && randomExplorationTask.isTaskEnd(currentState)) {
                return true
            }
                return false
        }
        if (currentAbstractState.window != targetWindow) {
            return true
        }
        val abstractState = atuaMF.getAbstractState(currentState)!!
        eventList.removeIf {
            it.isWidgetAction() &&
                    !abstractState.attributeValuationMaps.contains(it.attributeValuationMap)
        }
        if (eventList.isNotEmpty()) {
            return false
        }
        return true
    }

    private var mainTaskFinished:Boolean = false
    private val randomExplorationTask = RandomExplorationTask(regressionWatcher,atuaTestingStrategy, delay,useCoordinateClicks,true,3)

    override fun initialize(currentState: State<*>) {
        reset()
        randomExplorationTask.fillingData=false
        mainTaskFinished = false
        val currentAbstractState = atuaMF.getAbstractState(currentState)
        eventList.addAll(autautStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        targetWindow = atuaMF.getAbstractState(currentState)!!.window
        eventList.filter { it.isItemAction() }.forEach { action ->
            currentAbstractState!!.attributeValuationMaps.filter { action.attributeValuationMap!!.isParent(it) }.forEach { childWidget->
                val childActionType = when (action.actionType) {
                    AbstractActionType.ITEM_CLICK -> AbstractActionType.CLICK
                   AbstractActionType.ITEM_LONGCLICK -> AbstractActionType.LONGCLICK
                    else -> AbstractActionType.CLICK
                }
                currentAbstractState!!.getAvailableActions().filter { it.attributeValuationMap == childWidget && it.actionType == childActionType }.forEach {
                    if (it.attributeValuationMap!!.cardinality == Cardinality.MANY) {
                        val itemActionAttempt = 3*autautStrategy.budgetScale
                        for (i in 1..itemActionAttempt.toInt()) {
                            eventList.add(it)
                        }
                    } else {
                        eventList.add(it)
                    }
                }

            }
        }
        originalEventList.addAll(eventList)
    }


    override fun reset() {
        extraTasks.clear()
        eventList.clear()
        originalEventList.clear()
        currentExtraTask = null
        mainTaskFinished = false
        prevAbstractState = null
        dataFilled = false
        fillingData = false
        randomRefillingData = false
        recentChangedSystemConfiguration = false
        environmentChange = false
        alwaysUseRandomInput = false
        targetWindow = null
    }

    var targetWindow: Window? = null
    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        eventList.addAll(autautStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        originalEventList.addAll(eventList)
        if (eventList.isNotEmpty()){
            targetWindow = atuaMF.getAbstractState(currentState)!!.window
            log.info("Current node has ${eventList.size} target Window transition(s).")
            return true
        }
        log.info("Current node has no target Window transition.")
        return false
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        //check if we can encounter any target component in current state
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        var candidates= ArrayList<Widget>()
        candidates.addAll(atuaMF.getRuntimeWidgets(chosenAbstractAction!!.attributeValuationMap!!,currentAbstractState, currentState))
        if (candidates.isNotEmpty())
        {
            return candidates
        }

        return emptyList()
    }
    var isDoingRandomExplorationTask: Boolean = false
    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        if (currentAbstractState != null) {
            prevAbstractState = currentAbstractState
        }
        if (isCameraOpening(currentState)) {
            if (!isDoingRandomExplorationTask)
                randomExplorationTask.initialize(currentState)
            isDoingRandomExplorationTask = true
            return randomExplorationTask.chooseAction(currentState)
        }
        if (currentAbstractState.window != targetWindow) {
            if ( currentAbstractState.window is Dialog || currentAbstractState.window is OptionsMenu || currentAbstractState.window is OutOfApp) {
                if (!isDoingRandomExplorationTask)
                    randomExplorationTask.initialize(currentState)
                isDoingRandomExplorationTask = true
                return randomExplorationTask.chooseAction(currentState)
            }
        }
        isDoingRandomExplorationTask = false
        if (atuaMF.havingInternetConfiguration(currentAbstractState.window)) {
            if (!recentChangedSystemConfiguration && environmentChange && random.nextBoolean()) {
                recentChangedSystemConfiguration = true
                if (atuaMF.havingInternetConfiguration(currentAbstractState.window)) {
                    if (random.nextInt(4) < 3)
                        return GlobalAction(ActionType.EnableData).also {
                            atuaMF.internetStatus = true
                        }
                    else
                        return GlobalAction(ActionType.DisableData).also {
                            atuaMF.internetStatus = false
                        }
                } else {
                    return GlobalAction(ActionType.EnableData).also {
                        atuaMF.internetStatus = false
                    }
                }
            }
        }

        if (!dataFilled && !fillingData) {
            val lastAction = autautStrategy.eContext.getLastAction()
            if (!lastAction.actionType.isTextInsert()) {
                if (fillDataTask.isAvailable(currentState, alwaysUseRandomInput)) {
                    fillDataTask.initialize(currentState)
                    fillingData = true
                }
            } else {
                dataFilled = true
            }
        }
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            return fillDataTask.chooseAction(currentState)
        if (fillingData) {
            fillingData = false
            if (fillDataTask.fillActions.isNotEmpty()  ) {
                dataFilled = false
            } else
                dataFilled = true
        }
       /* if (randomRefillingData
                && originalEventList.size > eventList.size
                && fillDataTask.isAvailable(currentState)) {
            fillDataTask.initialize(currentState)
            fillingData = true
            return fillDataTask.chooseAction(currentState)
        }*/
        //TODO check eventList is not empty

        if (eventList.isEmpty()) {
            log.debug("No more target event. Random exploration.")
            return randomExplorationTask.chooseAction(currentState)
        }

        if (!eventList.any { it.attributeValuationMap!=null && it.attributeValuationMap.isInputField() }) {
            chosenAbstractAction = eventList.filterNot { it.attributeValuationMap!=null && it.attributeValuationMap.isInputField() }.random()
        } else {
            chosenAbstractAction = eventList.random()
        }
        eventList.remove(chosenAbstractAction!!)
        dataFilled = false
        fillingData = false
        if (chosenAbstractAction!=null)
        {
            log.info("Exercise Event: ${chosenAbstractAction!!.actionType}")
            var chosenWidget: Widget? = null
            if (chosenAbstractAction!!.attributeValuationMap!=null)
            {
                val candidates = chooseWidgets(currentState)
                if (candidates.isNotEmpty())  {
                    chosenWidget = runBlocking { getCandidates(candidates) }.random()
                }
                if (chosenWidget==null)
                {
                    log.debug("No widget found. Choose another Window transition.")
                    return chooseAction(currentState)
                }
                log.info("Choose Action for Widget: $chosenWidget")
            }
            val recommendedAction = chosenAbstractAction!!.actionType
            log.debug("Target action: $recommendedAction")
            val chosenAction = when (chosenAbstractAction!!.actionType)
            {
                AbstractActionType.SEND_INTENT -> chooseActionWithName(recommendedAction,chosenAbstractAction!!.extra, null, currentState,chosenAbstractAction)
                AbstractActionType.ROTATE_UI -> chooseActionWithName(recommendedAction,90,null,currentState,chosenAbstractAction)
                else -> chooseActionWithName(recommendedAction, chosenAbstractAction!!.extra?:"", chosenWidget, currentState,chosenAbstractAction)
            }
            if (chosenAction == null)
            {
                currentAbstractState.inputMappings.remove(chosenAbstractAction!!)
                //regressionTestingMF.registerTriggeredEvents(chosenEvent!!)
                if (eventList.isNotEmpty())
                {
                    return chooseAction(currentState)
                }
                log.debug("Cannot get action for this widget.")
                return ExplorationAction.pressBack()
            }
            else
            {
                autautStrategy.phaseStrategy.registerTriggeredEvents(chosenAbstractAction!!,currentState)
                atuaMF.isAlreadyRegisteringEvent = true
                dataFilled = false
                return chosenAction
            }
        }
        return ExplorationAction.pressBack()

    }

    companion object
    {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        private var instance: ExerciseTargetComponentTask? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: ATUAMF,
                        atuaTestingStrategy: ATUATestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): ExerciseTargetComponentTask {
            if (instance == null)
            {
                instance = ExerciseTargetComponentTask(regressionWatcher, atuaTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }



}