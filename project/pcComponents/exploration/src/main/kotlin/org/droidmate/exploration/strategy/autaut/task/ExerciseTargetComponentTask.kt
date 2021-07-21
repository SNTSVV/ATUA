package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.Cardinality
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
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
    var randomBudget: Int = 5*atuaTestingStrategy.scaleFactor.toInt()
    private var prevAbstractState: AbstractState?=null
    val originalEventList: ArrayList<AbstractAction> = ArrayList()
    val exercisedInputs: ArrayList<AbstractAction> = ArrayList()

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
       /* if (currentAbstractState.window is Dialog || currentAbstractState.window is OptionsMenu || currentAbstractState.window is OutOfApp) {
            if (isDoingRandomExplorationTask && randomExplorationTask.isTaskEnd(currentState)) {
                return true
            }
            return false
        }*/
        if (currentAbstractState.isOpeningKeyboard)
            return false
        if (isDoingRandomExplorationTask && !randomExplorationTask.isTaskEnd(currentState)) {
            return false
        }
        if (currentAbstractState.window != targetWindow) {
            if (randomBudget>=0)
                return false
            return true
        }
        val abstractState = atuaMF.getAbstractState(currentState)!!
        eventList.removeIf {
            it.isWidgetAction() &&
                    !abstractState.attributeValuationMaps.contains(it.attributeValuationMap)
        }
        establishTargetInputs(currentState)
        eventList.removeIf {
            exercisedInputs.contains(it)
        }
        if (eventList.isNotEmpty()) {
            return false
        } else {
            if (randomBudget<0)
                return true
        }
        return false
    }

    private var mainTaskFinished:Boolean = false
    private val randomExplorationTask = RandomExplorationTask(regressionWatcher,atuaTestingStrategy, delay,useCoordinateClicks,true,3)

    override fun initialize(currentState: State<*>) {
        reset()
        randomExplorationTask.fillingData=false
        mainTaskFinished = false
        establishTargetInputs(currentState)
        originalEventList.addAll(eventList)
    }

    private fun establishTargetInputs(currentState: State<*>) {
        eventList.clear()
        val currentAbstractState = atuaMF.getAbstractState(currentState)
        eventList.addAll(atuaStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        targetWindow = atuaMF.getAbstractState(currentState)!!.window
        eventList.filter { it.isItemAction() }.forEach { action ->
            currentAbstractState!!.attributeValuationMaps.filter { action.attributeValuationMap!!.isParent(it) }.forEach { childWidget ->
                val childActionType = when (action.actionType) {
                    AbstractActionType.ITEM_CLICK -> AbstractActionType.CLICK
                    AbstractActionType.ITEM_LONGCLICK -> AbstractActionType.LONGCLICK
                    else -> AbstractActionType.CLICK
                }
                currentAbstractState!!.getAvailableActions().filter { it.attributeValuationMap == childWidget && it.actionType == childActionType }.forEach {
                    if (currentAbstractState.avmCardinalities.get(it.attributeValuationMap!!) == Cardinality.MANY) {
                        val itemActionAttempt = 3 * atuaStrategy.scaleFactor
                        for (i in 1..itemActionAttempt.toInt()) {
                            eventList.add(it)
                        }
                    } else {
                        eventList.add(it)
                    }
                }

            }
        }
    }


    override fun reset() {
        extraTasks.clear()
        eventList.clear()
        originalEventList.clear()
        exercisedInputs.clear()
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
        randomBudget=5*atuaStrategy.scaleFactor.toInt()
    }

    var targetWindow: Window? = null
    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        eventList.addAll(atuaStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        originalEventList.addAll(eventList)
        if (eventList.isNotEmpty()){
            targetWindow = atuaMF.getAbstractState(currentState)!!.window
            log.info("Current abstrate state has ${eventList.size}  target inputs.")
            return true
        }
        log.info("Current abstrate state has no target input.")
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

    override fun chooseAction(currentState: State<*>): ExplorationAction? {
        executedCount++
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        val prevAbstractState = if (atuaMF.appPrevState != null)
            atuaMF.getAbstractState(atuaMF.appPrevState!!) ?: currentAbstractState
        else
            currentAbstractState
        if (isCameraOpening(currentState)) {
            return doRandomExploration(currentState)
        }
        if (currentAbstractState.window != targetWindow) {
            return doRandomExploration(currentState)
            /*if ( currentAbstractState.window is Dialog || currentAbstractState.window is OptionsMenu || currentAbstractState.window is OutOfApp) {
                if (!isDoingRandomExplorationTask)
                    randomExplorationTask.initialize(currentState)
                isDoingRandomExplorationTask = true
                return randomExplorationTask.chooseAction(currentState)
            }*/
        }
        if (currentState.widgets.any { it.isKeyboard }) {
            if (random.nextBoolean()) {
                return GlobalAction(actionType = ActionType.CloseKeyboard)
            }
            if (random.nextBoolean()) {
                return doRandomKeyboard(currentState, null)!!
            }
            //find search button
            val searchButtons = currentState.visibleTargets.filter { it.isKeyboard }.filter { it.contentDesc.toLowerCase().contains("search") }
            if (searchButtons.isNotEmpty()) {
                //Give a 50/50 chance to click on the search button
                if (random.nextBoolean()) {
                    dataFilled = false
                    val randomButton = searchButtons.random()
                    log.info("Widget: $random")
                    return randomButton.click()
                }
            }
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
            return doRandomExploration(currentState)
        }
        isDoingRandomExplorationTask = false
        randomBudget=5*atuaStrategy.scaleFactor.toInt()
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
        var action: ExplorationAction? = null
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            action = fillDataTask.chooseAction(currentState)
        else
            fillingData = false
        if (action != null) {
            return action
        }
        if (dataFilled) {
            val userlikedInputs= currentAbstractState.attributeValuationMaps.filter { it.isUserLikeInput() }
            val userlikedInputsEWidget = userlikedInputs.map { currentAbstractState.EWTGWidgetMapping.get(it) }
            val previousStateWidgets = prevAbstractState?.EWTGWidgetMapping?.values?: emptyList<Widget>()
            if (userlikedInputsEWidget.isNotEmpty() && userlikedInputsEWidget.intersect(previousStateWidgets).isEmpty()) {
                dataFilled = false
            }
        }
        if (!dataFilled && !fillingData) {
            val lastAction = atuaStrategy.eContext.getLastAction()
            if (!lastAction.actionType.isTextInsert()) {
                if (fillDataTask.isAvailable(currentState, alwaysUseRandomInput)) {
                    fillDataTask.initialize(currentState)
                    fillingData = true
                    val action = fillDataTask.chooseAction(currentState)
                    if (action!=null)
                        return action
                }
            } else {
                dataFilled = true
            }
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
                atuaStrategy.phaseStrategy.registerTriggeredEvents(chosenAbstractAction!!,currentState)
                exercisedInputs.add(chosenAbstractAction!!)
                atuaMF.isAlreadyRegisteringEvent = true
                dataFilled = false
                return chosenAction
            }
        }
        return ExplorationAction.pressBack()

    }

    private fun doRandomExploration(currentState: State<*>): ExplorationAction? {
        if (!isDoingRandomExplorationTask) {
            activateRandomExploration(currentState)
        }
        isDoingRandomExplorationTask = true
        val action = randomExplorationTask.chooseAction(currentState)
        if (!randomExplorationTask.fillingData) {
            randomBudget--
        }
        return action
    }

    private fun activateRandomExploration(currentState: State<*>) {
        randomExplorationTask.initialize(currentState)
        randomExplorationTask.setMaxiumAttempt(5 * atuaStrategy.scaleFactor.toInt())
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