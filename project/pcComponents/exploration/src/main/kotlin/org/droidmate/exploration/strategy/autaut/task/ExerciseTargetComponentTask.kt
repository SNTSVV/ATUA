package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ExerciseTargetComponentTask private constructor(
        regressionWatcher: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean)
    : AbstractStrategyTask(autAutTestingStrategy, regressionWatcher, delay, useCoordinateClicks){

    private var recentChangedSystemConfiguration: Boolean = false
    var environmentChange: Boolean = false
    val eventList:  ArrayList<AbstractAction> = ArrayList()
    var chosenAbstractAction: AbstractAction? = null
    var currentAbstractState: AbstractState? = null
    var fillingData = false
    var dataFilled = false
    var randomRefillingData = false
    var alwaysUseRandomInput = false
    private var prevAbstractState: AbstractState?=null
    var originalEventList: ArrayList<AbstractAction> = ArrayList()
    private val fillDataTask = PrepareContextTask.getInstance(autautMF,autAutTestingStrategy, delay, useCoordinateClicks)
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
        if (autautMF.getAbstractState(currentState)!!.window != targetWindow)
            return true
        val abstractState = autautMF.getAbstractState(currentState)!!
        eventList.removeIf {
            it.isWidgetAction() &&
                    !abstractState.attributeValuationSets.contains(it.attributeValuationSet)
        }
        if (eventList.isNotEmpty()) {
            return false
        }
        return true
    }

    private var mainTaskFinished:Boolean = false
    private val randomExplorationTask = RandomExplorationTask(regressionWatcher,autAutTestingStrategy, delay,useCoordinateClicks,true,3)
    private val candidateWidgetsMap = HashMap<State<*>, List<Widget>>()
    private val openNavigationBar = OpenNavigationBarTask.getInstance(regressionWatcher,autAutTestingStrategy,delay, useCoordinateClicks)
    override fun initialize(currentState: State<*>) {
        reset()
        randomExplorationTask.fillingData=false
        mainTaskFinished = false
        currentAbstractState = autautMF.getAbstractState(currentState)
        initializeExtraTasks(currentState)
        eventList.addAll(autautStrategy.phaseStrategy.getCurrentTargetEvents(currentState))
        targetWindow = autautMF.getAbstractState(currentState)!!.window
        eventList.filter { it.isItemAction() }.forEach { action ->
            currentAbstractState!!.attributeValuationSets.filter { action.attributeValuationSet!!.isParent(it) }.forEach { childWidget->
                val childActionType = when (action.actionType) {
                    AbstractActionType.ITEM_CLICK -> AbstractActionType.CLICK
                   AbstractActionType.ITEM_LONGCLICK -> AbstractActionType.LONGCLICK
                    else -> AbstractActionType.CLICK
                }
                currentAbstractState!!.getAvailableActions().filter { it.attributeValuationSet == childWidget && it.actionType == childActionType }.forEach {
                    if (it.attributeValuationSet!!.cardinality == Cardinality.MANY) {
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

    private fun initializeExtraTasks(currentState: State<*>) {
        extraTasks.clear()
//        if (openTargetDialogTask.isAvailable(currentState)) {
//            extraTasks.add(openTargetDialogTask.also { it.initialize(currentState) })
//        }
//        if (openOptionMenuTask.isAvailable(currentState)) {
//            extraTasks.add(openOptionMenuTask.also { it.initialize(currentState) })
//        }
//        if (openContextMenuTask.isAvailable(currentState)) {
//            extraTasks.add(openContextMenuTask.also { it.initialize(currentState) })
//        }
        extraTasks.add(openNavigationBar)
        extraTasks.add(randomExplorationTask)
        currentExtraTask = null
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
            targetWindow = autautMF.getAbstractState(currentState)!!.window
            log.info("Current node has ${eventList.size} target Window transition(s).")
            return true
        }
        log.info("Current node has no target Window transition.")
        return false
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        //check if we can encounter any target component in current state
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        var candidates= ArrayList<Widget>()
        candidates.addAll(autautMF.getRuntimeWidgets(chosenAbstractAction!!.attributeValuationSet!!,currentAbstractState, currentState))
        if (candidates.isNotEmpty())
        {
            return candidates
        }
        //if have target widget
        if (candidates.isNotEmpty())
        {
            return candidates
        }
        //autautMF.addUnreachableTargetComponentState(currentState)
        return emptyList()
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (currentAbstractState != null) {
            prevAbstractState = currentAbstractState
        }
        if (isCameraOpening(currentState)) {
            randomExplorationTask.chooseAction(currentState)
        }
        randomExplorationTask.isClickedShutterButton = false
        if (currentAbstractState.window != targetWindow) {
            randomExplorationTask.chooseAction(currentState)
        }
        if (autautMF.havingInternetConfiguration(currentAbstractState.window)) {
            if (!recentChangedSystemConfiguration && environmentChange && random.nextBoolean()) {
                recentChangedSystemConfiguration = true
                if (autautMF.havingInternetConfiguration(currentAbstractState.window)) {
                    if (random.nextInt(4) < 3)
                        return GlobalAction(ActionType.EnableData).also {
                            autautMF.internetStatus = true
                        }
                    else
                        return GlobalAction(ActionType.DisableData).also {
                            autautMF.internetStatus = false
                        }
                } else {
                    return GlobalAction(ActionType.EnableData).also {
                        autautMF.internetStatus = false
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

        if (!eventList.any { it.attributeValuationSet!=null && it.attributeValuationSet.isInputField() }) {
            chosenAbstractAction = eventList.filterNot { it.attributeValuationSet!=null && it.attributeValuationSet.isInputField() }.random()
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
            if (chosenAbstractAction!!.attributeValuationSet!=null)
            {
                val candidates = chooseWidgets(currentState)
                chosenWidget = candidates.firstOrNull()
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
                autautMF.isAlreadyRegisteringEvent = true
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
        fun getInstance(regressionWatcher: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): ExerciseTargetComponentTask {
            if (instance == null)
            {
                instance = ExerciseTargetComponentTask(regressionWatcher, autAutTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }



}